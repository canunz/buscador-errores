[CmdletBinding()]
param(
    [ValidateSet('inspect','backup','preflight','reset','authorize','verify','refresh')][string]$Action = 'inspect',
    [int]$ActorId = 0,
    [long]$ExpectedVersion = -1,
    [string]$BackupFile = 'C:\Proyectos\DimaBug-MVP\.dimabug-private\gmail-staging.database.backup.aes',
    [switch]$GoogleRevocationUnverified,
    [switch]$MaintenanceConfirmed
)
$ErrorActionPreference = 'Stop'
$root = 'C:\Proyectos\DimaBug-MVP'
$private = Join-Path $root '.dimabug-private'
if (-not (Get-Acl -LiteralPath $private).AreAccessRulesProtected) { throw 'La carpeta privada debe tener permisos restringidos' }
$runtimeFile = Join-Path $private 'staging.runtime.dpapi'
if (-not (Test-Path -LiteralPath $runtimeFile)) {
    # La salida sensible se captura en memoria y se cifra; nunca se escribe ni se muestra en claro.
    $runtimeText = @'
import psutil,json,shlex,datetime
from pathlib import Path
root=Path('C:/Proyectos/DimaBug-MVP').resolve()
found=[]
for p in psutil.process_iter(['name','pid']):
    if (p.info['name'] or '').lower()!='java.exe':continue
    try:
        if not Path(p.cwd()).resolve().is_relative_to(root):continue
        args=p.cmdline()
        if not any('BackendApplication' in a for a in args):continue
        expanded=[]
        for a in args[1:]:
            if a.startswith('@'):expanded.extend(shlex.split(Path(a[1:]).read_text(encoding='utf-8-sig'),posix=True,comments=True))
            else:expanded.append(a)
        found.append({'format':'dimabug-runtime-v1','pid':p.pid,'executable':p.exe(),'cwd':p.cwd(),'arguments':expanded,'environment':p.environ(),'capturedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()})
    except (psutil.Error,OSError):pass
if len(found)!=1:raise SystemExit('Se requiere exactamente un backend local del proyecto')
print(json.dumps(found[0]))
'@ | python -B -
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo capturar el contexto de arranque' }
    $runtimeBytes = [Text.Encoding]::UTF8.GetBytes($runtimeText)
    $protected = [Security.Cryptography.ProtectedData]::Protect($runtimeBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $check = [Security.Cryptography.ProtectedData]::Unprotect($protected, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    if (-not [Security.Cryptography.CryptographicOperations]::FixedTimeEquals($runtimeBytes, $check)) { throw 'No se verific贸 el respaldo de arranque' }
    $f = [IO.File]::Open($runtimeFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $f.Write($protected) } finally { $f.Dispose() }
    [Security.Cryptography.CryptographicOperations]::ZeroMemory($runtimeBytes)
    $runtimeText = $null
}

$runtimeBytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($runtimeFile), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
$vaultBytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes((Join-Path $private 'gmail-staging.credentials.dpapi')), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
try {
    $runtime = [Text.Encoding]::UTF8.GetString($runtimeBytes) | ConvertFrom-Json -AsHashtable
    $vault = [Text.Encoding]::UTF8.GetString($vaultBytes) | ConvertFrom-Json -AsHashtable
    if ($runtime.format -ne 'dimabug-runtime-v1' -or $vault.environment -ne 'staging') { throw 'Custodia o contexto incompatible' }
    $classpath = $null
    for ($i=0; $i -lt $runtime.arguments.Count-1; $i++) {
        if ($runtime.arguments[$i] -in @('-cp','-classpath','--class-path')) { $classpath = $runtime.arguments[$i+1]; break }
    }
    if (-not $classpath -or -not $classpath.Contains('dima-bug-backend')) { throw 'Classpath de mantenimiento no identificado' }
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $runtime.executable
    $start.WorkingDirectory = Join-Path $root 'dima-bug-backend'
    $start.UseShellExecute = $false
    $start.Environment.Clear()
    foreach ($key in $runtime.environment.Keys) { $start.Environment[$key] = [string]$runtime.environment[$key] }
    foreach ($key in @('GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET','GMAIL_TOKEN_ENCRYPTION_KEY','GMAIL_SENDER_EMAIL')) { $start.Environment[$key] = [string]$vault[$key] }
    $start.Environment['GMAIL_OAUTH_HABILITADO'] = 'true'
    $start.Environment['GMAIL_ENVIO_REAL_HABILITADO'] = 'false'
    $start.Environment['GMAIL_OAUTH_REDIRECT_URI'] = 'http://localhost:9090/login/oauth2/code/google'
    $start.ArgumentList.Add('-Duser.timezone=UTC')
    $start.ArgumentList.Add('-cp'); $start.ArgumentList.Add($classpath)
    $start.ArgumentList.Add('cl.casol.backend.correo.maintenance.RecuperarGmail')
    $start.ArgumentList.Add($Action)
    switch ($Action) {
        'backup' {
            if ($ActorId -le 0) { throw 'Falta actor administrador' }
            if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($BackupFile)) -ne $private) { throw 'El respaldo debe estar en la carpeta privada protegida' }
            $start.ArgumentList.Add($BackupFile); $start.ArgumentList.Add([string]$ActorId)
        }
        'preflight' {
            if ($ActorId -le 0 -or $ExpectedVersion -lt 0) { throw 'Falta actor/versi髇' }
            $start.ArgumentList.Add($BackupFile); $start.ArgumentList.Add([string]$ActorId); $start.ArgumentList.Add([string]$ExpectedVersion)
        }
        'reset' {
            if (-not $GoogleRevocationUnverified -or -not $MaintenanceConfirmed -or $ActorId -le 0 -or $ExpectedVersion -lt 0) { throw 'Faltan las condiciones de recuperaci贸n' }
            $start.ArgumentList.Add($BackupFile); $start.ArgumentList.Add([string]$ActorId); $start.ArgumentList.Add([string]$ExpectedVersion)
            $start.ArgumentList.Add('--google-revocation-unverified'); $start.ArgumentList.Add('--maintenance-confirmed')
        }
        'authorize' {
            if (-not $MaintenanceConfirmed -or $ActorId -le 0 -or $ExpectedVersion -lt 0) { throw 'Falta mantenimiento/actor/versi贸n' }
            $start.ArgumentList.Add([string]$ActorId); $start.ArgumentList.Add([string]$ExpectedVersion)
        }
        'verify' {
            if ($ExpectedVersion -lt 0) { throw 'Falta versi贸n' }
            $start.ArgumentList.Add([string]$ExpectedVersion)
        }
        'refresh' {
            if (-not $MaintenanceConfirmed -or $ExpectedVersion -lt 0) { throw 'Falta mantenimiento/versi贸n' }
            $start.ArgumentList.Add([string]$ExpectedVersion); $start.ArgumentList.Add('--maintenance-confirmed')
        }
    }
    $p = [Diagnostics.Process]::Start($start)
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw 'La operaci贸n no se complet贸; solo se muestran categor铆as sanitizadas' }
} finally {
    [Security.Cryptography.CryptographicOperations]::ZeroMemory($runtimeBytes)
    [Security.Cryptography.CryptographicOperations]::ZeroMemory($vaultBytes)
    $runtime = $null; $vault = $null; $start = $null
}
