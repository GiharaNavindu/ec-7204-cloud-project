param(
    [string]$ApiBase = "http://localhost:8080",
    [string]$Password = "Pass@123",
    [switch]$NoReset,
    [switch]$SkipPackage,
    [switch]$BuildImages,
    [int]$HealthTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Get-Json {
    param(
        [string]$Url,
        [hashtable]$Headers = @{}
    )
    $response = Invoke-RestMethod -Method Get -Uri $Url -Headers $Headers
    return $response
}

function Post-Json {
    param(
        [string]$Url,
        [object]$Body,
        [hashtable]$Headers = @{}
    )
    $json = $Body | ConvertTo-Json -Depth 10
    Write-Host "Sending POST to $Url with body: $json" -ForegroundColor DarkGray
    try {
        return Invoke-RestMethod -Method Post -Uri $Url -Headers $Headers -ContentType "application/json" -Body $json
    }
    catch {
        $errMsg = $_.Exception.Message
        $statusCode = $_.Exception.Response.StatusCode.Value
        Write-Host "`n❌ Server Error [$statusCode]: $errMsg" -ForegroundColor Red
        
        if ($_.Exception.Response) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $errBody = $reader.ReadToEnd()
                if ($errBody) {
                    Write-Host "Response body: $errBody" -ForegroundColor Red
                }
            }
            catch {
                Write-Host "Could not read response body" -ForegroundColor DarkRed
            }
        }
        throw
    }
}

function Patch-NoBody {
    param(
        [string]$Url,
        [hashtable]$Headers = @{}
    )
    return Invoke-RestMethod -Method Patch -Uri $Url -Headers $Headers
}

function Invoke-MavenPackage {
    param([string]$ServicePath)

    Push-Location $ServicePath
    try {
        Write-Host "Packaging in $ServicePath..." -ForegroundColor DarkCyan

        if (Test-Path ".\mvnw.cmd") {
            & ".\mvnw.cmd" clean package "-Dmaven.test.skip=true" | Out-Host
        }
        else {
            mvn clean package "-Dmaven.test.skip=true" | Out-Host
        }

        if ($LASTEXITCODE -ne 0) {
            throw "Maven package failed for '$ServicePath' with exit code $LASTEXITCODE"
        }

        $jar = Get-ChildItem -Path ".\target\*.jar" -File |
        Where-Object { $_.Name -notlike "*.jar.original" } |
        Select-Object -First 1

        if (-not $jar) {
            throw "No runtime JAR found in '$ServicePath\target'."
        }

        Write-Host "✅ Packaged -> $($jar.Name)" -ForegroundColor Green
    }
    finally {
        Pop-Location
    }
}

# ============================
# ENV SETUP
# ============================

Write-Step "Preparing local environment"

if (-not $env:JWT_SECRET -or $env:JWT_SECRET.Length -lt 64) {
    $env:JWT_SECRET = "local_demo_secret_key_with_more_than_sixty_four_characters_for_testing_2026_ruhuna_auction"
}

if (-not $env:JWT_EXPIRATION) {
    $env:JWT_EXPIRATION = "86400000"
}

# Dynamic users to prevent "Email already exists" 400 errors
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$SellerEmail = "seller_$timestamp@example.com"
$BuyerEmail = "buyer_$timestamp@example.com"

# ============================
# CLEAN & BUILD
# ============================

if (-not $NoReset) {
    Write-Step "Stopping and resetting containers/volumes"
    docker compose down -v --remove-orphans | Out-Host
}

$shouldBuildImages = $BuildImages

if (-not $SkipPackage) {
    Write-Step "Packaging local JARs for all services"

    foreach ($service in @("user-service", "auction-service", "bid-service", "api-gateway")) {
        if (Test-Path $service) {
            Invoke-MavenPackage -ServicePath $service
        }
    }
    $shouldBuildImages = $true
}
else {
    Write-Host "⚠️ Skipping Maven package step (-SkipPackage)" -ForegroundColor Yellow
}

# ============================
# START SERVICES
# ============================

Write-Step "Starting services"

if ($shouldBuildImages) {
    Write-Host "Building Docker images to ensure latest JARs are used..." -ForegroundColor DarkGray
    docker compose up -d --build | Out-Host
}
else {
    docker compose up -d | Out-Host
}

if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed. Check your docker-compose.yml configuration."
}

# ============================
# HEALTH CHECK
# ============================

Write-Step "Waiting for Gateway & Microservices to be ready"

$maxAttempts = [Math]::Ceiling($HealthTimeoutSeconds / 5)
$ready = $false

for ($i = 1; $i -le $maxAttempts; $i++) {
    try {
        $health = Get-Json -Url "$ApiBase/actuator/health"
        if ($health.status -ne "UP") { throw "Gateway not UP" }

        $userStatus = Get-Json -Url "$ApiBase/api/users/status"
        if ($userStatus -notlike "*up and running*") { throw "User Service not reachable" }

        $auctionStatus = Get-Json -Url "$ApiBase/api/auctions/status"
        if ($auctionStatus -notlike "*up and running*") { throw "Auction Service not reachable" }

        $bidStatus = Get-Json -Url "$ApiBase/api/bids/status"
        if ($bidStatus -notlike "*up and running*") { throw "Bid Service not reachable" }

        Write-Host "✅ All critical services are UP and reachable via Gateway" -ForegroundColor Green
        $ready = $true
        break
    }
    catch {
        Write-Host "Waiting... ($($i)/$maxAttempts): $($_.Exception.Message)" -ForegroundColor Gray
    }
    Start-Sleep -Seconds 5
}

if (-not $ready) {
    throw "Services did not become healthy within $HealthTimeoutSeconds seconds."
}

# ============================
# SELLER FLOW (Create Auction)
# ============================

Write-Step "Registering & Logging in SELLER"

Post-Json -Url "$ApiBase/api/users/register" -Body @{
    name     = "Auction Seller"
    email    = $SellerEmail
    password = $Password
} | Out-Null

$sellerLogin = Post-Json -Url "$ApiBase/api/users/login" -Body @{
    email    = $SellerEmail
    password = $Password
}
$sellerAuthHeaders = @{ Authorization = "Bearer $($sellerLogin.token)" }
Write-Host "✅ Seller authenticated" -ForegroundColor Green

Write-Step "Creating auction (As Seller)"

$now = Get-Date
$startTime = $now.AddMinutes(-5).ToString("yyyy-MM-ddTHH:mm:ss")
$endTime = $now.AddHours(2).ToString("yyyy-MM-ddTHH:mm:ss")

$auction = Post-Json -Url "$ApiBase/api/auctions" -Headers $sellerAuthHeaders -Body @{
    title           = "Limited Edition Watch"
    description     = "A very rare vintage watch from 1950s"
    startTime       = $startTime
    endTime         = $endTime
    createdByUserId = 1 
}
$auctionId = $auction.id
Write-Host "✅ Auction Created with ID: $auctionId" -ForegroundColor Green

Write-Step "Activating auction"
Patch-NoBody -Url "$ApiBase/api/auctions/$auctionId/status?status=IN_PROG" -Headers $sellerAuthHeaders | Out-Null
Write-Host "✅ Auction is now IN_PROG" -ForegroundColor Green

# ============================
# BUYER FLOW (Place Bids)
# ============================

Write-Step "Registering & Logging in BUYER"

Post-Json -Url "$ApiBase/api/users/register" -Body @{
    name     = "Eager Buyer"
    email    = $BuyerEmail
    password = $Password
} | Out-Null

$buyerLogin = Post-Json -Url "$ApiBase/api/users/login" -Body @{
    email    = $BuyerEmail
    password = $Password
}
$buyerAuthHeaders = @{ Authorization = "Bearer $($buyerLogin.token)" }
Write-Host "✅ Buyer authenticated" -ForegroundColor Green

Write-Step "Placing first bid (As Buyer)"

$bid1 = Post-Json -Url "$ApiBase/api/bids" -Headers $buyerAuthHeaders -Body @{
    auctionId = $auctionId
    amount    = 500.00
}
Write-Host "✅ First Bid placed: $($bid1.amount) (Bid ID: $($bid1.id))" -ForegroundColor Green

Write-Step "Placing higher bid (As Buyer)"

$bid2 = Post-Json -Url "$ApiBase/api/bids" -Headers $buyerAuthHeaders -Body @{
    auctionId = $auctionId
    amount    = 750.50
}
Write-Host "✅ Second Bid placed: $($bid2.amount) (Bid ID: $($bid2.id))" -ForegroundColor Green

Write-Step "Testing lower bid (should be rejected)"

try {
    Post-Json -Url "$ApiBase/api/bids" -Headers $buyerAuthHeaders -Body @{
        auctionId = $auctionId
        amount    = 600.00
    }
    throw "Error: Lower bid should have been rejected but was accepted"
}
catch {
    Write-Host "✅ Expected rejection received for lower bid: $($_.Exception.Message)" -ForegroundColor Green
}

# ============================
# VERIFICATION
# ============================

Write-Step "Fetching all bids for auction $auctionId"

$bids = Get-Json -Url "$ApiBase/api/bids/auction/$auctionId" -Headers $buyerAuthHeaders
Write-Host "Total successful bids found: $($bids.Count)" -ForegroundColor Green

if ($bids.Count -lt 2) {
    throw "Expected at least 2 bids, but found $($bids.Count)"
}

Write-Step "E2E Test Suite Passed Successfully! 🚀"

Write-Host "Summary:" -ForegroundColor Gray
Write-Host " - Seller: $SellerEmail"
Write-Host " - Buyer:  $BuyerEmail"
Write-Host " - AuctionId: $auctionId"
Write-Host " - Winning Bid: $($bids[0].amount)" -ForegroundColor Green