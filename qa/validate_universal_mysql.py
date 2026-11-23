"""Native DDL validation on a disposable, networkless MySQL. No existing DB credentials."""
from pathlib import Path
import hashlib,json,os,re,subprocess,time,uuid
R=Path(__file__).resolve().parents[1]
A=R/'qa/artifacts/registro-universal-20261115'
IMAGE='mysql@sha256:a532724022429812ec797c285c1b540a644c15e248579c6bfdf12a8fbaab4964'
name='dimabug-universal-test-'+uuid.uuid4().hex[:12]
env={k:os.environ[k] for k in ['SystemRoot','WINDIR','SystemDrive','USERPROFILE','LOCALAPPDATA','APPDATA','TEMP','TMP','ProgramFiles','ProgramFiles(x86)','PATH'] if k in os.environ}
report={'native_mysql':True,'disposable':True,'network':'none','published_ports':False,'existing_databases_accessed':False,'oauth_accessed':False,'real_mail':False,'image':IMAGE}
created=False
def run(args,data=None,timeout=45):
 return subprocess.run(['docker']+args,input=data,capture_output=True,text=True,encoding='utf-8',errors='replace',env=env,timeout=timeout)
def sql(q,ok=True):
 p=run(['exec','-i',name,'mysql','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','-uroot','registro_universal_test'],q)
 assert (p.returncode==0)==ok,'SQL_VALIDATION_FAILED'
 return p.stdout.strip()
try:
 p=run(['image','inspect',IMAGE,'--format','{{.Os}}/{{.Architecture}}'])
 assert p.returncode==0 and p.stdout.strip()=='linux/amd64','IMAGE_ARCHITECTURE'
 p=run(['run','--pull=never','--detach','--rm','--name',name,'--label','dimabug.test=registro-universal',
        '--network=none','--memory=1g','--tmpfs','/var/lib/mysql:rw,nosuid,size=768m',
        '--tmpfs','/var/run/mysqld:rw,nosuid,size=16m',
        '--env','MYSQL_ALLOW_EMPTY_PASSWORD=yes','--env','MYSQL_DATABASE=registro_universal_test',IMAGE],timeout=90)
 assert p.returncode==0,'CONTAINER_START'; created=True
 info=json.loads(run(['inspect',name]).stdout)[0]
 assert info['HostConfig']['NetworkMode']=='none' and not info['HostConfig'].get('PortBindings'),'NETWORK_ISOLATION'
 assert not any(m['Type'] in ('bind','volume') for m in info['Mounts']),'STORAGE_ISOLATION'
 ready=False
 for _ in range(55):
  p=run(['exec',name,'mysql','--batch','--skip-column-names','-uroot','registro_universal_test','-e','SELECT 1'],timeout=8)
  if p.returncode==0: ready=True;break
  time.sleep(1)
 assert ready,'MYSQL_NOT_READY'
 report['mysql_version']=sql('SELECT VERSION();')
 sql('''CREATE TABLE u_rol(rol_id INT PRIMARY KEY,rol_nombre VARCHAR(40),rol_estado BOOLEAN);
 INSERT INTO u_rol VALUES(1,'ADMINISTRADOR',true),(4,'USUARIO',true);
 CREATE TABLE u_usuario(usuario_id INT AUTO_INCREMENT PRIMARY KEY,rol_id INT NOT NULL,
 usuario_nombre VARCHAR(100) NOT NULL,usuario_email VARCHAR(120) NOT NULL UNIQUE,
 usuario_password_hash VARCHAR(255) NOT NULL,usuario_estado BOOLEAN NOT NULL,
 usuario_fecha_creacion DATETIME NOT NULL,usuario_fecha_actualizacion DATETIME NOT NULL,
 FOREIGN KEY(rol_id) REFERENCES u_rol(rol_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 INSERT INTO u_usuario VALUES(1,1,'Administrado','legacy@example.invalid','hash-sintetico',1,NOW(),NOW()),
 (2,4,'Bloqueado','blocked@example.invalid','hash-sintetico-bloqueado',0,NOW(),NOW());''')
 baseline="SELECT SHA2(GROUP_CONCAT(CONCAT_WS('|',usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) ORDER BY usuario_id),256) FROM u_usuario WHERE usuario_id IN (1,2);"
 before=sql(baseline)
 migration=(R/'dima-bug-backend/src/main/resources/db/migration/V20261115_01__registro_universal.sql').read_text(encoding='utf-8')
 sql(migration)
 assert sql(baseline)==before,'LEGACY_CHANGED'
 assert sql("SELECT COUNT(*) FROM u_usuario WHERE registro_estado='ADMINISTRADO';")=='2','LEGACY_ORIGIN'
 report['exact_migration_sha256']=hashlib.sha256(migration.encode()).hexdigest()
 report['legacy_roles_states_hashes_unchanged']=True
 sql("UPDATE u_usuario SET registro_estado='AUTO_PENDIENTE' WHERE usuario_id=1;",False)
 sql("UPDATE u_usuario SET registro_estado='INVALIDO' WHERE usuario_id=2;",False)
 report['state_checks_enforced']=True
 sql("INSERT INTO u_usuario(rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion,registro_estado) VALUES(4,'Solicitante','nuevo@example.invalid','hash-no-utilizable',0,NOW(),NOW(),'AUTO_PENDIENTE');")
 sql("INSERT INTO u_usuario(rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) VALUES(4,'Duplicado',' NUEVO@EXAMPLE.INVALID ','hash-no-utilizable',0,NOW(),NOW());",False)
 report['normalized_unique_index_enforced']=True
 sql("START TRANSACTION; UPDATE u_usuario SET registro_estado='AUTO_CONFIRMADO',usuario_estado=1 WHERE usuario_id=3; ROLLBACK;")
 assert sql("SELECT CONCAT(registro_estado,':',usuario_estado) FROM u_usuario WHERE usuario_id=3;")=='AUTO_PENDIENTE:0','ROLLBACK_FAILED'
 report['rollback_preserves_pending']=True
 assert sql("SELECT @@character_set_client;")=='utf8mb4','CHARSET'
 report['utf8mb4']=True
 report['native_application_concurrency_test']=False
 report['status']='NATIVE_MIGRATION_VERIFIED'
except Exception as ex:
 report.update(status='NOT_VERIFIED',failure_type=type(ex).__name__,detail=str(ex) if re.fullmatch('[A-Z_]+',str(ex)) else 'SUPPRESSED')
finally:
 if created:
  p=run(['inspect',name,'--format','{{index .Config.Labels "dimabug.test"}}'])
  if p.returncode==0 and p.stdout.strip()=='registro-universal':
   p=run(['rm','--force',name]);report['disposable_container_removed']=p.returncode==0
  else: report['disposable_container_removed']=False
 else:report['disposable_container_removed']=True
 report['verified']=report.get('status')=='NATIVE_MIGRATION_VERIFIED' and report['disposable_container_removed']
 (A/'evidence/native-mysql-migration.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
 print(json.dumps(report))
raise SystemExit(0 if report['verified'] else 1)
