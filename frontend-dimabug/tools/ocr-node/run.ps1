#requires -Version 7.4
[CmdletBinding()]
param(
    [ValidateSet('Build','Test','Fixture')][string]$Mode='Test',
    [ValidateSet('error-legible.png','error-ambiguo.png','error-borroso.png','sin-texto.png')]
    [string]$Fixture='error-legible.png'
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$node=Join-Path $root 'qa/runtime/tools/node-v22.23.5-win-x64/node.exe'
if(-not(Test-Path -LiteralPath $node)){throw 'Falta Node 22 portable verificado. Consulta README.md; no se descarga automaticamente.'}
Import-Module (Join-Path $root 'qa/Qa.Common.psm1')
$null=New-Item -ItemType Directory -Path (Join-Path $root 'qa/runtime/tmp') -Force
$entry=switch($Mode){
    'Build' {'build.cjs'}
    'Test' {'test/run.cjs'}
    'Fixture' {'dist/tools/ocr-node/src/cli.js'}
}
$info=New-QaProcessStartInfo -Executable $node -WorkingDirectory $PSScriptRoot
$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$info.ArgumentList.Add((Join-Path $PSScriptRoot $entry))
if($Mode -eq 'Fixture'){$info.ArgumentList.Add($Fixture)}
$process=[Diagnostics.Process]::Start($info)
$out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync()
try{
    $process.WaitForExit()
    Write-Output $out.GetAwaiter().GetResult()
    $stderr=$err.GetAwaiter().GetResult()
    if($stderr){Write-Output $stderr}
    exit $process.ExitCode
}finally{$process.Dispose()}
