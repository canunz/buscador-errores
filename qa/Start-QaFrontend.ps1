#requires -Version 7.0
[CmdletBinding()]
param([switch]$Start)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Qa.Common.psm1') -Force -DisableNameChecking
$root = Get-QaRoot
Assert-QaManifest
if (-not $Start) {
    'QA_FRONTEND_PREPARADO: sin ejecución; 127.0.0.1:14200 -> 127.0.0.1:19090; entorno limpio'
    return
}
Assert-QaPortFree -Port 14200
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'runtime/tmp') -PathType Container)) { throw 'Falta preparar el directorio runtime QA en una fase autorizada.' }
$node = (Get-Command node.exe -ErrorAction Stop).Source
$cli = Join-Path $root 'frontend-dimabug/node_modules/@angular/cli/bin/ng.js'
if (-not (Test-Path -LiteralPath $cli -PathType Leaf)) { throw 'Dependencias frontend no disponibles; no se instala nada automáticamente.' }
$info = New-QaProcessStartInfo -Executable $node -WorkingDirectory (Join-Path $root 'frontend-dimabug')
foreach ($arg in @($cli,'serve','buscador-errores','--configuration=qa-local')) { $info.ArgumentList.Add($arg) }
$process = [Diagnostics.Process]::Start($info)
try { $process.WaitForExit(); if ($process.ExitCode -ne 0) { throw 'Frontend QA terminó con error.' } }
finally { $process.Dispose() }
