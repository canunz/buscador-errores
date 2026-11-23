#requires -Version 7.4
param([Parameter(Mandatory)][string]$EvidenceFile)
$ErrorActionPreference='Stop'
Add-Type -Path (Join-Path $PSScriptRoot 'QaBackendJob.cs')
Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -Force -DisableNameChecking
$info=New-QaProcessStartInfo -Executable (Get-Process -Id $PID).Path -WorkingDirectory $PSScriptRoot
foreach($arg in @('-NoProfile','-NonInteractive','-Command','Start-Sleep -Seconds 60')){$info.ArgumentList.Add($arg)}
$job=[QaBackendJob]::new()
try{
    $job.Start($info)
    [IO.File]::WriteAllText($EvidenceFile,[string]$job.ProcessId)
    Start-Sleep -Seconds 60
}finally{$job.Dispose()}
