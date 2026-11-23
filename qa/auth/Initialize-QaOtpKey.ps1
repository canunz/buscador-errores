#requires -Version 7.4
[CmdletBinding()]param([switch]$Create)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot '../backend-tls/Qa.Custody.psm1')
if(-not $Create){'{"estado":"QA_OTP_KEY_NOT_GENERATED"}';return}
$random=$null;$plain=$null;$verified=$null;$document=$null
try{
    $directory=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'DimaBug-QA-MySQL-13318'
    Assert-QaCustodyPath (Join-Path $directory 'credentials.dpapi')
    $file=Join-Path $directory 'otp.dpapi';if(Test-Path -LiteralPath $file){throw 'QA_OTP_KEY_EXISTS_NO_OVERWRITE'}
    Add-Type -AssemblyName System.Security.Cryptography.ProtectedData
    $random=[byte[]]::new(32);[Security.Cryptography.RandomNumberGenerator]::Fill($random)
    $document=@{format='dimabug-qa-otp-key-v1';database='dima_bug_qa_mvp';port=13318;secret=[Convert]::ToBase64String($random)}
    $plain=[Text.Encoding]::UTF8.GetBytes('{"format":"dimabug-qa-otp-key-v1","database":"dima_bug_qa_mvp","port":13318,"secret":"'+$document.secret+'"}');$entropy=[Text.Encoding]::UTF8.GetBytes('dimabug-qa-otp-key-v1')
    $cipher=[Security.Cryptography.ProtectedData]::Protect($plain,$entropy,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $stream=[IO.File]::Open($file,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
    try{$stream.Write($cipher,0,$cipher.Length)}finally{$stream.Dispose()}
    Assert-QaCustodyPath $file
    $verified=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($file),$entropy,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    if(-not [Security.Cryptography.CryptographicOperations]::FixedTimeEquals($plain,$verified)){throw 'QA_OTP_KEY_VERIFY_FAILED'}
    '{"estado":"QA_OTP_KEY_DPAPI_VERIFIED","key_bits":256,"secrets_shown":false,"sql_executed":false}'
}catch{'{"estado":"QA_OTP_KEY_FAILED_NO_AUTOMATIC_RETRY","secrets_shown":false}';exit 1}
finally{foreach($array in @($random,$plain,$verified)){if($array -is [byte[]]){[Array]::Clear($array,0,$array.Length)}};$document=$null}
