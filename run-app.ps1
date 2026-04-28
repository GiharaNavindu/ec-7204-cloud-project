# AuctionHub Startup & Test Script
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
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

# 1. Environment Configuration
Write-Step "Configuring Environment..."
if (-not $env:JWT_SECRET) {
    # Generate a secure key if not present
    $env:JWT_SECRET = "production_ready_secret_key_for_auction_hub_2026_ruhuna_cloud_system"
    Write-Host "    Generated JWT_SECRET" -ForegroundColor Gray
}

# 2. Package JARs
Write-Step "Packaging local JARs for all services"
foreach ($service in @("user-service", "auction-service", "bid-service", "notification-service", "api-gateway")) {
    if (Test-Path $service) {
        Invoke-MavenPackage -ServicePath $service
    }
    else {
        Write-Host "⚠️ Directory $service not found. Skipping." -ForegroundColor Yellow
    }
}

# 3. Build and Start Services
Write-Step "Building and Starting Services with Docker Compose..."
Write-Host "    This may take a few minutes..." -ForegroundColor Gray
docker compose up -d --build

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to start services. Ensure Docker Desktop is running." -ForegroundColor Red
    exit $LASTEXITCODE
}

# 4. Wait for Health
Write-Step "Waiting for services to initialize..."
$maxAttempts = 36 # 3 minutes
$ready = $false

for ($i = 1; $i -le $maxAttempts; $i++) {
    try {
        $health = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/actuator/health" -ErrorAction SilentlyContinue
        if ($health.status -eq "UP") {
            # Gateway is up, now verify the microservices are also ready
            $userStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/users/status" -ErrorAction Stop
            $auctionStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/auctions/status" -ErrorAction Stop
            $bidStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/bids/status" -ErrorAction Stop
            $notificationStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/notifications/status" -ErrorAction Stop
            
            if (($userStatus -match "up and running") -and ($auctionStatus -match "up and running") -and ($bidStatus -match "up and running") -and ($notificationStatus -match "up and running")) {
                $ready = $true
                break
            }
        }
    }
    catch { }
    
    Write-Host "    Attempt ${i}/${maxAttempts}: Waiting for all services to boot..." -ForegroundColor Gray
    Start-Sleep -Seconds 5
}

if (-not $ready) {
    Write-Host "❌ Timeout: Services didn't become healthy in time." -ForegroundColor Red
    Write-Host "    Check logs with: docker compose logs" -ForegroundColor Gray
    exit 1
}

# 5. Connectivity Test
Write-Step "Testing API Gateway Connectivity..."
try {
    $userStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/users/status"
    Write-Host "✅ User Service: $userStatus" -ForegroundColor Green

    $auctionStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/auctions/status"
    Write-Host "✅ Auction Service: $auctionStatus" -ForegroundColor Green

    $bidStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/bids/status"
    Write-Host "✅ Bid Service: $bidStatus" -ForegroundColor Green

    $notificationStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/notifications/status"
    Write-Host "✅ Notification Service: $notificationStatus" -ForegroundColor Green
}
catch {
    Write-Host "❌ API connectivity failed." -ForegroundColor Red
    exit 1
}

# 6. Done
Write-Host "`n🚀 AuctionHub is READY!" -ForegroundColor Green
Write-Host "--------------------------------------------------"
Write-Host "Frontend Access : http://localhost:8080" -ForegroundColor Cyan
Write-Host "API Gateway     : http://localhost:8080/api" -ForegroundColor Cyan
Write-Host "--------------------------------------------------"
Write-Host "To stop the application, run: docker compose down" -ForegroundColor Gray
