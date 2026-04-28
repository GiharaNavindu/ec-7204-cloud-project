param(
[string]$ResourceGroupName = 'rg-ruhuna-auction',
[string]$Location = 'eastus',
[string]$AcrName = 'acruhunaauction',
[string]$PostgreSqlAdminUsername = 'pgadmin',
[switch]$PromptSecrets
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Write-Section {
param([string]$Message)
Write-Host "`n=== $Message ===" -ForegroundColor Cyan
}

function Write-Info {
param([string]$Message)
Write-Host "  $Message" -ForegroundColor Gray
}

function Assert-CommandExists {
param([string]$CommandName)
if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
throw "Required command '$CommandName' was not found. Install it and try again."
}
}

function Assert-AcrName {
param([string]$Name)
if ($Name -notmatch '^[a-z0-9]{5,50}$') {
throw "ACR name must be 5-50 characters, lowercase letters and numbers only."
}
}

function New-RandomSecret {
param([int]$ByteCount)
$bytes = New-Object byte[] $ByteCount

# PowerShell 5.1 compatible random number generation
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()

[Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
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

function Read-RequiredPlainText {
param([string]$Prompt)
$value = Read-Host -Prompt $Prompt
if ([string]::IsNullOrWhiteSpace($value)) {
throw "$Prompt cannot be empty."
}
return $value.Trim()
}

function ConvertTo-CompactJson {
param([Parameter(Mandatory = $true)]$InputObject)
$InputObject | ConvertTo-Json -Depth 10 -Compress
}

Assert-CommandExists -CommandName 'az'
Assert-AcrName -Name $AcrName

Write-Section 'Bootstrap configuration'
Write-Info "Resource group : $ResourceGroupName"
Write-Info "Region         : $Location"
Write-Info "ACR name       : $AcrName"

$subscriptionId = az account show --query id --output tsv 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($subscriptionId)) {
throw 'You must be logged in to Azure CLI before running this script.'
}

Write-Info "Subscription   : $subscriptionId"

Write-Section 'Secrets and dependency inputs'
$postgresPassword = Read-OrGenerateSecret -Prompt 'Enter PostgreSQL password for the Azure Flexible Server and application DB users' -LengthInBytes 48 -PromptForValue:$PromptSecrets
$jwtSecret = Read-OrGenerateSecret -Prompt 'Enter JWT secret for all services' -LengthInBytes 64 -PromptForValue:$PromptSecrets

$rabbitmqHost = Read-RequiredPlainText -Prompt 'Enter RabbitMQ host for the cloud deployment'
$rabbitmqPassword = Read-OrGenerateSecret -Prompt 'Enter RabbitMQ password' -LengthInBytes 32 -PromptForValue:$PromptSecrets
$googleClientId = Read-RequiredPlainText -Prompt 'Enter Google OAuth Client ID'
$googleClientSecret = Read-OrGenerateSecret -Prompt 'Enter Google OAuth Client Secret' -LengthInBytes 32 -PromptForValue:$PromptSecrets
$appOAuth2RedirectUri = Read-Host -Prompt 'Enter OAuth2 redirect URI for the gateway/public frontend callback (press Enter to skip)'
$zipkinUrl = Read-Host -Prompt 'Enter Zipkin URL (press Enter to skip)'

$postgresqlServerName = ('pg-' + $AcrName).ToLowerInvariant()
$redisCacheName = ('redis-' + $AcrName).ToLowerInvariant()

Write-Section 'Creating base Azure infrastructure'
az group create --name $ResourceGroupName --location $Location --output none
if ($LASTEXITCODE -ne 0) { throw "Failed to create Azure Resource Group." }

Write-Info 'Registering Azure providers used by the Bicep template...'
$providers = @(
'Microsoft.App',
'Microsoft.OperationalInsights',
'Microsoft.Network',
'Microsoft.DBforPostgreSQL',
'Microsoft.Cache',
'Microsoft.ManagedIdentity',
'Microsoft.ContainerRegistry',
'Microsoft.Insights'
)

foreach ($provider in $providers) {
az provider register --namespace $provider --wait --output none
}

Write-Info 'Creating Azure Container Registry with Basic SKU and admin access...'
$acrLookup = az acr show --name $AcrName --resource-group $ResourceGroupName --output none 2>$null
if ($LASTEXITCODE -ne 0) {
az acr create --name $AcrName --resource-group $ResourceGroupName --sku Basic --admin-enabled true --output none
if ($LASTEXITCODE -ne 0) { throw "Failed to create Azure Container Registry." }
} else {
az acr update --name $AcrName --resource-group $ResourceGroupName --admin-enabled true --output none
if ($LASTEXITCODE -ne 0) { throw "Failed to update Azure Container Registry." }
}

$acrLoginServer = az acr show --name $AcrName --resource-group $ResourceGroupName --query loginServer --output tsv
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($acrLoginServer)) {
throw 'Unable to resolve the ACR login server.'
}

$acrCredentials = az acr credential show --name $AcrName --resource-group $ResourceGroupName --output json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) {
throw 'Unable to read ACR credentials.'
}

$acrUsername = $acrCredentials.username
$acrPassword = $acrCredentials.passwords[0].value

Write-Section 'Cloud image builds via Azure Container Registry'
$services = @(
'api-gateway',
'user-service',
'auction-service',
'bid-service',
'notification-service'
)

foreach ($service in $services) {
$dockerfilePath = "$service/Dockerfile"
if (-not (Test-Path $dockerfilePath)) {
throw "Missing Dockerfile for service '$service' at '$dockerfilePath'."
}

Write-Info "Building $service in Azure using $dockerfilePath"
& az acr build --registry $AcrName --image "$service`:latest" --file $dockerfilePath .
if ($LASTEXITCODE -ne 0) {
throw "Azure Container Registry build failed for '$service'."
}
}

Write-Section 'Deploying infrastructure with Bicep'
$templateFile = 'infra/container-apps.bicep'
if (-not (Test-Path $templateFile)) {
throw "Bicep template not found at '$templateFile'."
}

$deploymentName = "bootstrap-aca-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
$deploymentParameters = @(
"location=$Location"
"acrLoginServer=$acrLoginServer"
"acrUsername=$acrUsername"
"acrPassword=$acrPassword"
"jwtSecret=$jwtSecret"
"dbPassword=$postgresPassword"
"rabbitmqHost=$rabbitmqHost"
"rabbitmqPassword=$rabbitmqPassword"
"googleClientId=$googleClientId"
"googleClientSecret=$googleClientSecret"
"appOAuth2RedirectUri=$appOAuth2RedirectUri"
"postgresqlServerName=$postgresqlServerName"
"postgresqlAdminUsername=$PostgreSqlAdminUsername"
"postgresqlAdminPassword=$postgresPassword"
"redisCacheName=$redisCacheName"
"zipkinUrl=$zipkinUrl"
'imageTag=latest'
)

$deploymentResult = & az deployment group create `
--name $deploymentName `
--resource-group $ResourceGroupName `
--template-file $templateFile `
--parameters @deploymentParameters `
--output json | ConvertFrom-Json

if ($LASTEXITCODE -ne 0) {
throw 'Bicep deployment failed.'
}

$outputs = $deploymentResult.properties.outputs

Write-Section 'Deployment outputs'
if ($outputs.apiGatewayPublicUrl) {
Write-Info "API Gateway public URL : $($outputs.apiGatewayPublicUrl.value)"
}
if ($outputs.userServiceInternalFqdn) {
Write-Info "User Service FQDN      : $($outputs.userServiceInternalFqdn.value)"
}
if ($outputs.auctionServiceInternalFqdn) {
Write-Info "Auction Service FQDN   : $($outputs.auctionServiceInternalFqdn.value)"
}
if ($outputs.bidServiceInternalFqdn) {
Write-Info "Bid Service FQDN       : $($outputs.bidServiceInternalFqdn.value)"
}
if ($outputs.notificationServiceInternalFqdn) {
Write-Info "Notification FQDN      : $($outputs.notificationServiceInternalFqdn.value)"
}
if ($outputs.postgresqlFqdn) {
Write-Info "PostgreSQL FQDN        : $($outputs.postgresqlFqdn.value)"
}
if ($outputs.redisCacheHostName) {
Write-Info "Redis host name        : $($outputs.redisCacheHostName.value)"
}

Write-Section 'GitHub repository setup'
$servicePrincipalName = "sp-ruhuna-auction-$AcrName"
$servicePrincipalScope = "/subscriptions/$subscriptionId/resourceGroups/$ResourceGroupName"
$spResult = & az ad sp create-for-rbac `
--name $servicePrincipalName `
--role Contributor `
--scopes $servicePrincipalScope `
--output json | ConvertFrom-Json

if ($LASTEXITCODE -ne 0) {
throw 'Failed to create the GitHub deployment service principal.'
}

$azureCredentials = [ordered]@{
clientId = $spResult.appId
clientSecret = $spResult.password
subscriptionId = $subscriptionId
tenantId = $spResult.tenant
resourceManagerEndpointUrl = 'https://management.azure.com/'
activeDirectoryEndpointUrl = 'https://login.microsoftonline.com/'
}

$azureCredentialsJson = ConvertTo-CompactJson -InputObject $azureCredentials

Write-Host ''
Write-Host 'Copy these into GitHub repository Secrets:' -ForegroundColor Cyan
Write-Host '  AZURE_CREDENTIALS =' -ForegroundColor Yellow
Write-Host "  $azureCredentialsJson" -ForegroundColor White
Write-Host "  ACR_LOGIN_SERVER = $acrLoginServer" -ForegroundColor Yellow
Write-Host "  ACR_USERNAME     = $acrUsername" -ForegroundColor Yellow
Write-Host "  ACR_PASSWORD     = $acrPassword" -ForegroundColor Yellow

Write-Host ''
Write-Host 'Copy these into GitHub repository Variables:' -ForegroundColor Cyan
Write-Host "  AZURE_RESOURCE_GROUP                          = $ResourceGroupName" -ForegroundColor Yellow
Write-Host "  AZURE_API_GATEWAY_CONTAINER_APP_NAME          = api-gateway" -ForegroundColor Yellow
Write-Host "  AZURE_USER_SERVICE_CONTAINER_APP_NAME         = user-service" -ForegroundColor Yellow
Write-Host "  AZURE_AUCTION_SERVICE_CONTAINER_APP_NAME      = auction-service" -ForegroundColor Yellow
Write-Host "  AZURE_BID_SERVICE_CONTAINER_APP_NAME          = bid-service" -ForegroundColor Yellow
Write-Host "  AZURE_NOTIFICATION_SERVICE_CONTAINER_APP_NAME = notification-service" -ForegroundColor Yellow

Write-Host ''
Write-Host 'Bootstrap complete. Your next pushes can use .github/workflows/deploy.yml directly.' -ForegroundColor Green
