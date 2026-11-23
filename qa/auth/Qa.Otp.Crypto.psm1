#requires -Version 7.4
Set-StrictMode -Version Latest
function Unprotect-QaOtpEnvelope {
    param([Parameter(Mandatory)][byte[]]$Bytes,[Parameter(Mandatory)][byte[]]$Key,
          [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{32}$')][string]$RunId,
          [Parameter(Mandatory)][ValidatePattern('^[1-9][0-9]*$')][string]$RecordId,
          [DateTimeOffset]$Now=[DateTimeOffset]::UtcNow)
    $plain=$null;$fields=$null
    try{
        if($Key.Length -ne 32 -or $Bytes.Length -lt 35 -or $Bytes.Length -gt 4096 -or [Text.Encoding]::ASCII.GetString($Bytes,0,6) -cne 'DQOTP1'){throw 'QA_OTP_FORMAT'}
        [byte[]]$nonce=$Bytes[6..17];[byte[]]$cipher=$Bytes[18..($Bytes.Length-17)];[byte[]]$tag=$Bytes[($Bytes.Length-16)..($Bytes.Length-1)]
        $plain=[byte[]]::new($cipher.Length);$aad=[Text.Encoding]::UTF8.GetBytes("dimabug-qa-otp-v1|$RunId|$RecordId")
        $aes=[Security.Cryptography.AesGcm]::new($Key,16)
        try{$aes.Decrypt($nonce,$cipher,$tag,$plain,$aad)}finally{$aes.Dispose()}
        $fields=[Text.Encoding]::UTF8.GetString($plain).Split("`n")
        $allowed=@('admin','tecnico','soporte','usuario','usuario-otro','tecnico-otro','admin-respaldo','usuario-inactivo')|ForEach-Object {$_+'@qa.dimabug.test'}
        if($fields.Count -ne 4 -or $fields[0] -cne $RecordId -or $fields[1] -cnotin $allowed -or $fields[3] -cnotmatch '^[0-9]{6}$'){throw 'QA_OTP_IDENTITY'}
        $expiry=[DateTimeOffset]::FromUnixTimeMilliseconds([long]$fields[2]);if($expiry -le $Now){throw 'QA_OTP_EXPIRED'}
        # Safe default formatting: the code remains SecureString, never a plaintext pipeline field.
        $secure=[Security.SecureString]::new();foreach($character in $fields[3].ToCharArray()){$secure.AppendChar($character)};$secure.MakeReadOnly()
        return [pscustomobject]@{RecordId=$RecordId;Email=$fields[1];Expires=$expiry;Code=$secure}
    }catch{throw 'QA_OTP_DELIVERY_REJECTED'}
    finally{if($plain){[Array]::Clear($plain,0,$plain.Length)};$fields=$null}
}
Export-ModuleMember -Function Unprotect-QaOtpEnvelope
