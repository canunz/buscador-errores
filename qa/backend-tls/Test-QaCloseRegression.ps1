#requires -Version 7.4
$ErrorActionPreference='Stop'
$passed=0
function Check([bool]$Value){if(-not $Value){throw "QA_REGRESSION_FAILED_CHECK_$($script:passed+1)"};$script:passed++}
$common=(Resolve-Path (Join-Path $PSScriptRoot '../Qa.Common.psm1')).Path
Import-Module $common -Force -DisableNameChecking
Import-Module (Join-Path $PSScriptRoot 'Qa.Jdbc.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Qa.Close.psm1') -Force
# Verify actual import commands remain non-forcing; emulate their module scope only.
foreach($moduleFile in @('Qa.Jdbc.psm1','Qa.Supervisor.psm1')){
    $tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $moduleFile),[ref]$tokens,[ref]$errors)
    $imports=@($ast.FindAll({param($n)$n -is [Management.Automation.Language.CommandAst] -and $n.GetCommandName() -eq 'Import-Module'},$true))
    Check ($imports.Count -gt 0)
    foreach($import in $imports){Check (-not ($import.CommandElements|Where-Object {$_ -is [Management.Automation.Language.CommandParameterAst] -and $_.ParameterName -eq 'Force'}))}
}
& (Get-Module Qa.Jdbc) {param($path) Import-Module $path -DisableNameChecking;$null=Get-Command New-QaProcessStartInfo -ErrorAction Stop} $common
Check ([bool](Get-Command New-QaProcessStartInfo -ErrorAction SilentlyContinue))
function Fixture([hashtable]$Changes=@{},[int]$Exit=0,[bool]$Timeout=$false,[string]$Raw=''){
    $payload=@{estado='QA_ACCOUNT_CLOSE_VERIFIED';closed=$true;actor='principal';run_id='regression';phase='COMPLETE';
        lock_attempted=$true;lock_acknowledged=$true;account_locked_verified=$true;app_sessions_zero_verified=$true}
    foreach($key in $Changes.Keys){$payload[$key]=$Changes[$key]}
    $json=if($Raw){$Raw}else{$payload|ConvertTo-Json -Compress}
    return { @{started=$true;exit_code=$Exit;timed_out=$Timeout;stdout=$json} }.GetNewClosure()
}
function Attempt([scriptblock]$Transport){Invoke-QaCloseAttempt -Python 'never-executed.exe' -Root $PSScriptRoot -RunId regression -Transport $Transport}
$r=Attempt (Fixture);Check ($r.confirmed -and $r.database_state -eq 'CONFIRMADO')
$r=Attempt (Fixture @{closed=$false});Check (-not $r.confirmed)
$r=Attempt (Fixture @{phase='LOCK'});Check (-not $r.confirmed)
$r=Attempt (Fixture @{actor='sentinel'});Check ($r.failure_code -eq 'CORRELATION_MISMATCH')
$r=Attempt (Fixture @{} 1);Check (-not $r.confirmed -and $r.exit_code -eq 1)
$r=Attempt (Fixture @{} 0 $true);Check (-not $r.confirmed -and $r.timed_out -and $null -eq $r.lock_acknowledged)
$r=Attempt (Fixture @{} 0 $false 'synthetic-secret-canary');Check ($r.failure_code -eq 'INVALID_JSON' -and ($r|ConvertTo-Json) -notmatch 'synthetic-secret-canary')
$r=Attempt (Fixture @{run_id='another-run'});Check ($r.failure_code -eq 'CORRELATION_MISMATCH' -and -not $r.confirmed)
$r=Attempt (Fixture @{lock_acknowledged='true'});Check ($r.failure_code -eq 'INVALID_CONTRACT')
$r=Attempt (Fixture @{lock_acknowledged=$true;account_locked_verified=$null;app_sessions_zero_verified=$null;closed=$false});Check ($r.lock_acknowledged -and $null -eq $r.account_locked_verified -and $r.database_state -eq 'DESCONOCIDO')
$r=Attempt {throw [Management.Automation.CommandNotFoundException]::new('synthetic-secret-canary')}
Check ($r.failure_code -eq 'COMMAND_NOT_FOUND' -and -not $r.process_started -and $r.lock_attempted -eq $false)
Check (($r|ConvertTo-Json) -notmatch 'synthetic-secret-canary')
$r=Attempt (Fixture @{} 0 $false '{}');Check ($r.failure_code -eq 'INVALID_CONTRACT')
$tmp=Join-Path (Split-Path $PSScriptRoot) ('runtime/tmp/close-regression-'+[Guid]::NewGuid().ToString('N'))
$null=[IO.Directory]::CreateDirectory($tmp)
Request-QaClose -Folder $tmp
$marker=Join-Path $tmp 'close.request';[IO.File]::WriteAllText($marker,'public-test-marker')
$before=(Get-Item $marker).LastWriteTimeUtc;Request-QaClose -Folder $tmp
Check ((Get-Item $marker).LastWriteTimeUtc -eq $before -and [IO.File]::ReadAllText($marker) -ceq 'public-test-marker')
$info=New-QaProcessStartInfo -Executable 'never-executed.exe' -WorkingDirectory $PSScriptRoot -OsEnvironment @{GOOGLE_CLIENT_ID='synthetic-secret-canary';SPRING_APPLICATION_JSON='synthetic-secret-canary';MYSQL_PWD='synthetic-secret-canary'}
foreach($key in @('GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET','GMAIL_TOKEN_ENCRYPTION_KEY','SPRING_APPLICATION_JSON','MYSQL_PWD','JAVA_TOOL_OPTIONS')){Check (-not $info.Environment.ContainsKey($key))}
@{estado='QA_CLOSE_POWERSHELL_REGRESSIONS_OK';checks=$passed;sql_ejecutado=$false;procesos_iniciados=$false;custodia_leida=$false}|ConvertTo-Json -Compress
