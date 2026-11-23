[CmdletBinding()]
param(
    [string]$Directory = 'C:\Proyectos\DimaBug-MVP\.dimabug-private',
    [switch]$SelfTest
)
$ErrorActionPreference = 'Stop'

function Get-PlainSecret([Security.SecureString]$Secret) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

function Protect-Portable([byte[]]$Content, [string]$Phrase, [byte[]]$Salt, [byte[]]$Nonce) {
    $passwordBytes = [Text.Encoding]::UTF8.GetBytes($Phrase)
    $derived = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2(
        $passwordBytes, $Salt, 600000, [Security.Cryptography.HashAlgorithmName]::SHA256, 32)
    try {
        $cipher = [byte[]]::new($Content.Length)
        $tag = [byte[]]::new(16)
        $aes = [Security.Cryptography.AesGcm]::new($derived, 16)
        try { $aes.Encrypt($Nonce, $Content, $cipher, $tag, [Text.Encoding]::UTF8.GetBytes('dimabug:gmail-vault:v1')) }
        finally { $aes.Dispose() }
        return @{
            format = 'dimabug-gmail-vault-v1'; kdf = 'PBKDF2-SHA256'; iterations = 600000
            salt = [Convert]::ToBase64String($Salt); nonce = [Convert]::ToBase64String($Nonce)
            tag = [Convert]::ToBase64String($tag); ciphertext = [Convert]::ToBase64String($cipher)
        }
    } finally {
        [Security.Cryptography.CryptographicOperations]::ZeroMemory($derived)
        [Security.Cryptography.CryptographicOperations]::ZeroMemory($passwordBytes)
    }
}

function Unprotect-Portable($Envelope, [string]$Phrase) {
    if ($Envelope.format -ne 'dimabug-gmail-vault-v1' -or $Envelope.iterations -ne 600000) {
        throw 'Formato de custodia no admitido'
    }
    $salt = [Convert]::FromBase64String($Envelope.salt)
    $nonce = [Convert]::FromBase64String($Envelope.nonce)
    $tag = [Convert]::FromBase64String($Envelope.tag)
    $cipher = [Convert]::FromBase64String($Envelope.ciphertext)
    $passwordBytes = [Text.Encoding]::UTF8.GetBytes($Phrase)
    $derived = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2(
        $passwordBytes, $salt, 600000, [Security.Cryptography.HashAlgorithmName]::SHA256, 32)
    try {
        $content = [byte[]]::new($cipher.Length)
        $aes = [Security.Cryptography.AesGcm]::new($derived, 16)
        try { $aes.Decrypt($nonce, $cipher, $tag, $content, [Text.Encoding]::UTF8.GetBytes('dimabug:gmail-vault:v1')) }
        finally { $aes.Dispose() }
        return ,$content
    } finally {
        [Security.Cryptography.CryptographicOperations]::ZeroMemory($derived)
        [Security.Cryptography.CryptographicOperations]::ZeroMemory($passwordBytes)
    }
}

if ($SelfTest) {
    # Datos exclusivamente sintéticos; no crea archivos ni una clave de DimaBug.
    $testBytes = [Text.Encoding]::UTF8.GetBytes('contenido-sintetico-solo-prueba')
    $salt = [byte[]](1..16); $nonce = [byte[]](1..12)
    $testEnvelope = Protect-Portable $testBytes 'frase-sintetica-solo-test' $salt $nonce
    $decoded = Unprotect-Portable $testEnvelope 'frase-sintetica-solo-test'
    if (-not [Security.Cryptography.CryptographicOperations]::FixedTimeEquals($testBytes, $decoded)) { throw 'Fallo de verificación' }
    $rejected = $false
    try { $null = Unprotect-Portable $testEnvelope 'frase-incorrecta-solo-test' } catch { $rejected = $true }
    if (-not $rejected) { throw 'Debe rechazar la frase incorrecta' }
    $protected = [Security.Cryptography.ProtectedData]::Protect($testBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $decoded = [Security.Cryptography.ProtectedData]::Unprotect($protected, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    if (-not [Security.Cryptography.CryptographicOperations]::FixedTimeEquals($testBytes, $decoded)) { throw 'Fallo DPAPI' }
    Write-Output 'SELFTEST_OK: portable, rechazo de frase incorrecta y DPAPI; sin archivos ni clave real.'
    return
}

if (-not $IsWindows -or $PSVersionTable.PSVersion.Major -lt 7) { throw 'Requiere PowerShell 7 en Windows' }
$root = [IO.Path]::GetFullPath('C:\Proyectos\DimaBug-MVP')
$target = [IO.Path]::GetFullPath($Directory)
if ($target -ne [IO.Path]::Combine($root, '.dimabug-private')) { throw 'La carpeta de custodia no coincide con la autorizada' }
$dpapiFile = Join-Path $target 'gmail-staging.credentials.dpapi'
$portableFile = Join-Path $target 'gmail-staging.credentials.recovery.json'
if ((Test-Path -LiteralPath $dpapiFile) -or (Test-Path -LiteralPath $portableFile)) { throw 'Existe custodia previa; no se sobrescribe ni se genera otra clave' }

Write-Host 'No introduzca secretos como comandos. Las entradas sensibles siguientes están ocultas.'
$clientId = $env:GOOGLE_CLIENT_ID
if ([string]::IsNullOrWhiteSpace($clientId)) { $clientId = Get-PlainSecret (Read-Host 'GOOGLE_CLIENT_ID' -AsSecureString) }
$clientSecret = $env:GOOGLE_CLIENT_SECRET
if ([string]::IsNullOrWhiteSpace($clientSecret)) { $clientSecret = Get-PlainSecret (Read-Host 'GOOGLE_CLIENT_SECRET' -AsSecureString) }
$sender = Read-Host 'Cuenta Gmail remitente que se autorizará'
if ($sender -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') { throw 'Remitente inválido' }
$phrase1 = Get-PlainSecret (Read-Host 'Frase de recuperación portable (mínimo 20 caracteres; guárdela fuera de esta carpeta)' -AsSecureString)
$phrase2 = Get-PlainSecret (Read-Host 'Repita la frase de recuperación' -AsSecureString)
if ($phrase1.Length -lt 20 -or $phrase1 -cne $phrase2) { throw 'Frase insuficiente o distinta; no se generó la clave' }

$null = New-Item -ItemType Directory -Path $target -Force
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetAccessRuleProtection($true, $false)
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl.SetOwner($sid)
$rule = [Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
$acl.AddAccessRule($rule)
Set-Acl -LiteralPath $target -AclObject $acl

# Se genera exactamente una vez, después de validar frase y preparar custodia.
$keyBytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
$vault = @{
    format = 'dimabug-gmail-credentials-v1'; environment = 'staging'; keyVersion = ('staging-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
    createdUtc = [DateTime]::UtcNow.ToString('o'); GOOGLE_CLIENT_ID = $clientId; GOOGLE_CLIENT_SECRET = $clientSecret
    GMAIL_TOKEN_ENCRYPTION_KEY = [Convert]::ToBase64String($keyBytes); GMAIL_SENDER_EMAIL = $sender
}
$content = [Text.Encoding]::UTF8.GetBytes(($vault | ConvertTo-Json -Compress))
try {
    $portable = Protect-Portable $content $phrase1 ([Security.Cryptography.RandomNumberGenerator]::GetBytes(16)) ([Security.Cryptography.RandomNumberGenerator]::GetBytes(12))
    $decoded = Unprotect-Portable $portable $phrase1
    if (-not [Security.Cryptography.CryptographicOperations]::FixedTimeEquals($content, $decoded)) { throw 'Respaldo portable no verificado' }
    $protected = [Security.Cryptography.ProtectedData]::Protect($content, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $check = [Security.Cryptography.ProtectedData]::Unprotect($protected, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    if (-not [Security.Cryptography.CryptographicOperations]::FixedTimeEquals($content, $check)) { throw 'Custodia Windows no verificada' }
    # Nunca sobrescribe archivos existentes. Si la segunda escritura falla se conserva la primera.
    $stream = [IO.File]::Open($portableFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $bytes = [Text.Encoding]::UTF8.GetBytes(($portable | ConvertTo-Json -Compress)); $stream.Write($bytes) } finally { $stream.Dispose() }
    $stream = [IO.File]::Open($dpapiFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $stream.Write($protected) } finally { $stream.Dispose() }
    Write-Output 'CUSTODIA_OK: dos copias cifradas verificadas. Ningún secreto se muestra. Base de datos y servicios sin cambios.'
} finally {
    [Security.Cryptography.CryptographicOperations]::ZeroMemory($keyBytes)
    [Security.Cryptography.CryptographicOperations]::ZeroMemory($content)
    $clientId = $null; $clientSecret = $null; $phrase1 = $null; $phrase2 = $null; $vault = $null
}
