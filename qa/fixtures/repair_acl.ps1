#requires -Version 7.4
param([Parameter(Mandatory)][string]$Directory)
$ErrorActionPreference='Stop'
try {
    $root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    $path=[IO.Path]::GetFullPath($Directory)
    if(-not $path.StartsWith((Join-Path $root 'qa/runtime/fixture-runs')+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'QA_PATH'}
    $current=Get-Item -LiteralPath $path
    while($current -and $current.FullName -ne $root){
        if($current.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'QA_LINK'}
        $current=$current.Parent
    }
    $acl=Get-Acl -LiteralPath $path
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if(-not $acl.AreAccessRulesProtected -or $acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $sid){throw 'QA_ACL'}
    $rules=$acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])
    if(-not ($rules|Where-Object {$_.IdentityReference.Value -eq $sid -and $_.AccessControlType -eq 'Allow' -and ($_.FileSystemRights -band [Security.AccessControl.FileSystemRights]::FullControl) -eq [Security.AccessControl.FileSystemRights]::FullControl})){throw 'QA_OWNER_ACCESS'}
    foreach($rule in $rules){if($rule.IdentityReference.Value -notin @($sid,'S-1-5-18') -or $rule.AccessControlType -ne 'Allow'){throw 'QA_ACL'}}
    'PRIVATE_ACL_OK'
} catch { 'PRIVATE_ACL_NOT_VERIFIED';exit 1 }
