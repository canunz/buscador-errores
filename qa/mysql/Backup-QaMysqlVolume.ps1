#requires -Version 7.4
# Physical backup of the approved QA volume; no SQL or container startup.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
try {
    $root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    $endpoint=& docker --context desktop-linux context inspect desktop-linux --format '{{json .Endpoints.docker.Host}}'
    if($LASTEXITCODE -ne 0 -or ($endpoint | ConvertFrom-Json) -notlike 'npipe://*'){throw 'Nonlocal engine'}
    $state=& docker --context desktop-linux inspect --format '{{json .State}}' dimabug-qa-mvp-13318-mysql | ConvertFrom-Json
    if($LASTEXITCODE -ne 0 -or $state.Running -or $state.ExitCode -ne 0){throw 'MySQL not cleanly stopped'}
    $mounts=& docker --context desktop-linux inspect --format '{{json .Mounts}}' dimabug-qa-mvp-13318-mysql | ConvertFrom-Json
    if($LASTEXITCODE -ne 0 -or @($mounts | Where-Object {$_.Type -eq 'volume' -and $_.Name -eq 'dimabug-qa-mvp-13318-mysql-data' -and $_.Destination -eq '/var/lib/mysql'}).Count -ne 1){throw 'Volume mismatch'}
    $backupRoot=Join-Path $root 'qa/runtime/backups'
    $null=New-Item -ItemType Directory -Path $backupRoot -Force
    $backupDir=Join-Path $backupRoot ('pre-proxy-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
    $acl=[Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true,$false)
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.SetOwner($sid)
    foreach($id in @($sid,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($id,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    $null=[IO.FileSystemAclExtensions]::CreateDirectory($acl,$backupDir)
    $archive=Join-Path $backupDir 'mysql-data.tar'
    & git -C $root check-ignore --quiet -- $archive
    if($LASTEXITCODE -ne 0){throw 'Backup not ignored'}
    $info=[Diagnostics.ProcessStartInfo]::new('docker')
    $info.UseShellExecute=$false;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($arg in @('--context','desktop-linux','cp','dimabug-qa-mvp-13318-mysql:/var/lib/mysql/.','-')){$info.ArgumentList.Add($arg)}
    $process=[Diagnostics.Process]::Start($info)
    $stderr=$process.StandardError.ReadToEndAsync()
    $file=[IO.File]::Open($archive,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
    try{$process.StandardOutput.BaseStream.CopyTo($file)}finally{$file.Dispose()}
    $process.WaitForExit();$null=$stderr.GetAwaiter().GetResult()
    if($process.ExitCode -ne 0){throw 'Backup failed'}
    $process.Dispose()
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'compose.qa-local.json') -Destination (Join-Path $backupDir 'compose.before.json')
    $metadata=@{format='dimabug-qa-pre-proxy-v1';archive='mysql-data.tar';sha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLower();source_container='dimabug-qa-mvp-13318-mysql';source_volume='dimabug-qa-mvp-13318-mysql-data';clean_stop=$true;acl_restricted=(Get-Acl -LiteralPath $backupDir).AreAccessRulesProtected}
    $metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backupDir 'backup.json') -Encoding utf8
    @{estado='RESPALDO_FISICO_CREADO';directorio=$backupDir;acl_restringida=$metadata.acl_restricted;secretos_mostrados=$false} | ConvertTo-Json -Compress
}catch{
    Write-Output '{"estado":"RESPALDO_NO_VERIFICADO","secretos_mostrados":false}'
    exit 1
}
