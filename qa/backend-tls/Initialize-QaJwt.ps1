#requires -Version 7.4
[CmdletBinding()]param([switch]$Create)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'Qa.Custody.psm1') -Force
if(-not $Create){'{"estado":"QA_JWT_NO_GENERADO","requiere_create":true}';return}
$random=$null;$plain=$null;$verification=$null;$secret=$null;$document=$null
try {
    # Existing custody directory only; no modification of DB vault or its portable backup.
    $directory=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'DimaBug-QA-MySQL-13318'
    Assert-QaCustodyPath (Join-Path $directory 'credentials.dpapi')
    $file=Join-Path $directory 'jwt.dpapi'
    if(Test-Path -LiteralPath $file){throw 'QA_JWT_ALREADY_EXISTS_NO_OVERWRITE'}
    Add-Type -AssemblyName System.Security.Cryptography.ProtectedData
    $random=[byte[]]::new(64);[Security.Cryptography.RandomNumberGenerator]::Fill($random)
    $secret=[Convert]::ToBase64String($random)
    $document=@{format='dimabug-qa-jwt-v1';database='dima_bug_qa_mvp';port=13318;secret=$secret}
    $plain=[Text.Encoding]::UTF8.GetBytes(($document|ConvertTo-Json -Compress))
    $entropy=[Text.Encoding]::UTF8.GetBytes('dimabug-qa-jwt-v1')
    $cipher=[Security.Cryptography.ProtectedData]::Protect($plain,$entropy,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $stream=[IO.File]::Open($file,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
    try{$stream.Write($cipher,0,$cipher.Length)}finally{$stream.Dispose()}
    Assert-QaCustodyPath $file
    $verification=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($file),$entropy,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    if(-not [Security.Cryptography.CryptographicOperations]::FixedTimeEquals($plain,$verification)){throw 'QA_JWT_VERIFY_FAILED'}
    $read=Read-QaJwtSecret;try{if($read.Length -ne 88){throw 'QA_JWT_INVALID'}}finally{$read.Dispose()}
    '{"estado":"QA_JWT_DPAPI_VERIFICADO","aleatoriedad_bits":512,"secretos_mostrados":false,"sql_ejecutado":false,"cuentas_desbloqueadas":false}'
}catch{
    $code=if($_.Exception.Message -match '^QA_[A-Z_]+$'){$_.Exception.Message}else{'QA_JWT_SANITIZED_FAILURE'}
    @{estado='QA_JWT_NO_COMPLETADO';codigo=$code;reintento_automatico=$false}|ConvertTo-Json -Compress
    exit 1
}finally{
    foreach($array in @($random,$plain,$verification)){if($array -is [byte[]]){[Array]::Clear($array,0,$array.Length)}}
    $secret=$null;$document=$null
}
