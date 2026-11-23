[CmdletBinding()]
param(
    [ValidateSet('Maintenance','Pilot','Normal')][string]$Mode = 'Maintenance',
    [Parameter(Mandatory)][long]$ExpectedVersion,
    [switch]$Reconfigure
)
$ErrorActionPreference = 'Stop'
$root = 'C:\Proyectos\DimaBug-MVP'
$private = Join-Path $root '.dimabug-private'
$activeFile = Join-Path $private 'backend.managed.json'
if (-not (Get-Acl -LiteralPath $private).AreAccessRulesProtected) { throw 'Custodia sin permisos restringidos' }
# No arranca contra tokens antiguos ni con una clave distinta.
& (Join-Path $PSScriptRoot 'Invoke-GmailRecuperacion.ps1') -Action verify -ExpectedVersion $ExpectedVersion

$runtimeBytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes((Join-Path $private 'staging.runtime.dpapi')), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
$vaultBytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes((Join-Path $private 'gmail-staging.credentials.dpapi')), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
try {
    $runtime = [Text.Encoding]::UTF8.GetString($runtimeBytes) | ConvertFrom-Json -AsHashtable
    $vault = [Text.Encoding]::UTF8.GetString($vaultBytes) | ConvertFrom-Json -AsHashtable
    if ($runtime.format -ne 'dimabug-runtime-v1' -or $vault.environment -ne 'staging') { throw 'Custodia incompatible' }
    if (Test-Path -LiteralPath $activeFile) {
        $old = Get-Content -LiteralPath $activeFile -Raw | ConvertFrom-Json
        $oldPid = [int]$old.pid
        $existing = Get-CimInstance Win32_Process -Filter "ProcessId=$oldPid"
        if ($existing) {
            if (-not $Reconfigure -or $old.format -ne 'dimabug-backend-managed-v1' -or $existing.Name -ne 'java.exe' -or $existing.CommandLine -notmatch 'BackendApplication' -or $existing.CreationDate.ToUniversalTime() -gt [DateTimeOffset]::Parse($old.recordedUtc).UtcDateTime) {
                throw 'Ya existe un proceso o su identidad cambió; no se detiene'
            }
            Stop-Process -Id $oldPid -ErrorAction Stop
            Wait-Process -Id $oldPid -Timeout 10 -ErrorAction SilentlyContinue
        }
    }
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 9090)
    try { $listener.Start() } finally { $listener.Stop() }
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $runtime.executable; $start.WorkingDirectory = $runtime.cwd
    $start.UseShellExecute = $false; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $start.Environment.Clear()
    foreach ($key in $runtime.environment.Keys) { $start.Environment[$key] = [string]$runtime.environment[$key] }
    foreach ($key in @('GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET','GMAIL_TOKEN_ENCRYPTION_KEY','GMAIL_SENDER_EMAIL')) { $start.Environment[$key] = [string]$vault[$key] }
    $start.Environment['SERVER_ADDRESS'] = '127.0.0.1'
    $start.Environment['GMAIL_OAUTH_HABILITADO'] = 'true'
    $start.Environment['GMAIL_ENVIO_REAL_HABILITADO'] = $(if ($Mode -eq 'Maintenance') {'false'} else {'true'})
    $start.Environment['GMAIL_OAUTH_REDIRECT_URI'] = 'http://localhost:9090/login/oauth2/code/google'
    $start.Environment['GMAIL_FRONTEND_URL'] = 'http://localhost:4200/admin/gmail'
    $start.Environment['DIMABUG_AUTH_CORREO_HABILITADO'] = $(if ($Mode -eq 'Maintenance') {'false'} else {'true'})
    $start.Environment['DIMABUG_AUTOMATIZACION_SEGUIMIENTO_HABILITADO'] = $(if ($Mode -eq 'Normal') {'true'} else {'false'})
    if ($Mode -eq 'Pilot') { $start.Environment['DIMABUG_AUTH_CORREO_MAXIMO_MINUTO'] = '1' }
    # Modo de Procedimientos y restantes argumentos/entorno se conservan.
    foreach ($argument in $runtime.arguments) {
        if ($argument -match '^(--|-D)(dimabug\.gmail\.|dimabug\.auth\.correo\.habilitado|dimabug\.automatizacion\.seguimiento-habilitado|server\.address)') {
            throw 'Hay un override en argumentos que requiere revisión antes de arrancar'
        }
        $start.ArgumentList.Add([string]$argument)
    }
    $process = [Diagnostics.Process]::Start($start)
    $record = @{format='dimabug-backend-managed-v1';pid=$process.Id;recordedUtc=[DateTime]::UtcNow.ToString('o');mode=$Mode;authorizationVersion=$ExpectedVersion}
    [IO.File]::WriteAllText($activeFile, ($record | ConvertTo-Json -Compress))
    $out = [IO.File]::Open((Join-Path $private 'backend.managed.out.log'), [IO.FileMode]::Append, [IO.FileAccess]::Write, [IO.FileShare]::ReadWrite)
    $err = [IO.File]::Open((Join-Path $private 'backend.managed.err.log'), [IO.FileMode]::Append, [IO.FileAccess]::Write, [IO.FileShare]::ReadWrite)
    try {
        $copyOut = $process.StandardOutput.BaseStream.CopyToAsync($out)
        $copyErr = $process.StandardError.BaseStream.CopyToAsync($err)
        Write-Output ('BACKEND_INICIADO: PID=' + $process.Id + '; modo=' + $Mode + '; solo loopback; secretos únicamente en memoria')
        $process.WaitForExit()
        $copyOut.GetAwaiter().GetResult(); $copyErr.GetAwaiter().GetResult()
    } finally { $out.Dispose(); $err.Dispose() }
} finally {
    [Security.Cryptography.CryptographicOperations]::ZeroMemory($runtimeBytes)
    [Security.Cryptography.CryptographicOperations]::ZeroMemory($vaultBytes)
    $runtime = $null; $vault = $null; $start = $null
}
