#requires -Version 7.4
# Bootstrap only: no application schema/users/migrations or application startup.
[CmdletBinding()]
param([switch]$Create)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$image = 'mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
$compose = Join-Path $PSScriptRoot 'compose.qa-local.json'
function Invoke-LocalDocker([string[]]$Arguments) {
    $result = & docker --context desktop-linux @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Docker operation failed; output suppressed' }
    return ($result -join "`n")
}
try {
    # Validator checks ALL containers including stopped ones; never reuse a name or volume.
    & python -B (Join-Path $PSScriptRoot 'verify_mysql_preparation.py') --inspect-docker
    if ($LASTEXITCODE -ne 0) { throw 'Preflight failed' }
    $context = (Invoke-LocalDocker @('context','inspect','desktop-linux') | ConvertFrom-Json)[0]
    if (-not $context.Endpoints.docker.Host.StartsWith('npipe://')) { throw 'Nonlocal context' }
    $server = Invoke-LocalDocker @('version','--format','{{json .Server}}') | ConvertFrom-Json
    if ($server.Os -ne 'linux' -or $server.Arch -notin @('amd64','x86_64')) { throw 'Unexpected architecture' }
    if (-not $Create) {
        Write-Output '{"estado":"MYSQL_QA_PREPARADO","recursos_creados":false}'
        exit 0
    }
    Add-Type -AssemblyName System.Security.Cryptography.ProtectedData
    $vault = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'DimaBug-QA-MySQL-13318/credentials.dpapi'
    $plain = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($vault), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $credentials = [Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
    if ($credentials.format -ne 'dimabug-qa-mysql-credentials-v1' -or $credentials.host -ne '127.0.0.1' -or
        $credentials.port -ne 13318 -or $credentials.database -ne 'dima_bug_qa_mvp' -or
        [Convert]::FromBase64String($credentials.root).Length -ne 32) { throw 'Wrong vault identity' }
    $secretDir = Join-Path $root 'qa/runtime/secrets'
    if (Test-Path -LiteralPath $secretDir) { throw 'Secret directory exists; do not overwrite' }
    $null = New-Item -ItemType Directory -Path (Split-Path $secretDir) -Force
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.SetOwner($sid)
    foreach ($id in @($sid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($id,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    $null = [IO.FileSystemAclExtensions]::CreateDirectory($acl, $secretDir)
    $secret = Join-Path $secretDir 'mysql-root.txt'
    & git -C $root check-ignore --quiet -- $secret
    if ($LASTEXITCODE -ne 0) { throw 'Secret not ignored' }
    $tracked = & git -C $root ls-files -- qa/runtime
    if ($tracked) { throw 'Runtime files tracked' }
    $bytes = [Text.Encoding]::UTF8.GetBytes($credentials.root)
    $stream = [IO.File]::Open($secret, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
    # Download ONLY the immutable, approved architecture-specific image.
    $null = Invoke-LocalDocker @('pull','--platform','linux/amd64',$image)
    $metadata = (Invoke-LocalDocker @('image','inspect',$image) | ConvertFrom-Json)[0]
    if ($metadata.Os -ne 'linux' -or $metadata.Architecture -ne 'amd64') { throw 'Image mismatch' }
    # Revalidate immediately before creation to avoid accidentally reusing resources.
    & python -B (Join-Path $PSScriptRoot 'verify_mysql_preparation.py') --inspect-docker
    if ($LASTEXITCODE -ne 0) { throw 'Creation gate failed' }
    $null = Invoke-LocalDocker @('compose','-f',$compose,'up','-d','--no-build','--pull','never','mysql')
    $ready = $false
    for ($i=0; $i -lt 90; $i++) {
        # Password is read inside the container, never passed in command arguments or output.
        $null = & docker --context desktop-linux exec dimabug-qa-mvp-13318-mysql sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" mysqladmin --protocol=tcp -h127.0.0.1 -uroot ping --silent' 2>&1
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Readiness not verified; do not retry creation' }
    $state = (Invoke-LocalDocker @('inspect','dimabug-qa-mvp-13318-mysql') | ConvertFrom-Json)[0]
    $bindings = $state.HostConfig.PortBindings.'3306/tcp'
    if ($bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1' -or $bindings[0].HostPort -ne '13318') { throw 'Binding mismatch' }
    $net = (Invoke-LocalDocker @('network','inspect','dimabug-qa-mvp-13318-net') | ConvertFrom-Json)[0]
    if (-not $net.Internal -or @($state.NetworkSettings.Networks.PSObject.Properties.Name).Count -ne 1) { throw 'Network mismatch' }
    $tcp = [Net.Sockets.TcpClient]::new()
    try {
        $task = $tcp.ConnectAsync('127.0.0.1',13318)
        if (-not $task.Wait(3000) -or -not $tcp.Connected) { throw 'Host port not accessible' }
    } finally { $tcp.Dispose() }
    Write-Output '{"estado":"MYSQL_QA_INICIALIZADO","puerto":13318,"solo_loopback":true,"red_interna":true,"imagen_amd64_verificada":true,"esquema_mvp_creado":false,"usuarios_mvp_creados":false,"secretos_mostrados":false}'
} catch {
    Write-Output '{"estado":"MYSQL_QA_NO_COMPLETADO","reintento_automatico":false,"secretos_mostrados":false,"no_eliminar_recursos":true}'
    exit 1
} finally {
    foreach ($name in @('plain','bytes')) {
        $value = Get-Variable -Name $name -ValueOnly -ErrorAction SilentlyContinue
        if ($value -is [byte[]]) { [Array]::Clear($value,0,$value.Length) }
    }
    $credentials=$null
}
