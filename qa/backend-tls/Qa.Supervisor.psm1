#requires -Version 7.4
Set-StrictMode -Version Latest
function New-QaBackendRunDirectory {
    Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -DisableNameChecking
    $base=Join-Path (Get-QaRoot) 'qa/runtime/backend-runs'
    $null=[IO.Directory]::CreateDirectory($base)
    if((Get-Item -LiteralPath $base).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'QA_RUN_BASE_LINK_REJECTED'}
    $path=Join-Path $base ([Guid]::NewGuid().ToString('N'))
    $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User;$acl.SetOwner($sid)
    foreach($identity in @($sid,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    $null=[IO.FileSystemAclExtensions]::CreateDirectory($acl,$path)
    return $path
}
function New-QaSupervisorStartInfo {
    param([Parameter(Mandatory)][string]$RunDirectory,[Parameter(Mandatory)][int]$ParentId,[switch]$EnableQaOtp,[string]$OtpMailbox)
    Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -DisableNameChecking
    $pwsh=(Get-Command pwsh.exe).Source
    $info=New-QaProcessStartInfo -Executable $pwsh -WorkingDirectory (Get-QaRoot)
    # Explicit tool paths, never the caller's complete PATH or staging environment.
    $dirs=@(Split-Path $pwsh)
    foreach($tool in @('java.exe','python.exe','docker.exe','git.exe')){$dirs+=Split-Path (Get-Command $tool -ErrorAction Stop).Source}
    $info.Environment['PATH']=(@($dirs|Select-Object -Unique)+@((Join-Path $env:SystemRoot 'System32'),$env:SystemRoot)) -join ';'
    foreach($arg in @('-NoProfile','-NonInteractive','-File',(Join-Path $PSScriptRoot 'Invoke-QaBackendSupervisor.ps1'),
        '-RunAuthorizedWindow','-ParentId',[string]$ParentId,'-RunDirectory',$RunDirectory)){$info.ArgumentList.Add($arg)}
    if($EnableQaOtp){$info.ArgumentList.Add('-EnableQaOtp');$info.ArgumentList.Add('-OtpMailbox');$info.ArgumentList.Add($OtpMailbox)}
    $info.CreateNoWindow=$true
    return $info # Does not start the supervisor. Its execution would eventually execute close SQL.
}
Export-ModuleMember -Function New-QaBackendRunDirectory,New-QaSupervisorStartInfo
