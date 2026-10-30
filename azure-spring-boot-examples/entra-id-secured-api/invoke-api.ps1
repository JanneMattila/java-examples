<#
.SYNOPSIS
    Calls the Java Echo API using Entra ID authentication (OAuth 2.0 client credentials flow).

.DESCRIPTION
    Reads configuration and secrets from ".env" file and uses only built-in PowerShell cmdlets.
    Runs three calls to demonstrate the validation layers of the API:
      1. Without access token                     -> expected HTTP 401
      2. Allow-listed client with app role        -> expected HTTP 200 (echo response)
      3. Client which is not in the allow-list    -> expected HTTP 403

.EXAMPLE
    .\invoke-api.ps1

.EXAMPLE
    .\invoke-api.ps1 -Message "Hello Java!" -EnvFile .\other.env
#>
[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot ".env"),
    [string]$Message = "Hello from PowerShell"
)

$ErrorActionPreference = "Stop"

# Read KEY=VALUE lines from .env file (comments and empty lines are skipped)
$config = @{}
foreach ($line in Get-Content -Path $EnvFile) {
    if ($line -match '^\s*([^#=\s][^=]*?)\s*=\s*(.*?)\s*$') {
        $config[$Matches[1]] = $Matches[2]
    }
}

$apiUrl = if ($config["API_URL"]) { $config["API_URL"].TrimEnd("/") } else { "http://localhost:8080" }

function Get-AccessToken([string]$ClientId, [string]$ClientSecret) {
    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "https://login.microsoftonline.com/$($config["TENANT_ID"])/oauth2/v2.0/token" `
        -Body @{
            grant_type    = "client_credentials"
            client_id     = $ClientId
            client_secret = $ClientSecret
            scope         = "$($config["API_APP_ID_URI"])/.default"
        }
    $response.access_token
}

function Invoke-EchoApi([string]$Title, [int]$ExpectedStatusCode, [string]$AccessToken) {
    Write-Host ""
    Write-Host "=== $Title (expected HTTP $ExpectedStatusCode) ===" -ForegroundColor Cyan

    $headers = @{}
    if ($AccessToken) {
        $headers["Authorization"] = "Bearer $AccessToken"
    }
    $body = @{
        message   = $Message
        timestamp = (Get-Date).ToString("o")
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod `
            -Method Post `
            -Uri "$apiUrl/api/echo" `
            -Headers $headers `
            -ContentType "application/json" `
            -Body $body
        $statusCode = 200
        $response | ConvertTo-Json -Depth 10 | Write-Host
    }
    catch {
        if ($null -eq $_.Exception.Response) { throw }
        $statusCode = [int]$_.Exception.Response.StatusCode
        Write-Host "Error: $($_.Exception.Message)"
    }

    $color = if ($statusCode -eq $ExpectedStatusCode) { "Green" } else { "Red" }
    Write-Host "HTTP $statusCode" -ForegroundColor $color
}

Invoke-EchoApi -Title "1) No access token" -ExpectedStatusCode 401

$accessToken = Get-AccessToken $config["CLIENT_ID"] $config["CLIENT_SECRET"]
Invoke-EchoApi -Title "2) Allow-listed client" -ExpectedStatusCode 200 -AccessToken $accessToken

if ($config["NOT_ALLOWED_CLIENT_ID"]) {
    $accessToken = Get-AccessToken $config["NOT_ALLOWED_CLIENT_ID"] $config["NOT_ALLOWED_CLIENT_SECRET"]
    Invoke-EchoApi -Title "3) Not allow-listed client" -ExpectedStatusCode 403 -AccessToken $accessToken
}
