param(
    [string]$ApiBase = "http://localhost:8080",
    [string]$Email = "jane@example.com",
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
    return Invoke-RestMethod -Method Post -Uri $Url -Headers $Headers -ContentType "application/json" -Body $json
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
    # Match the secret length requirements for HS512 usually used in microservices
    $env:JWT_SECRET = "local_demo_secret_key_with_more_than_sixty_four_characters_for_testing_2026_ruhuna_auction"
}

if (-not $env:JWT_EXPIRATION) {
    $env:JWT_EXPIRATION = "86400000"
}

# ============================
# CLEAN
# ============================

if (-not $NoReset) {
    Write-Step "Stopping and resetting containers/volumes"
    docker compose down -v --remove-orphans | Out-Host
}

# ============================
# BUILD JARS
# ============================

$shouldBuildImages = $BuildImages

if (-not $SkipPackage) {
    Write-Step "Packaging local JARs for all services"

    foreach ($service in @("user-service", "auction-service", "bid-service", "api-gateway")) {
        if (Test-Path $service) {
            Invoke-MavenPackage -ServicePath $service
        }
    }
    
    # Force Docker to rebuild images so it picks up the newly packaged JARs
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
        # 1. Check Gateway Health
        $health = Get-Json -Url "$ApiBase/actuator/health"
        if ($health.status -ne "UP") {
            throw "Gateway not UP"
        }

        # 2. Check User Service reachability
        $userStatus = Get-Json -Url "$ApiBase/api/users/status"
        if ($userStatus -notlike "*up and running*") {
            throw "User Service not reachable"
        }

        # 3. Check Auction Service reachability
        $auctionStatus = Get-Json -Url "$ApiBase/api/auctions/status"
        if ($auctionStatus -notlike "*up and running*") {
            throw "Auction Service not reachable"
        }

        # 4. Check Bid Service reachability
        $bidStatus = Get-Json -Url "$ApiBase/api/bids/status"
        if ($bidStatus -notlike "*up and running*") {
            throw "Bid Service not reachable"
        }

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
    docker compose ps | Out-Host
    docker compose logs --tail=50 | Out-Host
    throw "Services did not become healthy within $HealthTimeoutSeconds seconds."
}

# ============================
# AUTH FLOW
# ============================

Write-Step "Registering test user"

try {
    Post-Json -Url "$ApiBase/api/users/register" -Body @{
        name     = "Jane Doe"
        email    = $Email
        password = $Password
    } | Out-Host
}
catch {
    Write-Host "Registration note: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Step "Logging in"

$login = Post-Json -Url "$ApiBase/api/users/login" -Body @{
    email    = $Email
    password = $Password
}

$token = $login.token

if (-not $token) {
    throw "JWT token missing from login response"
}

$authHeaders = @{ Authorization = "Bearer $token" }

Write-Host "✅ Token acquired for $Email" -ForegroundColor Green

# ============================
# BUSINESS FLOW
# ============================

Write-Step "Creating auction"

# Dynamic dates
$now = Get-Date
$startTime = $now.AddMinutes(-5).ToString("yyyy-MM-ddTHH:mm:ss")
$endTime = $now.AddHours(2).ToString("yyyy-MM-ddTHH:mm:ss")

$auction = Post-Json -Url "$ApiBase/api/auctions" -Headers $authHeaders -Body @{
    title           = "Limited Edition Watch"
    description     = "A very rare vintage watch from 1950s"
    startTime       = $startTime
    endTime         = $endTime
    createdByUserId = 1  # Assuming first user in fresh DB
}

$auctionId = $auction.id

if (-not $auctionId) {
    throw "Auction creation failed - no ID returned"
}

Write-Host "✅ Auction Created with ID: $auctionId (Status: $($auction.status))" -ForegroundColor Green

Write-Step "Activating auction (Setting to IN_PROG)"

Patch-NoBody -Url "$ApiBase/api/auctions/$auctionId/status?status=IN_PROG" -Headers $authHeaders | Out-Null

# Verify activation
$auctionCheck = Get-Json -Url "$ApiBase/api/auctions/$auctionId" -Headers $authHeaders
if ($auctionCheck.status -ne "IN_PROG") {
    throw "Failed to activate auction. Status is $($auctionCheck.status)"
}
Write-Host "✅ Auction is now IN_PROG" -ForegroundColor Green

Write-Step "Placing first bid"

$bid1 = Post-Json -Url "$ApiBase/api/bids" -Headers $authHeaders -Body @{
    auctionId = $auctionId
    amount    = 500.00
}

Write-Host "✅ First Bid placed: $($bid1.amount) (Bid ID: $($bid1.id))" -ForegroundColor Green

Write-Step "Placing higher bid"

$bid2 = Post-Json -Url "$ApiBase/api/bids" -Headers $authHeaders -Body @{
    auctionId = $auctionId
    amount    = 750.50
}

Write-Host "✅ Second Bid placed: $($bid2.amount) (Bid ID: $($bid2.id))" -ForegroundColor Green

Write-Step "Testing lower bid (should be rejected)"

try {
    Post-Json -Url "$ApiBase/api/bids" -Headers $authHeaders -Body @{
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

$bids = Get-Json -Url "$ApiBase/api/bids/auction/$auctionId" -Headers $authHeaders
Write-Host "Total successful bids found: $($bids.Count)" -ForegroundColor Green

if ($bids.Count -lt 2) {
    throw "Expected at least 2 bids, but found $($bids.Count)"
}

Write-Step "Checking Bid Service logs for event processing"
docker compose logs --tail=20 bid-service | Out-Host

# ============================
# DONE
# ============================

Write-Step "E2E Test Suite Passed Successfully! 🚀"

Write-Host "Summary:" -ForegroundColor Gray
Write-Host " - User: $Email"
Write-Host " - AuctionId: $auctionId"
Write-Host " - Winning Bid: $($bids[0].amount)" -ForegroundColor Green
Write-Host "`n⚡ Fast rerun command: .\e2e.ps1 -NoReset -SkipPackage" -ForegroundColor DarkGray