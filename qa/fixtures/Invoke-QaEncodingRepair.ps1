#requires -Version 7.4
[CmdletBinding()]param([switch]$RunAuthorizedRepair,[string]$Candidate,[string]$HistoricalBackup)
$ErrorActionPreference='Stop'
if(-not $RunAuthorizedRepair){'{"status":"QA_REPAIR_PREPARED_NO_SQL"}';return}
# Requires a NEW, independent user authorization; never invoked by tests/defaults.
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $root 'qa/Qa.Common.psm1') -DisableNameChecking
Import-Module (Join-Path $root 'qa/backend-tls/Qa.Supervisor.psm1')
Import-Module (Join-Path $root 'qa/backend-tls/Qa.Close.psm1')
Import-Module (Join-Path $PSScriptRoot 'Qa.RepairAcl.psm1')
Assert-QaManifest
$manifest=Get-Content (Join-Path $root 'qa/candidate.json') -Raw|ConvertFrom-Json
if($Candidate -notmatch '^QA-local-[a-f0-9]+-[a-f0-9]+$' -or $manifest.candidate_id -cne $Candidate){throw 'QA_REPAIR_CANDIDATE'}
$historical=[IO.Path]::GetFullPath($HistoricalBackup)
if(-not $historical.StartsWith((Join-Path $root 'qa/runtime/fixture-runs')+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'QA_REPAIR_HISTORY'}
Assert-QaPortFree -Port 19090;Assert-QaPortFree -Port 14200
$folder=New-QaBackendRunDirectory
$directory=Join-Path (Join-Path $root 'qa/runtime/fixture-runs') ([Guid]::NewGuid().ToString('N'))
$acl=Get-Acl -LiteralPath $folder
$null=[IO.FileSystemAclExtensions]::CreateDirectory($acl,$directory)
Assert-QaRepairAcl -Directory $directory
$context=@{candidate=$Candidate;historical_backup=$historical;directory=$directory;parent=$PID;sentinel=0}
[IO.File]::WriteAllText((Join-Path $folder 'repair-context.json'),($context|ConvertTo-Json))
Add-Type -Path (Join-Path $root 'qa/backend-tls/QaBackendJob.cs')
$job=[QaBackendJob]::new();$sentinel=$null
$mutex=[Threading.Mutex]::new($false,'Local\DimaBugQa13318EncodingRepair')
$owned=$false;$attemptCreated=$false
$report=@{transaction='UNKNOWN';security='UNKNOWN';success=$false;automatic_retry=$false;directory=$directory}
function New-RepairProcessInfo([string]$Script,[string[]]$Arguments){
    $python=(Get-Command python.exe).Source
    $info=New-QaProcessStartInfo -Executable $python -WorkingDirectory $root
    $dirs=@(Split-Path $python)
    foreach($tool in @('docker.exe','pwsh.exe','git.exe')){$dirs+=Split-Path (Get-Command $tool -ErrorAction Stop).Source}
    $info.Environment['PATH']=(@($dirs|Select-Object -Unique)+@((Join-Path $env:SystemRoot 'System32'),$env:SystemRoot)) -join ';'
    foreach($a in @('-I','-B',$Script)+$Arguments){$info.ArgumentList.Add($a)}
    return $info
}
try {
    try{$owned=$mutex.WaitOne(0)}catch [Threading.AbandonedMutexException]{$owned=$true;throw 'QA_REPAIR_PREVIOUS_OWNER_DIED'}
    if(-not $owned){throw 'QA_REPAIR_ALREADY_RUNNING'}
    $attempt=Join-Path (Join-Path $root 'qa/runtime/fixture-runs') ("repair-$Candidate.attempt")
    $stream=[IO.File]::Open($attempt,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
    try{$bytes=[Text.Encoding]::UTF8.GetBytes($folder);$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()}
    $attemptCreated=$true
    $info=New-RepairProcessInfo (Join-Path $PSScriptRoot 'repair_sentinel.py') @('--run-authorized-window','--folder',$folder,'--parent',[string]$PID)
    # Outside worker job: survives unexpected death of principal PowerShell.
    $sentinel=[Diagnostics.Process]::Start($info)
    $until=[DateTime]::UtcNow.AddSeconds(10)
    while(-not (Test-Path (Join-Path $folder 'sentinel-ready.json'))){
        if($sentinel.HasExited -or [DateTime]::UtcNow -ge $until){throw 'QA_REPAIR_GUARD_NOT_READY'}
        Start-Sleep -Milliseconds 100
    }
    if((Get-Content (Join-Path $folder 'sentinel-ready.json') -Raw|ConvertFrom-Json).estado -cne 'LISTO'){throw 'QA_REPAIR_GUARD_NOT_READY'}
    $context.sentinel=$sentinel.Id
    [IO.File]::WriteAllText((Join-Path $folder 'repair-context.json'),($context|ConvertTo-Json))
    [IO.File]::WriteAllText((Join-Path $folder 'principal-ready.json'),'{"ready":true}')
    $worker=New-RepairProcessInfo (Join-Path $PSScriptRoot 'repair_worker.py') @('--run-authorized-repair','--folder',$folder)
    $job.Start($worker)
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while(-not $job.HasExited){
        if($sentinel.HasExited -or $watch.Elapsed.TotalSeconds -ge 155){throw 'QA_REPAIR_DEADLINE_OR_GUARD_EXIT'}
        Start-Sleep -Milliseconds 100
    }
    $report=Get-Content (Join-Path $directory 'repair-result.json') -Raw|ConvertFrom-Json -AsHashtable
    if($report.success -and -not (Test-Path (Join-Path $directory 'receipt.prepared.json'))){throw 'QA_REPAIR_RECEIPT_MISSING'}
} catch {$report.failure='CONTROL_OR_WORKER_ERROR_SUPPRESSED';$report.success=$false}
finally {
    $stopped=$job.Stop();$job.Dispose()
    $principalConfirmed=$false
    if($owned -and $attemptCreated){try {
        $closeInfo=New-RepairProcessInfo (Join-Path $PSScriptRoot 'repair_close_cli.py') @('--execute-close','--folder',$folder)
        $closeInfo.RedirectStandardOutput=$true;$closeInfo.RedirectStandardError=$true
        $closeProcess=[Diagnostics.Process]::Start($closeInfo)
        try {
            $output=$closeProcess.StandardOutput.ReadToEndAsync();$errorOutput=$closeProcess.StandardError.ReadToEndAsync()
            if($closeProcess.WaitForExit(45000)){
                $closeResult=$output.GetAwaiter().GetResult()|ConvertFrom-Json
                $principalConfirmed=$closeProcess.ExitCode -eq 0 -and $closeResult.closed -eq $true -and $closeResult.security -ceq 'VERIFIED'
            }else{$closeProcess.Kill($true)}
        }finally{$closeProcess.Dispose()}
    }catch{$principalConfirmed=$false}}
    $report.principal_close_confirmed=$principalConfirmed
    if(-not $principalConfirmed){$report.security='UNKNOWN';$report.success=$false}
    Request-QaClose -Folder $folder
    if($sentinel){
        $finished=$sentinel.WaitForExit(60000)
        try{$guard=Get-Content (Join-Path $folder 'sentinel-final.json') -Raw|ConvertFrom-Json}catch{$guard=$null}
        if(-not $finished -or -not $guard -or $guard.estado -cne 'CIERRE_VERIFICADO'){$report.security='UNKNOWN';$report.success=$false}
        $sentinel.Dispose()
    }else{$report.security='UNKNOWN';$report.success=$false}
    if(-not $stopped){$report.security='UNKNOWN';$report.success=$false}
    if($report.success){
        try{[IO.File]::Move((Join-Path $directory 'receipt.prepared.json'),(Join-Path $directory 'receipt.json'))}
        catch{$report.success=$false;$report.failure='QA_REPAIR_RECEIPT_NOT_FINALIZED'}
    }
    $report.worker_stopped=$stopped
    [IO.File]::WriteAllText((Join-Path $folder 'operation-summary.json'),($report|ConvertTo-Json -Depth 8))
    $report|ConvertTo-Json -Depth 8 -Compress
    if($owned){$mutex.ReleaseMutex()};$mutex.Dispose()
}
if(-not $report.success){exit 1}
