<#
.SYNOPSIS
    Deletes the Entra ID app registrations created by create-entra-id-apps.ps1 (ids are read from ".env").
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot ".env")
)

$ErrorActionPreference = "Stop"

$config = @{}
foreach ($line in Get-Content -Path $EnvFile) {
    if ($line -match '^\s*([^#=\s][^=]*?)\s*=\s*(.*?)\s*$') {
        $config[$Matches[1]] = $Matches[2]
    }
}

foreach ($key in @("CLIENT_ID", "NOT_ALLOWED_CLIENT_ID", "API_CLIENT_ID")) {
    $appId = $config[$key]
    if ($appId -and $PSCmdlet.ShouldProcess($appId, "Delete app registration ($key)")) {
        Write-Host "Deleting app registration $key = $appId"
        az ad app delete --id $appId
        if ($LASTEXITCODE -ne 0) { throw "Failed to delete app registration $appId" }
    }
}

if ($PSCmdlet.ShouldProcess($EnvFile, "Delete file")) {
    Remove-Item -Path $EnvFile
}
