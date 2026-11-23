#requires -Version 7.4
$ErrorActionPreference='Stop';$checks=0
Import-Module (Join-Path $PSScriptRoot 'Qa.Otp.Crypto.psm1') -Force
function Check([bool]$ok){if(-not $ok){throw "QA_OTP_CRYPTO_ASSERT_$script:checks"};$script:checks++}
$key=[byte[]]::new(32);[Security.Cryptography.RandomNumberGenerator]::Fill($key)
$nonce=[byte[]]::new(12);[Security.Cryptography.RandomNumberGenerator]::Fill($nonce)
$run='b'*32;$code=([Security.Cryptography.RandomNumberGenerator]::GetInt32(1000000)).ToString('D6')
$expires=[DateTimeOffset]::UtcNow.AddMinutes(5)
$plain=[Text.Encoding]::UTF8.GetBytes("1`nusuario@qa.dimabug.test`n$($expires.ToUnixTimeMilliseconds())`n$code")
$cipher=[byte[]]::new($plain.Length);$tag=[byte[]]::new(16);$aad=[Text.Encoding]::UTF8.GetBytes("dimabug-qa-otp-v1|$run|1")
$aes=[Security.Cryptography.AesGcm]::new($key,16);try{$aes.Encrypt($nonce,$plain,$cipher,$tag,$aad)}finally{$aes.Dispose()}
[byte[]]$envelope=[Text.Encoding]::ASCII.GetBytes('DQOTP1')+$nonce+$cipher+$tag
$delivery=Unprotect-QaOtpEnvelope $envelope $key $run '1'
try{Check ($delivery.Code -is [Security.SecureString]);Check ($delivery.Code.Length -eq 6);Check (($delivery|ConvertTo-Json) -notmatch $code)}finally{$delivery.Code.Dispose()}
function Reject([scriptblock]$action){try{$null=&$action}catch{Check ($_.Exception.Message -ceq 'QA_OTP_DELIVERY_REJECTED');return};throw 'QA_OTP_EXPECTED_REJECTION'}
Reject {Unprotect-QaOtpEnvelope $envelope $key ('c'*32) '1'}
Reject {Unprotect-QaOtpEnvelope $envelope $key $run '2'}
Reject {Unprotect-QaOtpEnvelope $envelope $key $run '1' -Now $expires.AddSeconds(1)}
$envelope[-1]=$envelope[-1] -bxor 1;Reject {Unprotect-QaOtpEnvelope $envelope $key $run '1'}
foreach($a in @($key,$plain)){[Array]::Clear($a,0,$a.Length)};$code=$null
@{estado='QA_OTP_CRYPTO_UNIT_OK';checks=$checks;dpapi_leida=$false;gui_iniciada=$false;sql_ejecutado=$false}|ConvertTo-Json -Compress
