#requires -Version 7.4
Set-StrictMode -Version Latest

function Assert-QaCustodyPath {
    param([Parameter(Mandatory)][string]$File)
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    foreach ($path in @((Split-Path -Parent $File),$File)) {
        if (-not (Test-Path -LiteralPath $path)) { throw 'QA_CUSTODY_MISSING' }
        $acl=Get-Acl -LiteralPath $path
        if ($path -ne $File -and -not $acl.AreAccessRulesProtected) { throw 'QA_CUSTODY_ACL_INHERITANCE' }
        if ($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $sid) { throw 'QA_CUSTODY_OWNER' }
        foreach($rule in $acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])) {
            if($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Value -notin @($sid,'S-1-5-18')) { throw 'QA_CUSTODY_ACL_REJECTED' }
        }
        if((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'QA_CUSTODY_LINK_REJECTED' }
    }
}
function Read-QaProtectedSecret {
    param([ValidateSet('Database','Jwt','Otp')][string]$Kind)
    $spec=switch($Kind){
        'Database' {@{file='credentials.dpapi';purpose=$null;format='dimabug-qa-mysql-credentials-v1';field='app_password';pattern='^[A-Za-z0-9+/]{43}=$'}}
        'Jwt' {@{file='jwt.dpapi';purpose='dimabug-qa-jwt-v1';format='dimabug-qa-jwt-v1';field='secret';pattern='^[A-Za-z0-9+/]{86}==$'}}
        'Otp' {@{file='otp.dpapi';purpose='dimabug-qa-otp-key-v1';format='dimabug-qa-otp-key-v1';field='secret';pattern='^[A-Za-z0-9+/]{43}=$'}}
    }
    $file=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) ('DimaBug-QA-MySQL-13318/'+$spec.file)
    Assert-QaCustodyPath $file
    Add-Type -AssemblyName System.Security.Cryptography.ProtectedData
    $bytes=$null;$document=$null;$value=$null;$secure=$null
    try {
        $entropy=if($spec.purpose){[Text.Encoding]::UTF8.GetBytes($spec.purpose)}else{$null}
        $bytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($file),$entropy,[Security.Cryptography.DataProtectionScope]::CurrentUser)
        # .NET parsing avoids cmdlet parameter/module logs of decrypted JSON.
        $document=[System.Text.Json.JsonDocument]::Parse([Text.Encoding]::UTF8.GetString($bytes))
        $element=$document.RootElement
        if($element.GetProperty('format').GetString() -cne $spec.format -or
            $element.GetProperty('database').GetString() -cne 'dima_bug_qa_mvp' -or $element.GetProperty('port').GetInt32() -ne 13318){throw 'QA_CUSTODY_IDENTITY'}
        if($Kind -eq 'Database' -and ($element.GetProperty('host').GetString() -cne '127.0.0.1' -or
            $element.GetProperty('app_user').GetString() -cne 'dimabug_qa_app')){throw 'QA_CUSTODY_IDENTITY'}
        $value=$element.GetProperty($spec.field).GetString()
        if($value -cnotmatch $spec.pattern){throw 'QA_CUSTODY_FORMAT'}
        $secure=[Security.SecureString]::new()
        foreach($character in $value.ToCharArray()){$secure.AppendChar($character)}
        $secure.MakeReadOnly();return $secure
    }catch{if($secure){$secure.Dispose()};throw 'QA_CUSTODY_NOT_CONFIRMED'}
    finally{if($bytes){[Array]::Clear($bytes,0,$bytes.Length)};if($document){$document.Dispose()};$value=$null}
}
function Read-QaDatabasePassword {return Read-QaProtectedSecret -Kind Database}
function Read-QaJwtSecret {return Read-QaProtectedSecret -Kind Jwt}
function Read-QaOtpSecret {return Read-QaProtectedSecret -Kind Otp}
Export-ModuleMember -Function Read-QaDatabasePassword,Read-QaJwtSecret,Read-QaOtpSecret,Assert-QaCustodyPath
