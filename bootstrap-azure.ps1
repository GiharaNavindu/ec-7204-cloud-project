param(
    [string]$ResourceGroupName = 'rg-ruhuna-auction',
    [string]$Location = 'centralus',
    [string]$AcrName = 'acruhuna2049',
    [string]$PostgreSqlAdminUsername = 'pgadmin',
    [switch]$PromptSecrets
)

$ErrorActionPreference = 'Stop'

function Write-Section {
    param([string]$Message)
    Write-Host "`n=== $Message ===" -ForegroundColor Cyan
}

function Write-Info {
    param([string]$Message)
    Write-Host "  $Message" -ForegroundColor Gray
}

function New-RandomSecret {
    param([int]$ByteCount)

    $bytes = New-Object byte[] $ByteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $rng.GetBytes($bytes)
    }
    finally {
        $rng.Dispose()
    }

    [Convert]::ToBase64String($bytes).
    TrimEnd('=').
    Replace('+', '-').
    Replace('/', '_')
}

function Convert-SecureStringToPlainText {
    param([System.Security.SecureString]$SecureString)

    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)

    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    }
    finally {
        if ($bstr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }
}

function Read-OrGenerateSecret {
    param(
        [string]$Prompt,
        [int]$LengthInBytes,
        [switch]$PromptForValue
    )

    if ($PromptForValue) {
        $secureValue = Read-Host -AsSecureString -Prompt $Prompt

        if (-not $secureValue) {
            throw "$Prompt cannot be empty."
        }

        return Convert-SecureStringToPlainText -SecureString $secureValue
    }

    return New-RandomSecret -ByteCount $LengthInBytes
}

Write-Section 'Bootstrap configuration'

Write-Info "Resource group : $ResourceGroupName"
Write-Info "Region         : $Location"
Write-Info "ACR name       : $AcrName"

$subscriptionId = az account show --query id --output tsv

if ($LASTEXITCODE -ne 0) {
    throw 'Please login first using az login'
}

Write-Section 'Secrets and dependency inputs'

$postgresPassword = Read-OrGenerateSecret `
    -Prompt 'Enter PostgreSQL password' `
    -LengthInBytes 32 `
    -PromptForValue:$PromptSecrets

$jwtSecret = Read-OrGenerateSecret `
    -Prompt 'Enter JWT secret' `
    -LengthInBytes 64 `
    -PromptForValue:$PromptSecrets

Write-Host "`nNOTE: Enter ONLY RabbitMQ hostname" -ForegroundColor Yellow
Write-Host "Example: toucan.lmq.cloudamqp.com" -ForegroundColor Yellow

$rabbitmqHost = (
    Read-Host -Prompt 'Enter RabbitMQ host'
).Trim().
Replace("amqps://", "").
Split(":")[0].
Split("@")[-1].
Split("/")[0]

$rabbitmqUsername = Read-Host -Prompt 'Enter RabbitMQ username'

$rabbitmqPassword = Read-OrGenerateSecret `
    -Prompt 'Enter RabbitMQ password' `
    -LengthInBytes 24 `
    -PromptForValue:$true

$googleClientId = Read-Host -Prompt 'Enter Google OAuth Client ID'

$googleClientSecret = Read-OrGenerateSecret `
    -Prompt 'Enter Google OAuth Client Secret' `
    -LengthInBytes 32 `
    -PromptForValue:$true

$appOAuth2RedirectUri = Read-Host -Prompt 'Enter OAuth2 redirect URI (Optional)'
$zipkinUrl = Read-Host -Prompt 'Enter Zipkin URL (Optional)'

$postgreSqlServerName = 'pg-ruhuna-auction'
$redisCacheName = 'redis-ruhuna-auction'

Write-Section 'Creating Azure Resource Group'

az group create `
    --name $ResourceGroupName `
    --location $Location `
    --output none

Write-Section 'Registering Azure Providers'

$providers = @(
    'Microsoft.App',
    'Microsoft.OperationalInsights',
    'Microsoft.Network',
    'Microsoft.DBforPostgreSQL',
    'Microsoft.Cache',
    'Microsoft.ContainerRegistry'
)

foreach ($provider in $providers) {
    Write-Info "Registering $provider"
    az provider register `
        --namespace $provider `
        --wait `
        --output none
}

Write-Section 'Azure Container Registry'

Write-Info 'Creating ACR...'

az acr create `
    --name $AcrName `
    --resource-group $ResourceGroupName `
    --sku Basic `
    --admin-enabled true `
    --output none

$acrLoginServer = az acr show `
    --name $AcrName `
    --resource-group $ResourceGroupName `
    --query loginServer `
    --output tsv

$acrCredentials = az acr credential show `
    --name $AcrName `
    --resource-group $ResourceGroupName `
    --output json | ConvertFrom-Json

$acrUsername = $acrCredentials.username
$acrPassword = $acrCredentials.passwords[0].value

Write-Section 'PostgreSQL Flexible Server'

Write-Info 'Creating PostgreSQL server...'

az postgres flexible-server create `
    --resource-group $ResourceGroupName `
    --name $postgreSqlServerName `
    --location $Location `
    --admin-user $PostgreSqlAdminUsername `
    --admin-password $postgresPassword `
    --sku-name Standard_B1ms `
    --tier Burstable `
    --public-access 0.0.0.0 `
    --version 16 `
    --output none

Write-Info 'Creating database...'

az postgres flexible-server db create `
    --resource-group $ResourceGroupName `
    --server-name $postgreSqlServerName `
    --name auction_db `
    --output none

Write-Section 'Azure Container Registry Login'

az acr login --name $AcrName

$services = @(
    'api-gateway',
    'user-service',
    'auction-service',
    'bid-service',
    'notification-service'
)

foreach ($service in $services) {

    Write-Host "`n>>> Building and Pushing $service using Azure ACR..." -ForegroundColor Cyan

    az acr build `
        --registry $AcrName `
        --image "$service`:latest" `
        --file "$service/Dockerfile" .

    if ($LASTEXITCODE -ne 0) {
        throw "Azure ACR build failed for $service"
    }
}

Write-Section 'Deploying Container Apps Infrastructure'

$deploymentParameters = @(
    "location=$Location",
    "acrLoginServer=$acrLoginServer",
    "acrUsername=$acrUsername",
    "acrPassword=$acrPassword",
    "jwtSecret=$jwtSecret",
    "dbPassword=$postgresPassword",
    "rabbitmqHost=$rabbitmqHost",
    "rabbitmqUsername=$rabbitmqUsername",
    "rabbitmqPassword=$rabbitmqPassword",
    "googleClientId=$googleClientId",
    "googleClientSecret=$googleClientSecret",
    "appOAuth2RedirectUri=$appOAuth2RedirectUri",
    "postgresqlServerName=$postgreSqlServerName",
    "postgresqlAdminUsername=$PostgreSqlAdminUsername",
    "postgresqlAdminPassword=$postgresPassword",
    "redisCacheName=$redisCacheName",
    "zipkinUrl=$zipkinUrl",
    "imageTag=latest"
)

az deployment group create `
    --name bootstrap-initial `
    --resource-group $ResourceGroupName `
    --template-file infra/container-apps.bicep `
    --parameters @deploymentParameters `
    --output none

Write-Section 'GitHub Actions Service Principal'

$spName = "sp-auction-$AcrName"

$sp = az ad sp create-for-rbac `
    --name $spName `
    --role Contributor `
    --scopes "/subscriptions/$subscriptionId/resourceGroups/$ResourceGroupName" `
    --output json | ConvertFrom-Json

$creds = @{
    clientId                   = $sp.appId
    clientSecret               = $sp.password
    subscriptionId             = $subscriptionId
    tenantId                   = $sp.tenant
    resourceManagerEndpointUrl = 'https://management.azure.com/'
    activeDirectoryEndpointUrl = 'https://login.microsoftonline.com/'
}

Write-Host "`n==============================" -ForegroundColor Green
Write-Host "GitHub Secrets" -ForegroundColor Green
Write-Host "==============================" -ForegroundColor Green

Write-Host "`nAZURE_CREDENTIALS =" -ForegroundColor Yellow
Write-Host ($creds | ConvertTo-Json -Compress)

Write-Host "`nACR_LOGIN_SERVER = $acrLoginServer" -ForegroundColor Yellow
Write-Host "ACR_USERNAME     = $acrUsername" -ForegroundColor Yellow
Write-Host "ACR_PASSWORD     = $acrPassword" -ForegroundColor Yellow

Write-Host "`n==============================" -ForegroundColor Green
Write-Host "GitHub Variables" -ForegroundColor Green
Write-Host "==============================" -ForegroundColor Green

Write-Host "`nAZURE_RESOURCE_GROUP = $ResourceGroupName" -ForegroundColor Yellow

Write-Section 'BOOTSTRAP COMPLETE'

Write-Host "Your Ruhuna Auction cloud platform is now deployed." -ForegroundColor Green