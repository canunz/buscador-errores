#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Qa.Common.psm1') -Force -DisableNameChecking
$count = 0
foreach ($file in @('Qa.Common.psm1','Start-QaFrontend.ps1','Start-QaBackend.ps1','Verify-Preparation.ps1')) {
    $errorsFound=$null
    [void][System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $file),[ref]$null,[ref]$errorsFound)
    if ($errorsFound.Count -ne 0) { throw "Error de sintaxis en $file" }
    $count++
}
# Solo datos canario sintéticos: no leer ni modificar las variables del usuario.
$canary = @{
    SystemRoot=$env:SystemRoot; DB_URL='synthetic-staging'; DB_PASSWORD='synthetic-not-a-secret'
    GOOGLE_CLIENT_SECRET='synthetic-not-a-secret'; SPRING_APPLICATION_JSON='synthetic'
    JAVA_TOOL_OPTIONS='synthetic'; NODE_OPTIONS='synthetic'; HTTP_PROXY='synthetic'; QA_DB_PASSWORD='synthetic'
}
$info = New-QaProcessStartInfo -Executable 'C:\qa-synthetic\java.exe' -WorkingDirectory (Get-QaRoot) -OsEnvironment $canary
foreach ($name in @('DB_URL','DB_PASSWORD','GOOGLE_CLIENT_SECRET','SPRING_APPLICATION_JSON','JAVA_TOOL_OPTIONS','NODE_OPTIONS','HTTP_PROXY','QA_DB_PASSWORD')) {
    if ($info.Environment.ContainsKey($name)) { throw "Se heredó una variable no permitida: $name" }
    $count++
}
if ($info.Environment['PATH'].Contains('synthetic-staging')) { throw 'PATH no aislado' }
@{status='POWERSHELL_QA_CHECKS_OK';checks=$count;applications_started=0;credentials_generated=0} | ConvertTo-Json -Compress
