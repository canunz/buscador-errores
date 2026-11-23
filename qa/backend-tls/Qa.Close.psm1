#requires -Version 7.4
Set-StrictMode -Version Latest

function Invoke-QaCloseAttempt {
    param([Parameter(Mandatory)][string]$Python,[Parameter(Mandatory)][string]$Root,
          [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9_-]{1,80}$')][string]$RunId,
          [scriptblock]$Transport)
    $watch=[Diagnostics.Stopwatch]::StartNew()
    $result=@{actor='principal';run_id=$RunId;utc=[DateTime]::UtcNow.ToString('o');phase='PREPARE_PROCESS';
        process_started=$false;exit_code=$null;timed_out=$false;json_valid=$false;lock_attempted=$null;
        lock_acknowledged=$null;account_locked_verified=$null;app_sessions_zero_verified=$null;
        confirmed=$false;database_state='DESCONOCIDO';failure_code=$null;adapter_failure_code=$null}
    try {
        if($Transport){$response=& $Transport}
        else {
            $null=Get-Command New-QaProcessStartInfo -ErrorAction Stop
            $info=New-QaProcessStartInfo -Executable $Python -WorkingDirectory $Root
            $info.Environment['PATH']="$(Split-Path (Get-Command docker.exe -ErrorAction Stop).Source);$($info.Environment['PATH'])"
            $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
            foreach($arg in @('-I','-B',(Join-Path $PSScriptRoot 'close_qa_account.py'),'--execute-close','--actor','principal','--run-id',$RunId)){$info.ArgumentList.Add($arg)}
            $process=[Diagnostics.Process]::Start($info);$result.process_started=$true;$result.phase='WAIT_PROCESS'
            $out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync()
            try {
                if(-not $process.WaitForExit(90000)){
                    $result.timed_out=$true;$result.failure_code='PROCESS_TIMEOUT'
                    if(-not $process.HasExited){$process.Kill()};$null=$process.WaitForExit(5000)
                    return $result
                }
                $response=@{started=$true;exit_code=$process.ExitCode;timed_out=$false;stdout=$out.GetAwaiter().GetResult()}
                $null=$err.GetAwaiter().GetResult() # Deliberately not persisted or logged.
            }finally{$process.Dispose()}
        }
        $result.process_started=[bool]$response.started;$result.exit_code=$response.exit_code;$result.timed_out=[bool]$response.timed_out
        $result.phase='PARSE_RESPONSE'
        if($result.timed_out){$result.failure_code='PROCESS_TIMEOUT';return $result}
        try{$document=$response.stdout|ConvertFrom-Json -ErrorAction Stop}catch{$result.failure_code='INVALID_JSON';return $result}
        if($document -isnot [pscustomobject]){$result.failure_code='INVALID_CONTRACT';return $result}
        $required=@('estado','closed','actor','run_id','phase','lock_attempted','lock_acknowledged','account_locked_verified','app_sessions_zero_verified')
        $names=@($document.PSObject.Properties|ForEach-Object Name)
        foreach($field in $required){if($field -notin $names){$result.failure_code='INVALID_CONTRACT';return $result}}
        if($document.run_id -cne $RunId -or $document.actor -cne 'principal'){$result.failure_code='CORRELATION_MISMATCH';return $result}
        foreach($field in @('closed','lock_attempted','lock_acknowledged','account_locked_verified','app_sessions_zero_verified')){
            if($null -ne $document.$field -and $document.$field -isnot [bool]){$result.failure_code='INVALID_CONTRACT';return $result}
        }
        $result.json_valid=$true
        if('failure_code' -in $names -and ($document.failure_code -match '^SQL_ERROR_[0-9]+$' -or $document.failure_code -in @('CONTROL_OR_TRANSPORT_ERROR','DEPENDENCY_OR_CONTROL_ERROR'))){
            $result.adapter_failure_code=$document.failure_code
        }
        foreach($field in @('lock_attempted','lock_acknowledged','account_locked_verified','app_sessions_zero_verified')){$result[$field]=$document.$field}
        # No arbitrary provider text is copied from the adapter.
        if($document.phase -in @('PREPARE_ADAPTER','LOCK','SESSIONS','VERIFY_SESSIONS','VERIFY_ACCOUNTS','COMPLETE')){$result.phase=$document.phase}
        $result.confirmed=$result.process_started -and $result.exit_code -eq 0 -and $document.estado -ceq 'QA_ACCOUNT_CLOSE_VERIFIED' -and
            $document.phase -ceq 'COMPLETE' -and $document.lock_attempted -eq $true -and $document.closed -eq $true -and
            $document.lock_acknowledged -eq $true -and $document.account_locked_verified -eq $true -and $document.app_sessions_zero_verified -eq $true
        if($result.confirmed){$result.database_state='CONFIRMADO';$result.phase='COMPLETE'}else{$result.failure_code='ADAPTER_NOT_CONFIRMED'}
    }catch{
        $result.failure_code=if($_.Exception -is [Management.Automation.CommandNotFoundException]){'COMMAND_NOT_FOUND'}else{'PROCESS_OR_PROTOCOL_ERROR'}
        if(-not $result.process_started){$result.lock_attempted=$false}
    }finally{$result.elapsed_ms=$watch.ElapsedMilliseconds}
    return $result
}

function Request-QaClose {
    param([Parameter(Mandatory)][string]$Folder)
    $file=Join-Path $Folder 'close.request'
    try{$stream=[IO.File]::Open($file,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write);$stream.Dispose()}
    catch [IO.IOException]{if(-not (Test-Path -LiteralPath $file -PathType Leaf)){throw}}
}
Export-ModuleMember -Function Invoke-QaCloseAttempt,Request-QaClose
