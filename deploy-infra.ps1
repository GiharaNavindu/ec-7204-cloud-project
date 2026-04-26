param(
    [string]$ResourceGroupName = "rg-ruhuna-auction",
    [string]$Location = "eastus"
)

$ErrorActionPreference = "Stop"

# -------------------------
# Fill these placeholders
# -------------------------
$AcrLoginServer = "<YOUR_ACR_LOGIN_SERVER>"            # Example: acruhuna4751.azurecr.io
$AcrUsername    = "<YOUR_ACR_USERNAME>"
$AcrPassword    = "<YOUR_ACR_PASSWORD>"

$JwtSecret      = "<YOUR_JWT_SECRET_MIN_64_CHARS>"
$DbUrl          = "<YOUR_DB_JDBC_URL>"                 # Example: jdbc:postgresql://<server>.postgres.database.azure.com:5432/user_db
$DbUsername     = "<YOUR_DB_USERNAME>"                 # Example: admin
$DbPassword     = "<YOUR_DB_PASSWORD>"

$TemplateFile   = "infra/container-apps.bicep"
$DeploymentName = "aca-core-$(Get-Date -Format 'yyyyMMdd-HHmmss')"

# Guardrails to avoid deploying with placeholders
$placeholders = @(
    "<YOUR_ACR_LOGIN_SERVER>",
    "<YOUR_ACR_USERNAME>",
    "<YOUR_ACR_PASSWORD>",
    "<YOUR_JWT_SECRET_MIN_64_CHARS>",
    "<YOUR_DB_JDBC_URL>",
    "<YOUR_DB_USERNAME>",
    "<YOUR_DB_PASSWORD>"
)

$currentValues = @(
    $AcrLoginServer,
    $AcrUsername,
    $AcrPassword,
    $JwtSecret,
    $DbUrl,
    $DbUsername,
    $DbPassword
)

if ($currentValues | Where-Object { $_ -in $placeholders }) {
    throw "Please replace all placeholder values in deploy-infra.ps1 before running."
}

if ($JwtSecret.Length -lt 64) {
    throw "JWT secret must be at least 64 characters."
}

Write-Host "Deploying Bicep template: $TemplateFile"
Write-Host "Resource group: $ResourceGroupName"
Write-Host "Location: $Location"
Write-Host "Deployment name: $DeploymentName"

az deployment group create `
  --name $DeploymentName `
  --resource-group $ResourceGroupName `
  --template-file $TemplateFile `
  --parameters `
    location=$Location `
    acrLoginServer=$AcrLoginServer `
    acrUsername=$AcrUsername `
    acrPassword=$AcrPassword `
    jwtSecret=$JwtSecret `
    dbUrl=$DbUrl `
    dbUsername=$DbUsername `
    dbPassword=$DbPassword

Write-Host "Infrastructure deployment completed successfully."
