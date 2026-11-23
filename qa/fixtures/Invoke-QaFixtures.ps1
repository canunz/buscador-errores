#requires -Version 7.4
[CmdletBinding()]param([ValidateSet('load','reset','verify')][string]$Phase='verify',[switch]$Apply,[string]$Receipt)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -DisableNameChecking
Import-Module (Join-Path $PSScriptRoot '../backend-tls/Qa.Custody.psm1')
if(-not $Apply){'{"estado":"QA_FIXTURE_PROCEDURE_PREPARED","sql_ejecutado":false}';return}
# FUTURE separate authorization only. No app startup or account unlock is performed here.
Assert-QaManifest
$root=Get-QaRoot;$python=(Get-Command python.exe).Source
if($Receipt){Assert-QaCustodyPath ([IO.Path]::GetFullPath($Receipt))}
if($Phase -ne 'load' -and -not $Receipt){throw 'QA_FIXTURE_RECEIPT_REQUIRED'}
$base=Join-Path $root 'qa/runtime/fixture-runs';$null=[IO.Directory]::CreateDirectory($base)
if((Get-Item $base).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'QA_FIXTURE_BASE_LINK'}
$directory=Join-Path $base ([Guid]::NewGuid().ToString('N'))
$acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User;$acl.SetOwner($sid)
foreach($id in @($sid,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($id,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
$null=[IO.FileSystemAclExtensions]::CreateDirectory($acl,$directory)
if($Phase -ne 'verify'){
    $arguments=@('-I','-B',(Join-Path $PSScriptRoot 'preflight_backup.py'),'--execute','--directory',$directory)
    if($Receipt){$arguments+=@('--receipt',$Receipt)};if($Phase -eq 'reset'){$arguments+='--for-reset'}
    & $python @arguments;if($LASTEXITCODE -ne 0){throw 'QA_FIXTURE_BACKUP_NOT_VERIFIED'}
    $manifest=Get-Content (Join-Path $root 'qa/candidate.json') -Raw|ConvertFrom-Json
    $bundle=Get-Content (Join-Path $root $manifest.artifacts.backend.bundle.path) -Raw|ConvertFrom-Json
    if('qa_otp' -notin $bundle.files.PSObject.Properties.Name){throw 'QA_OTP_PLUGIN_REQUIRED'}
    $java=(Get-Command java.exe).Source;$info=New-QaProcessStartInfo -Executable $java -WorkingDirectory $directory
    $helpers=(Join-Path $root $bundle.files.helper.path)+','+(Join-Path $root $bundle.files.qa_otp.path)
    foreach($a in @("-Dloader.path=$helpers",'-Dloader.main=dimabug.qa.otp.PrepareFixtureHashes',
        "-Dloader.config.location=$(([Uri][IO.Path]::GetFullPath((Join-Path $root $bundle.files.loader.path))).AbsoluteUri)",
        "-Dloader.home=$directory",'-cp',(Join-Path $root $bundle.files.backend.path),'org.springframework.boot.loader.launch.PropertiesLauncher',
        (Join-Path $directory 'hashes.json'))){$info.ArgumentList.Add($a)}
    $process=[Diagnostics.Process]::Start($info);try{if(-not $process.WaitForExit(30000)){$process.Kill();throw 'QA_HASH_TIMEOUT'};if($process.ExitCode -ne 0){throw 'QA_HASH_FAILED'}}finally{$process.Dispose()}
    Assert-QaCustodyPath (Join-Path $directory 'hashes.json')
}
$arguments=@('-I','-B',(Join-Path $PSScriptRoot 'manage_fixtures.py'),$Phase,'--execute','--directory',$directory)
if($Phase -ne 'verify'){$arguments+=@('--hashes',(Join-Path $directory 'hashes.json'))}
if($Receipt){$arguments+=@('--receipt',$Receipt)}
& $python @arguments;if($LASTEXITCODE -ne 0){throw 'QA_FIXTURE_OPERATION_NOT_VERIFIED_NO_RETRY'}
@{estado='QA_FIXTURE_OPERATION_VERIFIED';fase=$Phase;directorio_privado=$directory}|ConvertTo-Json -Compress
