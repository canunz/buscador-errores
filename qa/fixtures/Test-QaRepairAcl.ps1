#requires -Version 7.4
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'Qa.RepairAcl.psm1')
$passed=0
function Accept([scriptblock]$Checker){
    Assert-QaRepairAcl -Directory 'simulated-only' -Checker $Checker
    $script:passed++
}
function Reject([scriptblock]$Checker,[string]$Expected){
    $errorCode=$null
    try{Assert-QaRepairAcl -Directory 'simulated-only' -Checker $Checker}catch{$errorCode=$_.Exception.Message}
    if($errorCode -cne $Expected){throw "QA_ACL_REGRESSION_REJECTION_FAILED_CHECK_$($script:passed+1)"}
    $script:passed++
}
$original=$global:LASTEXITCODE
try{
    $global:LASTEXITCODE=0
    Accept {'PRIVATE_ACL_OK'}
    $global:LASTEXITCODE=$null
    Accept {'PRIVATE_ACL_OK'}
    $global:LASTEXITCODE=73
    Accept {'PRIVATE_ACL_OK'} # stale native status must not control script success
    Reject {'unexpected'} 'QA_REPAIR_ACL_RESULT_INVALID'
    Reject {} 'QA_REPAIR_ACL_RESULT_MISSING'
    Reject {$null} 'QA_REPAIR_ACL_RESULT_MISSING'
    Reject {'   '} 'QA_REPAIR_ACL_RESULT_MISSING'
    Reject {throw 'PRIVATE_PROVIDER_MESSAGE'} 'QA_REPAIR_ACL_CHECKER_ERROR'
    Reject {Write-Error 'PRIVATE_PROVIDER_MESSAGE'} 'QA_REPAIR_ACL_CHECKER_ERROR'
    Reject {'PRIVATE_ACL_NOT_VERIFIED'} 'QA_REPAIR_ACL_UNSAFE'
    Reject {'PRIVATE_ACL_OK';'PRIVATE_ACL_NOT_VERIFIED'} 'QA_REPAIR_ACL_RESULT_INVALID'
    Reject {'PRIVATE_ACL_OK';'PRIVATE_ACL_OK'} 'QA_REPAIR_ACL_RESULT_INVALID'
    Reject {'private_acl_ok'} 'QA_REPAIR_ACL_RESULT_INVALID'
    Reject {@{status='PRIVATE_ACL_OK'}} 'QA_REPAIR_ACL_RESULT_INVALID'
    # Actual checker rejection with simulated insecure ACL metadata, no ACL writes.
    $checkerScript=Join-Path $PSScriptRoot 'repair_acl.ps1'
    $simulatedDirectory=Join-Path $PSScriptRoot '../runtime/fixture-runs/acl-simulation'
    $state=[pscustomobject]@{aclRead=$false}
    $insecure={
        param($Path)
        function Get-Item { [pscustomobject]@{FullName=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));Attributes=0} }
        function Get-Acl { $state.aclRead=$true;[pscustomobject]@{AreAccessRulesProtected=$false} }
        & $checkerScript -Directory $Path
    }.GetNewClosure()
    $errorCode=$null
    try{Assert-QaRepairAcl -Directory $simulatedDirectory -Checker $insecure}catch{$errorCode=$_.Exception.Message}
    if($errorCode -cne 'QA_REPAIR_ACL_UNSAFE' -or -not $state.aclRead){throw 'QA_ACL_UNSAFE_PERMISSIONS_NOT_REJECTED'}
    $passed++
    [ordered]@{status='QA_REPAIR_ACL_REGRESSIONS_OK';checks=$passed;sql_executed=$false;acl_modified=$false}|ConvertTo-Json -Compress
}finally{$global:LASTEXITCODE=$original}
