#requires -Version 7.4
[CmdletBinding()]param([switch]$Apply,[string]$VerifiedReceipt)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot '../Qa.Common.psm1') -DisableNameChecking
Import-Module (Join-Path $PSScriptRoot '../backend-tls/Qa.Custody.psm1')
$root=Get-QaRoot;$runtime=[IO.Path]::GetFullPath((Join-Path $root 'qa/runtime/'))
if($Apply){
    Assert-QaPortFree -Port 19090;Assert-QaPortFree -Port 14200
    if(-not $VerifiedReceipt){throw 'QA_FIXTURE_RECEIPT_REQUIRED'}
    Assert-QaCustodyPath ([IO.Path]::GetFullPath($VerifiedReceipt))
    $receipt=Get-Content -LiteralPath $VerifiedReceipt -Raw|ConvertFrom-Json
    if($receipt.format -cne 'dimabug-qa-fixture-receipt-v1' -or $receipt.version -cnotin @('mvp-qa-v1','mvp-qa-v2')){throw 'QA_FIXTURE_RECEIPT_IDENTITY'}
    # Future explicit execution only: reconfirm fixture, identity, both account locks and zero clients.
    & (Get-Command python.exe).Source -I -B (Join-Path $PSScriptRoot 'manage_fixtures.py') verify --execute --directory (Split-Path -Parent $VerifiedReceipt) --receipt $VerifiedReceipt
    if($LASTEXITCODE -ne 0){throw 'QA_FIXTURE_STATE_NOT_VERIFIED'}
}
$files=@()
foreach($name in @('materiales','tickets')){
    $directory=[IO.Path]::GetFullPath((Join-Path $runtime $name))
    if(-not $directory.StartsWith($runtime,[StringComparison]::OrdinalIgnoreCase)){throw 'QA_STORAGE_PATH_ESCAPE'}
    if(-not (Test-Path -LiteralPath $directory)){continue}
    if((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'QA_STORAGE_LINK_REJECTED'}
    foreach($file in @(Get-ChildItem -LiteralPath $directory -Force)){
        if($file.PSIsContainer -or $file.Attributes -band [IO.FileAttributes]::ReparsePoint -or
            $file.Name -cnotmatch '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'){throw 'QA_STORAGE_UNREVIEWED_ENTRY'}
        $files+= $file.FullName
    }
}
if($Apply){foreach($file in $files){Remove-Item -LiteralPath $file}}
@{estado=if($Apply){'QA_SYNTHETIC_STORAGE_CLEARED'}else{'QA_SYNTHETIC_STORAGE_PLAN_ONLY'};archivos=$files.Count;sql_ejecutado=$false}|ConvertTo-Json -Compress
