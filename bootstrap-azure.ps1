param(
    [string]$ResourceGroupName = 'rg-ruhuna-auction',
    [string]$Location = 'canadacentral',
    [string]$AcrName = 'acruhunaauction',
    [string]$PostgreSqlAdminUsername = 'pgadmin',
    [switch]$PromptSecrets
)

# Force classic error handling for PS 5.1 compatibility
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
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Convert-SecureStringToPlainText {
    param([System.Security.SecureString]$SecureString)
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) } finally { if ($bstr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) } }
}

function Read-OrGenerateSecret {
    param([string]$Prompt, [int]$LengthInBytes, [switch]$PromptForValue)
    if ($PromptForValue) {
        $secureValue = Read-Host -AsSecureString -Prompt $Prompt
        if (-not $secureValue) { throw "$Prompt cannot be empty." }
        return Convert-SecureStringToPlainText -SecureString $secureValue
    }
    return New-RandomSecret -ByteCount $LengthInBytes
}

Write-Section 'Bootstrap configuration'
Write-Info "Resource group : $ResourceGroupName"
Write-Info "Region         : $Location"
Write-Info "ACR name       : $AcrName"

$subscriptionId = az account show --query id --output tsv
if ($LASTEXITCODE -ne 0) { throw 'You must be logged in to Azure CLI (az login) first.' }

Write-Section 'Secrets and dependency inputs'
$postgresPassword = Read-OrGenerateSecret -Prompt 'Enter PostgreSQL password' -LengthInBytes 32 -PromptForValue:$PromptSecrets
$jwtSecret = Read-OrGenerateSecret -Prompt 'Enter JWT secret' -LengthInBytes 64 -PromptForValue:$PromptSecrets

Write-Host "NOTE: For RabbitMQ Host, enter ONLY the domain (e.g. toucan.lmq.cloudamqp.com)" -ForegroundColor Yellow
$rabbitmqHost = (Read-Host -Prompt 'Enter RabbitMQ host').Trim().Replace("amqps://", "").Split(":")[0].Split("@")[-1].Split("/")[0]
$rabbitmqUsername = Read-Host -Prompt 'Enter RabbitMQ username'
$rabbitmqPassword = Read-OrGenerateSecret -Prompt 'Enter RabbitMQ password' -LengthInBytes 24 -PromptForValue:$true

$googleClientId = Read-Host -Prompt 'Enter Google OAuth Client ID'
$googleClientSecret = Read-OrGenerateSecret -Prompt 'Enter Google OAuth Client Secret' -LengthInBytes 32 -PromptForValue:$true
$appOAuth2RedirectUri = Read-Host -Prompt 'Enter OAuth2 redirect URI (Enter to skip)'
$zipkinUrl = Read-Host -Prompt 'Enter Zipkin URL (Enter to skip)'

$postgresqlServerName = ('pg-' + $AcrName).ToLowerInvariant()
$redisCacheName = ('redis-' + $AcrName).ToLowerInvariant()

Write-Section 'Creating base Azure infrastructure'
Write-Info 'Creating Resource Group...'
az group create --name $ResourceGroupName --location $Location --output none

Write-Info 'Registering Azure providers (this may take a few minutes)...'
$providers = @('Microsoft.App', 'Microsoft.OperationalInsights', 'Microsoft.Network', 'Microsoft.DBforPostgreSQL', 'Microsoft.Cache', 'Microsoft.ContainerRegistry')
foreach ($provider in $providers) {
    az provider register --namespace $provider --wait --output none
}

Write-Info 'Setting up Azure Container Registry...'
# Idempotent create: if exists, it updates; if not, it creates. No crashing 'show' command used here.
az acr create --name $AcrName --resource-group $ResourceGroupName --sku Basic --admin-enabled true --output none

# Now that it definitely exists, we fetch metadata
$acrLoginServer = az acr show --name $AcrName --resource-group $ResourceGroupName --query loginServer --output tsv
$acrCredentials = az acr credential show --name $AcrName --resource-group $ResourceGroupName --output json | ConvertFrom-Json
$acrUsername = $acrCredentials.username
$acrPassword = $acrCredentials.passwords[0].value

Write-Section 'Cloud image builds (Bandwidth Saver Mode)'
$services = @('api-gateway', 'user-service', 'auction-service', 'bid-service', 'notification-service')
foreach ($service in $services) {
    Write-Host "`n>>> Building $service in the cloud..." -ForegroundColor Cyan
    az acr build --registry $AcrName --image "$service`:latest" --file "$service/Dockerfile" .
}

Write-Section 'Deploying infrastructure with Bicep'
Write-Info 'Starting Bicep deployment (this handles DBs, Redis, and Apps)...'
$deploymentParameters = @(
    "location=$Location", "acrLoginServer=$acrLoginServer", "acrUsername=$acrUsername", "acrPassword=$acrPassword", 
    "jwtSecret=$jwtSecret", "dbPassword=$postgresPassword", "rabbitmqHost=$rabbitmqHost", "rabbitmqUsername=$rabbitmqUsername", 
    "rabbitmqPassword=$rabbitmqPassword", "googleClientId=$googleClientId", "googleClientSecret=$googleClientSecret", 
    "appOAuth2RedirectUri=$appOAuth2RedirectUri", "postgresqlServerName=$postgresqlServerName", 
    "postgresqlAdminUsername=$PostgreSqlAdminUsername", "postgresqlAdminPassword=$postgresPassword", 
    "redisCacheName=$redisCacheName", "zipkinUrl=$zipkinUrl", 'imageTag=latest'
)

az deployment group create --name "bootstrap-initial" --resource-group $ResourceGroupName --template-file 'infra/container-apps.bicep' --parameters @deploymentParameters --output none

Write-Section 'GitHub repository setup'
Write-Info 'Creating Service Principal for GitHub Actions...'
$spName = "sp-auction-$AcrName"
$sp = az ad sp create-for-rbac --name $spName --role Contributor --scopes "/subscriptions/$subscriptionId/resourceGroups/$ResourceGroupName" --output json | ConvertFrom-Json

$creds = @{ clientId=$sp.appId; clientSecret=$sp.password; subscriptionId=$subscriptionId; tenantId=$sp.tenant; resourceManagerEndpointUrl='https://management.azure.com/'; activeDirectoryEndpointUrl='https://login.microsoftonline.com/' }

Write-Host "`nCopy these into GitHub repository Secrets:" -ForegroundColor Cyan
Write-Host "AZURE_CREDENTIALS =" -ForegroundColor Yellow
Write-Host ($creds | ConvertTo-Json -Compress) -ForegroundColor White
Write-Host "`nACR_LOGIN_SERVER = $acrLoginServer" -ForegroundColor Yellow
Write-Host "ACR_USERNAME     = $acrUsername" -ForegroundColor Yellow
Write-Host "ACR_PASSWORD     = $acrPassword" -ForegroundColor Yellow

Write-Host "`nCopy these into GitHub repository Variables:" -ForegroundColor Cyan
Write-Host "AZURE_RESOURCE_GROUP = $ResourceGroupName" -ForegroundColor Yellow

Write-Host "`nBOOTSTRAP COMPLETE! Your cloud system is live." -ForegroundColor Green