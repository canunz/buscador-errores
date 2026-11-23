#requires -Version 7.0
[CmdletBinding()]
param(
    [switch]$Start,
    [switch]$CheckCustody
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Qa.Common.psm1') -Force -DisableNameChecking
$root = Get-QaRoot
Assert-QaManifest
if ($Start) { throw 'QA_START_NOT_AUTHORIZED_IN_PREPARATION: falta autorizar la ventana supervisada y el desbloqueo temporal. No se inicia ningún proceso.' }
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'candidate.json') -Raw | ConvertFrom-Json
if (-not $manifest.artifacts.backend -or -not $manifest.artifacts.backend.bundle) { throw 'Falta registrar el paquete QA con control TLS.' }
$bundleFile = Join-Path $root $manifest.artifacts.backend.bundle.path
if ((Get-FileHash -LiteralPath $bundleFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $manifest.artifacts.backend.bundle.sha256) { throw 'El paquete QA cambió.' }
$bundle = Get-Content -LiteralPath $bundleFile -Raw | ConvertFrom-Json
foreach ($entry in $bundle.files.PSObject.Properties) {
    $path = [IO.Path]::GetFullPath((Join-Path $root $entry.Value.path))
    if (-not $path.StartsWith((Join-Path $root 'qa/artifacts/'),[StringComparison]::OrdinalIgnoreCase) -or
        (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.Value.sha256) { throw 'Archivo del paquete QA no coincide.' }
}
if ($CheckCustody) {
    Import-Module (Join-Path $PSScriptRoot 'backend-tls/Qa.Custody.psm1') -Force
    $protectedPassword = Read-QaDatabasePassword
    try { if ($protectedPassword.Length -lt 32) { throw 'Custodia QA inválida.' } }
    finally { $protectedPassword.Dispose() }
    $protectedJwt = Read-QaJwtSecret
    try { if ($protectedJwt.Length -ne 88) { throw 'Custodia JWT QA inválida.' } }
    finally { $protectedJwt.Dispose() }
}
if (-not $Start) {
    @{estado='QA_BACKEND_PREPARADO_SIN_ARRANQUE';tls_bundle_verificado=$true;custodia_verificada=[bool]$CheckCustody;
      jwt_custodia_verificada=[bool]$CheckCustody;supervisor_implementado=$true;ventana_pendiente_autorizacion=$true;
      cuentas_desbloqueadas=$false;aplicaciones_iniciadas=$false} | ConvertTo-Json -Compress
    return
}
