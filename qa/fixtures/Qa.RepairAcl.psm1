#requires -Version 7.4
Set-StrictMode -Version Latest

function Assert-QaRepairAcl {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Directory,[scriptblock]$Checker)
    # A PowerShell script does not necessarily assign LASTEXITCODE on success.
    # Accept only the checker's explicit, single success token; fail closed.
    $ErrorActionPreference='Stop'
    if(-not $Checker){
        $Checker={param($Path) & (Join-Path $PSScriptRoot 'repair_acl.ps1') -Directory $Path}
    }
    try{$result=@(& $Checker $Directory)}
    catch{throw 'QA_REPAIR_ACL_CHECKER_ERROR'}
    if($result.Count -eq 0 -or ($result.Count -eq 1 -and ($null -eq $result[0] -or ($result[0] -is [string] -and [string]::IsNullOrWhiteSpace($result[0]))))){throw 'QA_REPAIR_ACL_RESULT_MISSING'}
    if($result.Count -eq 1 -and $result[0] -is [string]){
        if($result[0] -ceq 'PRIVATE_ACL_OK'){return}
        if($result[0] -ceq 'PRIVATE_ACL_NOT_VERIFIED'){throw 'QA_REPAIR_ACL_UNSAFE'}
    }
    throw 'QA_REPAIR_ACL_RESULT_INVALID'
}

Export-ModuleMember -Function Assert-QaRepairAcl
