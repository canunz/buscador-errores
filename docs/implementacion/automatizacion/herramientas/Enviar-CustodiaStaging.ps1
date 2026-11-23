[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$file = 'C:\Proyectos\DimaBug-MVP\.dimabug-private\gmail-staging.credentials.dpapi'
$bytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($file), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
$process = $null
try {
    $vault = [Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json -AsHashtable
    if ($vault.environment -cne 'staging' -or $vault.GMAIL_SENDER_EMAIL -cne 'dimabug507@gmail.com' -or
        [Convert]::FromBase64String($vault.GMAIL_TOKEN_ENCRYPTION_KEY).Length -ne 32) { throw 'Custodia incompatible' }
    $payload = @{}
    foreach ($key in @('GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET','GMAIL_TOKEN_ENCRYPTION_KEY','GMAIL_SENDER_EMAIL')) {
        if ([string]::IsNullOrWhiteSpace($vault[$key])) { throw 'Custodia incompleta' }
        $payload[$key] = [string]$vault[$key]
    }
    # Solo este programa público viaja como argumento. El JSON secreto usa stdin de SSH.
    $receiver = @'
import socket,sys
s=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)
s.settimeout(60)
s.connect('/run/dimabug-staging-custodia/entrada.sock')
payload=sys.stdin.buffer.read(32769)
if len(payload)>32768:raise SystemExit(1)
s.sendall(payload)
s.shutdown(socket.SHUT_WR)
result=s.recv(256)
if result!=b'INSTALACION_VERIFICADA'+bytes([10]):raise SystemExit(1)
print('INSTALACION_VERIFICADA')
s.close()
'@
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($receiver))
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'ssh.exe'
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($arg in @('-o','BatchMode=yes','-o','ConnectTimeout=10','carolina@157.230.187.214',
        ('python3 -c "import base64;exec(base64.b64decode(''' + $encoded + '''))"'))) { $start.ArgumentList.Add($arg) }
    $process = [Diagnostics.Process]::Start($start)
    $process.StandardInput.Write(($payload | ConvertTo-Json -Compress))
    $process.StandardInput.Close()
    $result = $process.StandardOutput.ReadToEnd()
    $errorText = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -or $result.Trim() -cne 'INSTALACION_VERIFICADA') {
        throw 'Transferencia no confirmada; comprobar estado con instalador antes de reintentar'
    }
    'INSTALACION_VERIFICADA: custodia transferida por SSH; sin secretos en salida ni argumentos'
} finally {
    [Security.Cryptography.CryptographicOperations]::ZeroMemory($bytes)
    $vault=$null; $payload=$null; $result=$null; $errorText=$null
    if ($process) { $process.Dispose() }
}
