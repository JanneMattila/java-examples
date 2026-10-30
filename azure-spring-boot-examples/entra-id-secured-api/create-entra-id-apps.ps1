<#
.SYNOPSIS
    Creates the Entra ID app registrations for the Java Echo API demo and writes them to ".env".

.DESCRIPTION
    Uses Azure CLI ("az login" first) and Microsoft Graph (via "az rest") to create:
      1. "Java Echo API"                         - The protected API. Exposes "Echo.ReadWrite" as
                                                   delegated permission (scope) and application permission (app role).
      2. "Java Echo Client"                      - Allow-listed client app with client secret and "Echo.ReadWrite" app role.
      3. "Java Echo Client - Not Allow-listed"   - Client app with client secret and "Echo.ReadWrite" app role,
                                                   but NOT in the API allow-list. Demonstrates HTTP 403.
    Requires permissions to create app registrations and to grant admin consent (app role assignments),
    e.g., Cloud Application Administrator or Application Administrator.

.EXAMPLE
    .\create-entra-id-apps.ps1

.EXAMPLE
    .\create-entra-id-apps.ps1 -AllowAzureCli
    Also pre-authorizes Azure CLI for the "Echo.ReadWrite" scope and adds it to the allow-list,
    so that you can test delegated (user) tokens with "az account get-access-token".
#>
[CmdletBinding()]
param(
    [string]$ApiAppName = "Java Echo API",
    [string]$ClientAppName = "Java Echo Client",
    [string]$NotAllowedClientAppName = "Java Echo Client - Not Allow-listed",
    [string]$ApiUrl = "http://localhost:8080",
    [string]$EnvFile = (Join-Path $PSScriptRoot ".env"),
    [switch]$AllowAzureCli
)

$ErrorActionPreference = "Stop"
$permission = "Echo.ReadWrite"
$graphUrl = "https://graph.microsoft.com/v1.0"
$azureCliAppId = "04b07795-8ddb-461a-bbee-02f9e1bf7b46"

function Invoke-AzCli {
    $output = & az @args
    if ($LASTEXITCODE -ne 0) {
        throw "Command 'az $($args -join ' ')' failed with exit code $LASTEXITCODE."
    }
    $output -join "`n"
}

function Invoke-Graph([string]$Method, [string]$Uri, [hashtable]$Body) {
    $file = New-TemporaryFile
    try {
        [System.IO.File]::WriteAllText($file.FullName, ($Body | ConvertTo-Json -Depth 10))
        Invoke-AzCli rest --method $Method --uri $Uri --headers "Content-Type=application/json" --body "@$($file.FullName)"
    }
    finally {
        Remove-Item -Path $file.FullName -Force
    }
}

# Newly created objects take a moment to replicate in Entra ID
function Invoke-WithRetry([scriptblock]$Action, [int]$MaxAttempts = 6) {
    for ($attempt = 1; ; $attempt++) {
        try {
            return & $Action
        }
        catch {
            if ($attempt -ge $MaxAttempts) { throw }
            Write-Host "  Waiting for Entra ID replication ($attempt/$MaxAttempts)..."
            Start-Sleep -Seconds 10
        }
    }
}

$tenantId = Invoke-AzCli account show --query tenantId --output tsv
Write-Host "Using tenant: $tenantId"

foreach ($name in @($ApiAppName, $ClientAppName, $NotAllowedClientAppName)) {
    $existing = Invoke-AzCli ad app list --display-name $name --query "[].appId" --output tsv
    if ($existing) {
        throw "App registration '$name' already exists ($existing). Remove it first (.\delete-entra-id-apps.ps1) or use different names."
    }
}

#
# 1. API app registration
#
Write-Host "Creating API app registration '$ApiAppName'..."
$apiApp = Invoke-AzCli ad app create --display-name $ApiAppName --sign-in-audience AzureADMyOrg | ConvertFrom-Json
$apiAppIdUri = "api://$($apiApp.appId)"
# Scope and app role share the same value, so Entra ID requires them to share
# the same id, display name, description and enabled state as well
$permissionId = [guid]::NewGuid().ToString()
$permissionDisplayName = "Read and write echo"
$permissionDescription = "Allows calling the Java Echo API."

$apiSettings = @{
    requestedAccessTokenVersion = 2
    oauth2PermissionScopes      = @(
        @{
            id                      = $permissionId
            value                   = $permission
            type                    = "User"
            isEnabled               = $true
            adminConsentDisplayName = $permissionDisplayName
            adminConsentDescription = $permissionDescription
            userConsentDisplayName  = $permissionDisplayName
            userConsentDescription  = $permissionDescription
        }
    )
}
$apiAppSettings = @{
    identifierUris = @($apiAppIdUri)
    api            = $apiSettings
    appRoles       = @(
        @{
            id                 = $permissionId
            value              = $permission
            allowedMemberTypes = @("Application")
            displayName        = $permissionDisplayName
            description        = $permissionDescription
            isEnabled          = $true
        }
    )
}
Invoke-WithRetry { Invoke-Graph PATCH "$graphUrl/applications/$($apiApp.id)" $apiAppSettings } | Out-Null

if ($AllowAzureCli) {
    # Scope must exist before it can be pre-authorized, so this is a separate update
    $apiSettings.preAuthorizedApplications = @(
        @{ appId = $azureCliAppId; delegatedPermissionIds = @($permissionId) }
    )
    Invoke-WithRetry { Invoke-Graph PATCH "$graphUrl/applications/$($apiApp.id)" $apiAppSettings } | Out-Null
}

$apiSp = Invoke-WithRetry { Invoke-AzCli ad sp create --id $apiApp.appId | ConvertFrom-Json }

#
# 2. Client app registrations
#
function New-ClientApp([string]$Name) {
    Write-Host "Creating client app registration '$Name'..."
    $app = Invoke-AzCli ad app create --display-name $Name --sign-in-audience AzureADMyOrg | ConvertFrom-Json

    # "API permissions" in the portal: Application permission "Echo.ReadWrite"
    Invoke-WithRetry {
        Invoke-Graph PATCH "$graphUrl/applications/$($app.id)" @{
            requiredResourceAccess = @(
                @{
                    resourceAppId  = $apiApp.appId
                    resourceAccess = @(@{ id = $permissionId; type = "Role" })
                }
            )
        }
    } | Out-Null

    $sp = Invoke-WithRetry { Invoke-AzCli ad sp create --id $app.appId | ConvertFrom-Json }

    $secret = Invoke-WithRetry {
        Invoke-AzCli ad app credential reset --id $app.appId --display-name "invoke-api.ps1" --years 1 --append --query password --output tsv
    }

    # "Grant admin consent" for the application permission = app role assignment
    Write-Host "  Granting admin consent for '$permission'..."
    Invoke-WithRetry {
        Invoke-Graph POST "$graphUrl/servicePrincipals/$($apiSp.id)/appRoleAssignedTo" @{
            principalId = $sp.id
            resourceId  = $apiSp.id
            appRoleId   = $permissionId
        }
    } | Out-Null

    [pscustomobject]@{ AppId = $app.appId; Secret = $secret }
}

$client = New-ClientApp $ClientAppName
$notAllowedClient = New-ClientApp $NotAllowedClientAppName

#
# 3. Write .env file (used by the Java app and the PowerShell scripts)
#
$allowedClientIds = @($client.AppId)
if ($AllowAzureCli) { $allowedClientIds += $azureCliAppId }

$envContent = @"
# Generated by create-entra-id-apps.ps1. Contains secrets, do not commit!

# --- Java API configuration ---
TENANT_ID=$tenantId
API_CLIENT_ID=$($apiApp.appId)
API_APP_ID_URI=$apiAppIdUri
ALLOWED_CLIENT_IDS=$($allowedClientIds -join ',')

# --- invoke-api.ps1 configuration ---
API_URL=$ApiUrl
CLIENT_ID=$($client.AppId)
CLIENT_SECRET=$($client.Secret)
NOT_ALLOWED_CLIENT_ID=$($notAllowedClient.AppId)
NOT_ALLOWED_CLIENT_SECRET=$($notAllowedClient.Secret)
"@

# UTF-8 without BOM so that Java, Docker and PowerShell can all read it
[System.IO.File]::WriteAllText($EnvFile, $envContent)

Write-Host ""
Write-Host "Done! Configuration written to '$EnvFile'." -ForegroundColor Green
Write-Host "  API app id:                $($apiApp.appId)"
Write-Host "  API app id URI:            $apiAppIdUri"
Write-Host "  Allow-listed client:       $($client.AppId)"
Write-Host "  Not allow-listed client:   $($notAllowedClient.AppId)"
if ($AllowAzureCli) { Write-Host "  Azure CLI pre-authorized:  $azureCliAppId" }
Write-Host ""
Write-Host "Note: New client secrets can take a minute before they can be used."
