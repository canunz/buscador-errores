#requires -Version 7.4
[CmdletBinding()]
param([switch]$SelfTest, [switch]$VerifyOnly)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# No SQL, Docker, plaintext files, staging configuration or application startup.
function New-RandomBytes([int]$Count) {
    $b = [byte[]]::new($Count)
    [Security.Cryptography.RandomNumberGenerator]::Fill($b)
    return ,$b
}
function Get-Key([string]$Phrase, [byte[]]$Salt) {
    return ,[Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2($Phrase, $Salt, 600000, [Security.Cryptography.HashAlgorithmName]::SHA256, 32)
}
function Protect-Portable([byte[]]$Plain, [string]$Phrase) {
    $salt = New-RandomBytes 32
    $nonce = New-RandomBytes 12
    $key = Get-Key $Phrase $salt
    $cipher = [byte[]]::new($Plain.Length)
    $tag = [byte[]]::new(16)
    $aes = [Security.Cryptography.AesGcm]::new($key, 16)
    try { $aes.Encrypt($nonce, $Plain, $cipher, $tag, [Text.Encoding]::UTF8.GetBytes('dimabug-qa-mysql-v1')) }
    finally { $aes.Dispose(); [Array]::Clear($key, 0, $key.Length) }
    return @{format='dimabug-qa-mysql-v1'; kdf='PBKDF2-SHA256'; iterations=600000; cipher='AES-256-GCM';
        salt=[Convert]::ToBase64String($salt); nonce=[Convert]::ToBase64String($nonce);
        tag=[Convert]::ToBase64String($tag); ciphertext=[Convert]::ToBase64String($cipher)}
}
function Unprotect-Portable($Document, [string]$Phrase) {
    if ($Document.format -ne 'dimabug-qa-mysql-v1' -or $Document.kdf -ne 'PBKDF2-SHA256' -or
        $Document.iterations -ne 600000 -or $Document.cipher -ne 'AES-256-GCM') { throw 'Formato rechazado' }
    $key = Get-Key $Phrase ([Convert]::FromBase64String($Document.salt))
    $cipher = [Convert]::FromBase64String($Document.ciphertext)
    $plain = [byte[]]::new($cipher.Length)
    $aes = [Security.Cryptography.AesGcm]::new($key, 16)
    try { $aes.Decrypt([Convert]::FromBase64String($Document.nonce), $cipher,
        [Convert]::FromBase64String($Document.tag), $plain, [Text.Encoding]::UTF8.GetBytes('dimabug-qa-mysql-v1')) }
    finally { $aes.Dispose(); [Array]::Clear($key, 0, $key.Length) }
    return ,$plain
}
function Assert-Same([byte[]]$A, [byte[]]$B) {
    if ([Convert]::ToBase64String($A) -cne [Convert]::ToBase64String($B)) { throw 'Verificacion fallida' }
}
function Read-Hidden([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $p = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($p) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($p); $secure.Dispose() }
}
function New-PrivateDirectory([string]$Path) {
    if (Test-Path -LiteralPath $Path) { throw 'Destino existente: no se sobrescribe' }
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.SetOwner($sid)
    foreach ($id in @($sid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new($id, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    # Set ACL at directory creation, before any encrypted data is written.
    $null = [IO.FileSystemAclExtensions]::CreateDirectory($acl, $Path)
    $actual = Get-Acl -LiteralPath $Path
    if (-not $actual.AreAccessRulesProtected) { throw 'ACL no protegida' }
    foreach ($rule in $actual.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) {
        if ($rule.IdentityReference.Value -notin @($sid.Value, 'S-1-5-18')) { throw 'ACL inesperada' }
    }
}
try {
    if (-not $IsWindows) { throw 'Solo Windows' }
    if ($SelfTest) {
        # Synthetic public payload only; no credentials, DPAPI, filesystem or interactive input.
        $payload = [Text.Encoding]::UTF8.GetBytes('synthetic-public-test')
        $doc = Protect-Portable $payload 'synthetic-public-test-passphrase'
        Assert-Same $payload (Unprotect-Portable $doc 'synthetic-public-test-passphrase')
        $rejected = $false
        try { $null = Unprotect-Portable $doc 'incorrect-public-test-passphrase' } catch { $rejected = $true }
        if (-not $rejected) { throw 'Frase incorrecta aceptada' }
        $tag = [Convert]::FromBase64String($doc.tag); $tag[0] = $tag[0] -bxor 1
        $doc.tag = [Convert]::ToBase64String($tag)
        $rejected = $false
        try { $null = Unprotect-Portable $doc 'synthetic-public-test-passphrase' } catch { $rejected = $true }
        if (-not $rejected) { throw 'Alteracion aceptada' }
        Write-Output '{"estado":"AUTOPRUEBA_CRIPTOGRAFICA_OK","credenciales_generadas":false,"archivos_creados":false}'
        exit 0
    }
    Add-Type -AssemblyName System.Security.Cryptography.ProtectedData
    $primary = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'DimaBug-QA-MySQL-13318'
    $backupBase = 'C:\Users\carol\OneDrive\Estudios y desarrollo personal'
    $backup = Join-Path $backupBase 'DimaBug-QA-MySQL-13318'
    if ($VerifyOnly) {
        $primaryFile = Join-Path $primary 'credentials.dpapi'
        $backupFile = Join-Path $backup 'credentials.portable.json'
        $recoveryPhrase = Read-Hidden 'Frase del respaldo QA existente (no se generan credenciales)'
        $localRead = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($primaryFile), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        $backupRead = Unprotect-Portable (Get-Content -LiteralPath $backupFile -Raw | ConvertFrom-Json) $recoveryPhrase
        Assert-Same $localRead $backupRead
        $metadata = [Text.Encoding]::UTF8.GetString($localRead) | ConvertFrom-Json
        if ($metadata.format -ne 'dimabug-qa-mysql-credentials-v1' -or $metadata.port -ne 13318 -or $metadata.database -ne 'dima_bug_qa_mvp') { throw 'Identidad QA incorrecta' }
        $metadata = $null
        Write-Output '{"estado":"CUSTODIA_QA_VERIFICADA","solo_verificacion":true,"secretos_mostrados":false,"mysql_modificado":false}'
        exit 0
    }
    if (-not (Test-Path -LiteralPath $backupBase -PathType Container)) { throw 'Carpeta de respaldo ausente' }
    if ((Test-Path -LiteralPath $primary) -or (Test-Path -LiteralPath $backup)) { throw 'Custodia existente: no se sobrescribe ni se regenera' }
    $phrase = Read-Hidden 'Frase NUEVA del respaldo QA (minimo 20 caracteres; conservar fuera de este equipo)'
    $confirmation = Read-Hidden 'Repetir frase del respaldo QA'
    if ($phrase.Length -lt 20 -or $phrase -cne $confirmation) { throw 'Frase insuficiente o no coincide' }
    $confirmation = $null
    New-PrivateDirectory $primary
    New-PrivateDirectory $backup
    # Generation occurs only after destination/ACL checks and hidden phrase confirmation.
    $document = [ordered]@{format='dimabug-qa-mysql-credentials-v1'; host='127.0.0.1'; port=13318;
        database='dima_bug_qa_mvp'; root=[Convert]::ToBase64String((New-RandomBytes 32));
        schema_user='dimabug_qa_schema'; schema_password=[Convert]::ToBase64String((New-RandomBytes 32));
        app_user='dimabug_qa_app'; app_password=[Convert]::ToBase64String((New-RandomBytes 32))}
    $plain = [Text.Encoding]::UTF8.GetBytes(($document | ConvertTo-Json -Compress))
    $protected = [Security.Cryptography.ProtectedData]::Protect($plain, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $portable = Protect-Portable $plain $phrase
    $primaryFile = Join-Path $primary 'credentials.dpapi'
    $backupFile = Join-Path $backup 'credentials.portable.json'
    # New directories guaranteed; CreateNew still prevents overwriting any file.
    $stream = [IO.File]::Open($primaryFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $stream.Write($protected, 0, $protected.Length) } finally { $stream.Dispose() }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($portable | ConvertTo-Json -Compress))
    $stream = [IO.File]::Open($backupFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    $localRead = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($primaryFile), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    Assert-Same $plain $localRead
    # Re-enter phrase to prove recovery using the persisted backup, not memory ciphertext.
    $recoveryPhrase = Read-Hidden 'Introducir otra vez la frase para verificar recuperacion del archivo guardado'
    $backupRead = Unprotect-Portable (Get-Content -LiteralPath $backupFile -Raw | ConvertFrom-Json) $recoveryPhrase
    Assert-Same $plain $backupRead
    Write-Output '{"estado":"CUSTODIA_QA_VERIFICADA","dpapi_verificada":true,"respaldo_portable_verificado":true,"secretos_mostrados":false,"mysql_modificado":false}'
} catch {
    Write-Output '{"estado":"CUSTODIA_QA_NO_COMPLETADA","secretos_mostrados":false,"reintento_automatico":false}'
    exit 1
} finally {
    foreach ($name in @('plain','localRead','backupRead')) {
        $value = Get-Variable -Name $name -ValueOnly -ErrorAction SilentlyContinue
        if ($value -is [byte[]]) { [Array]::Clear($value, 0, $value.Length) }
    }
    # Managed strings cannot be reliably zeroed; close this dedicated terminal after completion.
    $phrase = $null; $recoveryPhrase = $null; $confirmation = $null; $document = $null
}
