#requires -Version 7.4
[CmdletBinding()]param([switch]$RunAuthorizedWindow,[int]$ParentId,[string]$RunDirectory,[switch]$EnableQaOtp,[string]$OtpMailbox)
$ErrorActionPreference='Stop'
if(-not $RunAuthorizedWindow){'{"estado":"QA_SUPERVISOR_PREPARADO_NO_EJECUTADO","sql_ejecutado":false}';return}
# FUTURE AUTHORIZATION ONLY. Parent performs any separately authorized unlock; this supervisor NEVER unlocks.
Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -Force -DisableNameChecking
Import-Module (Join-Path $PSScriptRoot 'Qa.Jdbc.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Qa.Close.psm1')
$root=Get-QaRoot
$folder=[IO.Path]::GetFullPath($RunDirectory)
$base=[IO.Path]::GetFullPath((Join-Path $root 'qa/runtime/backend-runs/'))
if(-not $folder.StartsWith($base,[StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $folder -PathType Container) -or
    (Get-Item -LiteralPath $folder).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'QA_SUPERVISOR_FOLDER_REJECTED'}
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$acl=Get-Acl -LiteralPath $folder
if(-not $acl.AreAccessRulesProtected -or $acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $sid){throw 'QA_SUPERVISOR_ACL_REJECTED'}
foreach($rule in $acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])){
    if($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Value -notin @($sid,'S-1-5-18')){throw 'QA_SUPERVISOR_ACL_REJECTED'}
}
if(@(Get-ChildItem -LiteralPath $folder -Force).Count -ne 0){throw 'QA_SUPERVISOR_FRESH_DIRECTORY_REQUIRED'}
if($ParentId -le 0 -or $ParentId -eq $PID){throw 'QA_SUPERVISOR_PARENT_REJECTED'}
$parent=[Diagnostics.Process]::GetProcessById($ParentId);$null=$parent.Handle
$python=(Get-Command python.exe).Source
Add-Type -Path (Join-Path $PSScriptRoot 'QaBackendJob.cs')
$job=[QaBackendJob]::new();$sentinel=$null;$info=$null;$watch=[Diagnostics.Stopwatch]::StartNew();$closed=$false;$reason='ERROR'
function Write-QaState([string]$Name,$Data){
    $target=Join-Path $folder $Name;$temporary=$target+'.tmp'
    [IO.File]::WriteAllText($temporary,($Data|ConvertTo-Json -Compress))
    [IO.File]::Move($temporary,$target,$true)
}

try{
    Assert-QaManifest
    Assert-QaPortFree -Port 19090
    foreach($command in @('New-QaProcessStartInfo','Invoke-QaCloseAttempt','Request-QaClose')){$null=Get-Command $command -ErrorAction Stop}
    if($EnableQaOtp){
        Import-Module (Join-Path $PSScriptRoot '../auth/Qa.Otp.psm1')
        $null=Assert-QaOtpMailbox $OtpMailbox
        $otpCheck=Read-QaOtpKey;try{if($otpCheck.Length -ne 44){throw 'QA_OTP_KEY_INVALID'}}finally{$otpCheck.Dispose()}
    }
    $sentinelInfo=New-QaProcessStartInfo -Executable $python -WorkingDirectory $root
    $sentinelInfo.Environment['PATH']="$(Split-Path (Get-Command docker.exe).Source);$($sentinelInfo.Environment['PATH'])"
    foreach($arg in @('-I','-B',(Join-Path $PSScriptRoot 'cleanup_sentinel.py'),'--run-authorized-window','--dir',$folder,'--parent',[string]$PID)){$sentinelInfo.ArgumentList.Add($arg)}
    $sentinel=[Diagnostics.Process]::Start($sentinelInfo)
    while(-not (Test-Path -LiteralPath (Join-Path $folder 'sentinel-ready.json'))){
        if($sentinel.HasExited -or $watch.Elapsed.TotalSeconds -gt 10){throw 'QA_SENTINEL_NOT_READY'};Start-Sleep -Milliseconds 100
    }
    # Ready is BEFORE any parent unlock/start signal. No SQL is run by readiness itself.
    Write-QaState 'supervisor-ready.json' @{estado='LISTO';deadline_seconds=180;unlocks_accounts=$false}
    while(-not (Test-Path -LiteralPath (Join-Path $folder 'begin.request'))){
        if($parent.HasExited -or $sentinel.HasExited -or $watch.Elapsed.TotalSeconds -ge 180 -or (Test-Path (Join-Path $folder 'close.request'))){throw 'QA_PRESTART_INTERRUPTED'}
        Start-Sleep -Milliseconds 100
    }
    $manifest=Get-Content -LiteralPath (Join-Path $root 'qa/candidate.json') -Raw|ConvertFrom-Json
    $info=New-QaBackendStartInfo -BundleFile (Join-Path $root $manifest.artifacts.backend.bundle.path) -EnableQaOtp:$EnableQaOtp -OtpMailbox $OtpMailbox
    $job.Start($info) # Created suspended, assigned to kill-on-close Job, then resumed.
    Clear-QaBackendStartInfo $info;$info=$null
    Write-QaState 'backend-owned.json' @{estado='PROCESO_SUPERVISADO';process_id=$job.ProcessId}
    $startup=[Diagnostics.Stopwatch]::StartNew();$healthy=$false
    while($true){
        if($job.HasExited){$reason='CHILD_EXIT';break}
        if($parent.HasExited){$reason='PARENT_EXIT';break}
        if($sentinel.HasExited){$reason='SENTINEL_EXIT';break}
        if($watch.Elapsed.TotalSeconds -ge 180){$reason='DEADLINE';break}
        if(Test-Path -LiteralPath (Join-Path $folder 'close.request')){$reason='REQUESTED';break}
        if(-not $healthy){
            try{$health=Invoke-RestMethod 'http://127.0.0.1:19090/api/health' -TimeoutSec 2 -NoProxy
                $healthy=$health.status -eq 'OK' -and $health.application -eq 'DimaBug API'
            }catch{}
            if($healthy){Write-QaState 'health.json' @{estado='HTTP_LIVENESS_OK';database_health_proven=$false}}
            elseif($startup.Elapsed.TotalSeconds -ge 120){$reason='STARTUP_TIMEOUT';break}
        }
        Start-Sleep -Milliseconds 200
    }
}catch{$reason='SUPERVISOR_ERROR'}
finally{
    if($info){Clear-QaBackendStartInfo $info}
    $stopped=$job.Stop();$job.Dispose()
    $otpCleaned=$true
    if($EnableQaOtp){try{Clear-QaOtpMailbox $OtpMailbox}catch{$otpCleaned=$false}}
    $requestRecorded=$true
    try{Request-QaClose -Folder $folder}catch{$requestRecorded=$false}
    # Both guardians attempt idempotent close. No success claim if Docker/MySQL is unavailable.
    $closeAttempt=Invoke-QaCloseAttempt -Python $python -Root $root -RunId (Split-Path -Leaf $folder)
    $closed=$closeAttempt.confirmed
    $sentinelClosed=$false
    if($sentinel){
        $null=$sentinel.WaitForExit(90000);$sentinel.Dispose()
        try{
            $sentinelReport=Get-Content -LiteralPath (Join-Path $folder 'sentinel-final.json') -Raw|ConvertFrom-Json
            $sentinelClosed=$sentinelReport.estado -ceq 'CIERRE_VERIFICADO' -and $sentinelReport.actor -ceq 'sentinel' -and $sentinelReport.run_id -ceq (Split-Path -Leaf $folder)
        }catch{}
    }
    Write-QaState 'supervisor-final.json' @{estado=if($closed -and $stopped -and $sentinelClosed -and $otpCleaned){'CIERRE_VERIFICADO'}else{'CIERRE_NO_VERIFICADO'};
        trigger=$reason;java_stopped=$stopped;mysql_closed_verified=$closed;sentinel_closed_verified=$sentinelClosed;
        actor='principal';run_id=(Split-Path -Leaf $folder);utc=[DateTime]::UtcNow.ToString('o');primary_close=$closeAttempt;
        close_request_recorded=$requestRecorded;database_state=if($closed -or $sentinelClosed){'CONFIRMADO'}else{'DESCONOCIDO'};
        otp_cleanup_verified=$otpCleaned;manual_recovery_required=(-not ($closed -and $stopped -and $sentinelClosed -and $otpCleaned))}
    $parent.Dispose()
}
if(-not ($closed -and $stopped -and $sentinelClosed -and $otpCleaned)){exit 1}
