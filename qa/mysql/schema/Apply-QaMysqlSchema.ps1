#requires -Version 7.4
# Approved phase: temporary admin unlock, final DDL and control(1), guaranteed relock attempt.
[CmdletBinding()]
param([switch]$Apply)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$mysqlName='dimabug-qa-mvp-13318-mysql'
$completed=0;$lockNeeded=$false;$relocked=$false;$verified=$false;$control='UNSPECIFIED'
function Sql([string]$Text,[switch]$Admin){
    $info=[Diagnostics.ProcessStartInfo]::new('docker')
    $info.UseShellExecute=$false;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $command=if($Admin){'IFS= read -r MYSQL_PWD || exit 1; export MYSQL_PWD; export MYSQL_HISTFILE=/dev/null; exec mysql --protocol=socket --user=dimabug_qa_schema --database=dima_bug_qa_mvp --batch --raw --skip-column-names --connect-timeout=5'}else{'MYSQL_HISTFILE=/dev/null MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql --protocol=socket --user=root --batch --raw --skip-column-names --connect-timeout=5'}
    foreach($arg in @('--context','desktop-linux','exec','-i',$mysqlName,'sh','-c',$command)){$info.ArgumentList.Add($arg)}
    $process=[Diagnostics.Process]::Start($info)
    $out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync()
    try{
        # Linux read preserves a trailing CR. Use LF explicitly for the password framing.
        $process.StandardInput.NewLine="`n"
        if($Admin){$process.StandardInput.WriteLine($credentials.schema_password)}
        $process.StandardInput.WriteLine($Text);$process.StandardInput.Close()
        if(-not $process.WaitForExit(30000)){$process.Kill();throw 'SQL_TIMEOUT'}
        $output=$out.GetAwaiter().GetResult();$stderr=$err.GetAwaiter().GetResult()
        if($process.ExitCode -ne 0){
            $match=[regex]::Match($stderr,'ERROR (\d+)')
            if($match.Success){throw ('SQL_ERROR_'+$match.Groups[1].Value)}
            throw 'SQL_OPERATION_FAILED'
        }
        return $output.Trim()
    }finally{$process.Dispose()}
}
try{
    & python -I -B (Join-Path $PSScriptRoot 'verify_schema_proposal.py')
    if($LASTEXITCODE -ne 0){throw 'STATIC_GATE_FAILED'}
    # Read-only helper checks locked accounts, empty schema and absence of app/global permissions.
    $preflight=@'
import importlib.util,json,tarfile,re
from pathlib import Path
p=Path('qa/mysql/schema/apply_admin_grants.py').resolve()
s=importlib.util.spec_from_file_location('gate',p);m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
assert json.loads(m.docker(['context','inspect','desktop-linux','--format','{{json .Endpoints.docker.Host}}'])).startswith('npipe://')
assert json.loads(m.docker(['inspect','--format','{{json .Config.Image}}',m.NAME]))=='mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
mounts=json.loads(m.docker(['inspect','--format','{{json .Mounts}}',m.NAME]))
assert any(x.get('Name')=='dimabug-qa-mvp-13318-mysql-data' and x['Destination']=='/var/lib/mysql' for x in mounts)
a=max(Path('qa/runtime/backups').glob('pre-proxy-*/mysql-data.tar'),key=lambda p:p.parent.name)
with tarfile.open(a) as t:
 e=next(e for e in t if e.name.endswith('/auto.cnf') or e.name=='auto.cnf');u=re.search(rb'server-uuid\s*=\s*([a-f0-9-]+)',t.extractfile(e).read())[1].decode()
assert m.sql('SELECT @@version,@@server_uuid,CURRENT_USER();')=='8.0.46\t'+u+'\troot@localhost'
m.check_unchanged()
allow=json.loads((p.parent/'privileges.json').read_text())
expected={("'dimabug_qa_schema'@'localhost'",'dima_bug_qa_mvp',n,v,'NO') for n in allow['app']['per_table'] for v in allow['admin']['per_table']}
assert {tuple(x.split('\t')) for x in m.privilege_rows('TABLE_PRIVILEGES','dimabug_qa_schema').splitlines()}==expected
print(json.dumps({'estado':'ESQUEMA_PREFLIGHT_OK','tablas':0,'admin_grants_exactos':True,'ambas_cuentas_bloqueadas':True}))
'@
    Push-Location ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..')))
    try{$preflight | & python -I -B -;if($LASTEXITCODE -ne 0){throw 'READ_ONLY_GATE_FAILED'}}finally{Pop-Location}
    if(-not $Apply){exit 0}
    Add-Type -AssemblyName System.Security.Cryptography.ProtectedData
    $vault=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'DimaBug-QA-MySQL-13318/credentials.dpapi'
    $plain=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($vault),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $credentials=[Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
    if($credentials.database -ne 'dima_bug_qa_mvp' -or $credentials.port -ne 13318 -or $credentials.schema_user -ne 'dimabug_qa_schema' -or $credentials.schema_password -cnotmatch '^[A-Za-z0-9+/]{43}=$'){throw 'CUSTODY_MISMATCH'}
    $source=Get-Content -LiteralPath (Join-Path $PSScriptRoot '20_schema_final.sql') -Raw
    $statements=@(([regex]::Replace($source,'--[^\n]*','')).Split(';') | Where-Object {$_.Trim()} | ForEach-Object {$_.Trim()+';'})
    if($statements.Count -ne 45 -or $statements[0] -cne 'USE `dima_bug_qa_mvp`;'){throw 'DDL_CHANGED'}
    foreach($statement in $statements[1..44]){if($statement -cnotmatch '^CREATE TABLE \w+\s*\('){throw 'DDL_CHANGED'}}
    # Empty-schema recovery checkpoint: structure only, no credentials or account hashes.
    $root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
    $backupRoot=Join-Path $root 'qa/runtime/backups'
    $checkpoint=Join-Path $backupRoot ('pre-schema-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
    $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User;$acl.SetOwner($sid)
    foreach($id in @($sid,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($id,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
    $null=[IO.FileSystemAclExtensions]::CreateDirectory($acl,$checkpoint)
    & git -C $root check-ignore --quiet -- (Join-Path $checkpoint 'empty-schema.sql')
    if($LASTEXITCODE -ne 0){throw 'CHECKPOINT_NOT_IGNORED'}
    $ddl=Sql 'SHOW CREATE DATABASE dima_bug_qa_mvp;'
    $ddl | Set-Content -LiteralPath (Join-Path $checkpoint 'empty-schema.sql') -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '18_admin_grants.sql') -Destination $checkpoint
    Write-Output '{"estado":"CHECKPOINT_ESQUEMA_VACIO_GUARDADO","acl_restringida":true,"sin_datos_ni_credenciales":true}'
    $lockNeeded=$true
    $null=Sql "ALTER USER 'dimabug_qa_schema'@'localhost' ACCOUNT UNLOCK;"
    if((Sql 'SELECT CURRENT_USER();' -Admin) -cne 'dimabug_qa_schema@localhost'){throw 'ADMIN_IDENTITY_MISMATCH'}
    foreach($statement in $statements[1..44]){
        $null=Sql $statement -Admin;$completed++
        if($completed % 10 -eq 0){@{estado='DDL_QA_EN_CURSO';tablas_creadas=$completed} | ConvertTo-Json -Compress}
    }
    $seed=Get-Content -LiteralPath (Join-Path $PSScriptRoot '25_structural_rows.sql') -Raw
    $seedText=[regex]::Replace($seed,'--[^\n]*','')
    $normalized=[regex]::Replace($seedText,'\s+',' ').Trim()
    if($normalized -cne 'USE `dima_bug_qa_mvp`; INSERT INTO u_organizacion_control(control_id) VALUES(1);'){throw 'SEED_CHANGED'}
    $null=Sql $seedText -Admin
    & python -I -B (Join-Path $PSScriptRoot 'verify_schema_runtime.py')
    if($LASTEXITCODE -ne 0){throw 'RUNTIME_MODEL_CHECK_FAILED'}
    if((Sql "SELECT COUNT(*) FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE LIKE '''dimabug_qa_app''@%';") -ne '0'){throw 'APP_PERMISSIONS_CHANGED'}
    $verified=$true
}catch{
    $message=$_.Exception.Message
    $control=if($message -cmatch '^[A-Z_0-9]+$'){$message}else{$_.Exception.GetType().Name}
}finally{
    if($lockNeeded){
        try{
            $null=Sql "ALTER USER 'dimabug_qa_schema'@'localhost' ACCOUNT LOCK;"
            $relocked=(Sql "SELECT COUNT(*) FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app') AND account_locked='Y';") -eq '2'
        }catch{$relocked=$false}
    }
    if(Get-Variable plain -ErrorAction SilentlyContinue){[Array]::Clear($plain,0,$plain.Length)}
    $credentials=$null
}
if($verified -and $relocked){
    Write-Output '{"estado":"ESQUEMA_QA_APLICADO_Y_VERIFICADO","tablas":44,"fila_control":true,"ambas_cuentas_bloqueadas":true,"app_sin_permisos":true,"secretos_mostrados":false,"hibernate_no_iniciado":true}'
}else{
    @{estado='ESQUEMA_QA_NO_COMPLETADO';control=$control;create_completados=$completed;ambas_cuentas_bloqueadas=$relocked;reintento_automatico=$false;secretos_mostrados=$false} | ConvertTo-Json -Compress
    exit 1
}
