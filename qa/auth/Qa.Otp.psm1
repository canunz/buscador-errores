#requires -Version 7.4
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '../backend-tls/Qa.Custody.psm1')
function Read-QaOtpKey {return Read-QaOtpSecret}
function New-QaOtpMailbox {
    Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -DisableNameChecking
    $base=Join-Path (Get-QaRoot) 'qa/runtime/otp-runs';$null=[IO.Directory]::CreateDirectory($base)
    if((Get-Item $base).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'QA_OTP_LINK_REJECTED'}
    $run=[Guid]::NewGuid().ToString('N');$path=Join-Path $base $run
    $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User;$acl.SetOwner($sid)
    foreach($id in @($sid,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($id,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
    $null=[IO.FileSystemAclExtensions]::CreateDirectory($acl,$path)
    [IO.File]::WriteAllText((Join-Path $path 'run.json'),(@{format='dimabug-qa-otp-mailbox-v1';run_id=$run;database='dima_bug_qa_mvp';port=13318}|ConvertTo-Json -Compress))
    return $path
}
function Assert-QaOtpMailbox([string]$Path){
    Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -DisableNameChecking
    $full=[IO.Path]::GetFullPath($Path);$base=[IO.Path]::GetFullPath((Join-Path (Get-QaRoot) 'qa/runtime/otp-runs/'))
    if(-not $full.StartsWith($base,[StringComparison]::OrdinalIgnoreCase)){throw 'QA_OTP_PATH_REJECTED'}
    Assert-QaCustodyPath (Join-Path $full 'run.json')
    $meta=Get-Content (Join-Path $full 'run.json') -Raw|ConvertFrom-Json
    if($meta.format -cne 'dimabug-qa-otp-mailbox-v1' -or $meta.database -cne 'dima_bug_qa_mvp' -or $meta.port -ne 13318 -or $meta.run_id -cne (Split-Path -Leaf $full) -or $meta.run_id -cnotmatch '^[a-f0-9]{32}$'){throw 'QA_OTP_MAILBOX_IDENTITY'}
    return $meta
}
function Clear-QaOtpMailbox([string]$Path){
    $null=Assert-QaOtpMailbox $Path
    # Only bounded delivery artifacts in the verified directory, never recursive deletion.
    foreach($file in @(Get-ChildItem -LiteralPath $Path -File)){
        if($file.Name -match '^[0-9]+\.(otp|failed)$' -or $file.Name -match '^pending-[a-f0-9-]+\.tmp$'){
            if($file.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'QA_OTP_LINK_REJECTED'}
            Remove-Item -LiteralPath $file.FullName
        }
    }
}
Export-ModuleMember -Function Read-QaOtpKey,New-QaOtpMailbox,Assert-QaOtpMailbox,Clear-QaOtpMailbox
