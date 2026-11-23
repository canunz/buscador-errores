#requires -Version 7.4
# Creates ONLY the two approved, locked QA accounts. No grants, schema DDL or unlock.
[CmdletBinding()]
param([switch]$Create)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$mysqlName='dimabug-qa-mvp-13318-mysql'
$completed=0
function Invoke-DockerRead([string[]]$Arguments){
    $output=& docker --context desktop-linux @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw 'Docker read failed; output suppressed'}
    return ($output -join "`n")
}
function Invoke-QaSql([string]$Sql){
    $info=[Diagnostics.ProcessStartInfo]::new('docker')
    $info.UseShellExecute=$false
    $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $command='MYSQL_HISTFILE=/dev/null MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql --protocol=socket --user=root --batch --raw --skip-column-names --connect-timeout=5'
    foreach($arg in @('--context','desktop-linux','exec','-i',$mysqlName,'sh','-c',$command)){$info.ArgumentList.Add($arg)}
    $process=[Diagnostics.Process]::Start($info)
    $outTask=$process.StandardOutput.ReadToEndAsync();$errorTask=$process.StandardError.ReadToEndAsync()
    try{
        $process.StandardInput.WriteLine($Sql);$process.StandardInput.Close()
        if(-not $process.WaitForExit(15000)){ $process.Kill();throw 'SQL timeout; do not retry' }
        $output=$outTask.GetAwaiter().GetResult();$null=$errorTask.GetAwaiter().GetResult()
        if($process.ExitCode -ne 0){throw 'SQL failed; output suppressed'}
        return $output.Trim()
    }finally{$process.Dispose()}
}
try{
    $context=Invoke-DockerRead @('context','inspect','desktop-linux','--format','{{json .Endpoints.docker.Host}}') | ConvertFrom-Json
    if(-not $context.StartsWith('npipe://')){throw 'Nonlocal context'}
    $running=Invoke-DockerRead @('inspect','--format','{{json .State.Running}}',$mysqlName) | ConvertFrom-Json
    $image=Invoke-DockerRead @('inspect','--format','{{json .Config.Image}}',$mysqlName) | ConvertFrom-Json
    if(-not $running -or $image -ne 'mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'){throw 'MySQL identity mismatch'}
    $mounts=Invoke-DockerRead @('inspect','--format','{{json .Mounts}}',$mysqlName) | ConvertFrom-Json
    if(@($mounts | Where-Object {$_.Type -eq 'volume' -and $_.Name -eq 'dimabug-qa-mvp-13318-mysql-data' -and $_.Destination -eq '/var/lib/mysql'}).Count -ne 1){throw 'Wrong volume'}
    $networks=Invoke-DockerRead @('inspect','--format','{{json .NetworkSettings.Networks}}',$mysqlName) | ConvertFrom-Json
    if(@($networks.PSObject.Properties.Name).Count -ne 1 -or $networks.PSObject.Properties.Name -ne 'dimabug-qa-mvp-13318-net'){throw 'MySQL network changed'}
    $proxy=Invoke-DockerRead @('inspect','--format','{{json .NetworkSettings.Networks}}','dimabug-qa-mvp-13318-proxy') | ConvertFrom-Json
    $proxyIp=$proxy.'dimabug-qa-mvp-13318-net'.IPAddress
    if($proxyIp -ne '172.21.0.3'){throw 'Proxy origin changed: review before creating account'}
    $uuidFile=Invoke-DockerRead @('exec',$mysqlName,'cat','/var/lib/mysql/auto.cnf')
    $uuidMatch=[regex]::Match($uuidFile,'server-uuid\s*=\s*([a-f0-9-]+)')
    if(-not $uuidMatch.Success){throw 'UUID unavailable'}
    $identity=Invoke-QaSql 'SELECT @@version,@@server_uuid,CURRENT_USER();'
    if($identity -cne ("8.0.46`t"+$uuidMatch.Groups[1].Value+"`troot@localhost")){throw 'SQL identity mismatch'}
    if((Invoke-QaSql "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dima_bug_qa_mvp' AND DEFAULT_CHARACTER_SET_NAME='utf8mb4' AND DEFAULT_COLLATION_NAME='utf8mb4_unicode_ci';") -ne '1'){throw 'QA schema missing'}
    if((Invoke-QaSql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_qa_mvp';") -ne '0'){throw 'Schema not empty'}
    if((Invoke-QaSql "SELECT COUNT(*) FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app');") -ne '0'){throw 'QA account already exists; no overwrite'}
    if((Invoke-QaSql 'SELECT @@global.general_log,@@global.log_bin,@@global.slow_query_log;') -ne "0`t0`t0"){throw 'Query logging requires review'}
    # These two builtin AUDIT-class helpers only maintain authentication caches.
    # Reject all other active AUDIT plugins, including a library-loaded name collision.
    # Oracle: caching-sha2-pluggable-authentication.html, x-plugin-sha2-cache-plugin.html.
    if((Invoke-QaSql "SELECT COUNT(*) FROM information_schema.PLUGINS WHERE PLUGIN_TYPE='AUDIT' AND PLUGIN_STATUS='ACTIVE' AND NOT (PLUGIN_NAME IN ('sha2_cache_cleaner','mysqlx_cache_cleaner') AND PLUGIN_LIBRARY IS NULL);") -ne '0'){throw 'Audit plugin requires review'}
    if((Invoke-QaSql 'SELECT @@global.mandatory_roles;') -ne ''){throw 'Mandatory roles require review'}
    if(-not $Create){Write-Output '{"estado":"CUENTAS_QA_PREFLIGHT_OK","cuentas_creadas":0}';exit 0}
    Add-Type -AssemblyName System.Security.Cryptography.ProtectedData
    $vault=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'DimaBug-QA-MySQL-13318/credentials.dpapi'
    $plain=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($vault),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $credentials=[Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
    if($credentials.format -ne 'dimabug-qa-mysql-credentials-v1' -or $credentials.host -ne '127.0.0.1' -or $credentials.port -ne 13318 -or $credentials.database -ne 'dima_bug_qa_mvp' -or $credentials.schema_user -ne 'dimabug_qa_schema' -or $credentials.app_user -ne 'dimabug_qa_app'){throw 'Custody identity mismatch'}
    foreach($password in @($credentials.schema_password,$credentials.app_password)){
        # Custody format is 32-byte Base64: no SQL quotes, backslashes or control characters.
        if($password -cnotmatch '^[A-Za-z0-9+/]{43}=$' -or [Convert]::FromBase64String($password).Length -ne 32){throw 'Unexpected credential format'}
    }
    if($credentials.schema_password -ceq $credentials.app_password -or $credentials.schema_password -ceq $credentials.root -or $credentials.app_password -ceq $credentials.root){throw 'Credentials must be independent'}
    $template=Get-Content -LiteralPath (Join-Path $PSScriptRoot '15_create_users.sql.template') -Raw
    $text=[regex]::Replace($template,'--[^\n]*','')
    $statements=@($text.Split(';') | Where-Object {$_.Trim()} | ForEach-Object {$_.Trim()+';'})
    $normalized=@($statements | ForEach-Object {[regex]::Replace($_,'\s+',' ')})
    if($statements.Count -ne 2 -or
       $normalized[0] -cne "CREATE USER 'dimabug_qa_schema'@'localhost' IDENTIFIED WITH caching_sha2_password BY {{QA_SCHEMA_PASSWORD_SQL_LITERAL}} WITH MAX_USER_CONNECTIONS 2 ACCOUNT LOCK;" -or
       $normalized[1] -cne "CREATE USER 'dimabug_qa_app'@{{QA_PROXY_HOST_SQL_LITERAL}} IDENTIFIED WITH caching_sha2_password BY {{QA_APP_PASSWORD_SQL_LITERAL}} REQUIRE SSL WITH MAX_USER_CONNECTIONS 4 ACCOUNT LOCK;"){throw 'Template changed: review required'}
    # Render ONLY in memory, then send via stdin to an authenticated local socket client.
    $rendered=$statements[0].Replace('{{QA_SCHEMA_PASSWORD_SQL_LITERAL}}',"'"+$credentials.schema_password+"'")
    $null=Invoke-QaSql $rendered;$completed++
    $rendered=$statements[1].Replace('{{QA_APP_PASSWORD_SQL_LITERAL}}',"'"+$credentials.app_password+"'").Replace('{{QA_PROXY_HOST_SQL_LITERAL}}',"'"+$proxyIp+"'")
    $null=Invoke-QaSql $rendered;$completed++
    $accounts=Invoke-QaSql "SELECT User,Host,account_locked,plugin,ssl_type,max_user_connections FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app') ORDER BY User;"
    $expected="dimabug_qa_app`t172.21.0.3`tY`tcaching_sha2_password`tANY`t4`ndimabug_qa_schema`tlocalhost`tY`tcaching_sha2_password`t`t2"
    if($accounts.Replace("`r`n","`n") -cne $expected){throw 'Account verification failed'}
    $predicate="GRANTEE LIKE '''dimabug_qa_schema''@%' OR GRANTEE LIKE '''dimabug_qa_app''@%'"
    foreach($table in @('USER_PRIVILEGES','SCHEMA_PRIVILEGES','TABLE_PRIVILEGES','COLUMN_PRIVILEGES')){
        $where=if($table -eq 'USER_PRIVILEGES'){"($predicate) AND PRIVILEGE_TYPE <> 'USAGE'"}else{$predicate}
        if((Invoke-QaSql ("SELECT COUNT(*) FROM information_schema."+$table+' WHERE '+$where+';')) -ne '0'){throw 'Unexpected privilege'}
    }
    if((Invoke-QaSql "SELECT COUNT(*) FROM mysql.role_edges WHERE TO_USER IN ('dimabug_qa_schema','dimabug_qa_app');") -ne '0'){throw 'Unexpected role'}
    if((Invoke-QaSql "SELECT COUNT(*) FROM mysql.proxies_priv WHERE User IN ('dimabug_qa_schema','dimabug_qa_app');") -ne '0'){throw 'Unexpected PROXY privilege'}
    if((Invoke-QaSql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_qa_mvp';") -ne '0'){throw 'Schema changed'}
    Write-Output '{"estado":"CUENTAS_QA_BLOQUEADAS_VERIFICADAS","cuentas_creadas":2,"ambas_bloqueadas":true,"credenciales_de_custodia":true,"permisos_funcionales":0,"roles_asignados":0,"app_ssl_obligatorio":true,"tablas":0,"secretos_mostrados":false}'
}catch{
    $known=@('Docker read failed; output suppressed','SQL timeout; do not retry','SQL failed; output suppressed',
        'Nonlocal context','MySQL identity mismatch','Wrong volume','MySQL network changed',
        'Proxy origin changed: review before creating account','UUID unavailable','SQL identity mismatch',
        'QA schema missing','Schema not empty','QA account already exists; no overwrite',
        'Query logging requires review','Audit plugin requires review','Mandatory roles require review',
        'Custody identity mismatch','Unexpected credential format','Credentials must be independent',
        'Template changed: review required','Account verification failed','Unexpected privilege','Unexpected role',
        'Unexpected PROXY privilege','Schema changed')
    $control=if($_.Exception.Message -cin $known){$_.Exception.Message}else{$_.Exception.GetType().Name}
    @{estado='CREACION_CUENTAS_NO_VERIFICADA';control=$control;sentencias_create_completadas=$completed;reintento_automatico=$false;secretos_mostrados=$false} | ConvertTo-Json -Compress
    exit 1
}finally{
    if(Get-Variable plain -ErrorAction SilentlyContinue){[Array]::Clear($plain,0,$plain.Length)}
    $credentials=$null;$password=$null;$rendered=$null;$accounts=$null
    # Close the dedicated process: managed plaintext strings cannot be reliably zeroed.
}
