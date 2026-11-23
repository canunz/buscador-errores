#requires -Version 7.4
$ErrorActionPreference='Stop'
Add-Type -Path (Join-Path $PSScriptRoot 'QaBackendJob.cs')
Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -Force -DisableNameChecking
$passed=0
function New-TestInfo([string]$Command){
    $i=New-QaProcessStartInfo -Executable (Get-Process -Id $PID).Path -WorkingDirectory $PSScriptRoot
    foreach($a in @('-NoProfile','-NonInteractive','-Command',$Command)){$i.ArgumentList.Add($a)}
    return $i
}
$job=[QaBackendJob]::new()
try{
    $job.Start((New-TestInfo 'Start-Sleep -Seconds 60'))
    if($job.HasExited){throw 'QA_TEST_CHILD_EARLY_EXIT'};$passed++
    if(-not $job.Stop() -or -not $job.HasExited){throw 'QA_TEST_STOP_FAILED'};$passed++
    if(-not $job.Stop()){throw 'QA_TEST_IDEMPOTENT_STOP_FAILED'};$passed++
}finally{$job.Dispose()}
$job=[QaBackendJob]::new();$child=$null
try{
    $job.Start((New-TestInfo 'Start-Sleep -Seconds 60'))
    $child=[Diagnostics.Process]::GetProcessById($job.ProcessId);$null=$child.Handle
    $job.Dispose()
    if(-not $child.WaitForExit(5000)){throw 'QA_TEST_DISPOSE_KILL_FAILED'};$passed++
}finally{$job.Dispose();if($child){$child.Dispose()}}
$job=[QaBackendJob]::new()
try{
    $job.Start((New-TestInfo 'exit 7'))
    $until=[DateTime]::UtcNow.AddSeconds(10)
    while(-not $job.HasExited -and [DateTime]::UtcNow -lt $until){Start-Sleep -Milliseconds 50}
    if(-not $job.HasExited){throw 'QA_TEST_EXIT_NOT_OBSERVED'};$passed++
}finally{$job.Dispose()}
# Abrupt owner death: no finally/Dispose in owner; OS must kill its job's child.
$evidence=Join-Path (Get-QaRoot) ('qa/runtime/tmp/job-owner-'+[Guid]::NewGuid().ToString('N')+'.txt')
$ownerInfo=New-QaProcessStartInfo -Executable (Get-Process -Id $PID).Path -WorkingDirectory $PSScriptRoot
foreach($arg in @('-NoProfile','-NonInteractive','-File',(Join-Path $PSScriptRoot 'test_job_owner.ps1'),'-EvidenceFile',$evidence)){$ownerInfo.ArgumentList.Add($arg)}
$owner=[Diagnostics.Process]::Start($ownerInfo);$ownedChild=$null
try{
    $until=[DateTime]::UtcNow.AddSeconds(15)
    while(-not (Test-Path -LiteralPath $evidence) -and [DateTime]::UtcNow -lt $until){Start-Sleep -Milliseconds 50}
    if(-not (Test-Path -LiteralPath $evidence)){throw 'QA_TEST_OWNER_NOT_READY'}
    $identifier=[int]([IO.File]::ReadAllText($evidence))
    $ownedChild=[Diagnostics.Process]::GetProcessById($identifier);$null=$ownedChild.Handle
    $owner.Kill();$null=$owner.WaitForExit(5000)
    if(-not $ownedChild.WaitForExit(5000)){throw 'QA_TEST_OWNER_DEATH_KILL_FAILED'};$passed++
}finally{
    if(-not $owner.HasExited){$owner.Kill();$null=$owner.WaitForExit(5000)}
    $owner.Dispose();if($ownedChild){$ownedChild.Dispose()}
}
@{estado='QA_WINDOWS_JOB_TESTS_OK';checks=$passed;spring_iniciado=$false;angular_iniciado=$false;sql_ejecutado=$false}|ConvertTo-Json -Compress
