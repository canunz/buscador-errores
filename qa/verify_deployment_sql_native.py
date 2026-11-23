from pathlib import Path
import sys,subprocess,json,time,uuid,re,os
sys.path.insert(0,str(Path(__file__).parent/'verification'))
from deployment_sql_session import SqlSession,quarantine,SqlSessionError
R=Path(__file__).resolve().parents[1];V=R/'qa/artifacts/registro-universal-deploy-20261115'
name='dimabug-deploy-guard-'+uuid.uuid4().hex[:12];db='universal_guard_test'
image='mysql@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
def docker(a,data=None,timeout=30):return subprocess.run(['docker']+a,input=data,capture_output=True,text=True,encoding='utf-8',timeout=timeout)
client=['docker','exec','-i',name,'mysql','--protocol=socket','--user=root','--skip-reconnect','--skip-force','--unbuffered','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database='+db]
def sql(q):
 p=subprocess.run(client,input=q,text=True,capture_output=True,timeout=30);assert p.returncode==0,'SQL_SETUP_FAILED';return p.stdout.strip()
report={'native_mysql':True,'staging_accessed':False,'real_credentials_used':False,'real_mail':False};created=False
try:
 p=docker(['run','--pull=never','--rm','--detach','--name',name,'--label','dimabug.test=deployment-guard','--network=none','--memory=1g','--tmpfs','/var/lib/mysql:rw,nosuid,size=768m','--tmpfs','/var/run/mysqld:rw,nosuid,size=16m','--env','MYSQL_ALLOW_EMPTY_PASSWORD=yes','--env','MYSQL_DATABASE='+db,image],timeout=90)
 assert p.returncode==0,'CONTAINER_START_FAILED';created=True
 ready=False
 for _ in range(55):
  p=docker(['exec',name,'mysql','-uroot',db,'-e','SELECT 1'],timeout=8)
  if p.returncode==0:ready=True;break
  time.sleep(1)
 assert ready,'MYSQL_NOT_READY';server=sql('SELECT @@server_uuid;')
 sql("""CREATE TABLE u_usuario(usuario_id INT PRIMARY KEY,usuario_estado BOOLEAN,registro_estado VARCHAR(24),password_hash VARCHAR(80)) ENGINE=InnoDB;
 INSERT INTO u_usuario VALUES(1,1,'ADMINISTRADO','intacto'),(2,1,'AUTO_CONFIRMADO','intacto'),(3,0,'AUTO_PENDIENTE','intacto'),(4,0,'AUTO_CANCELADO','intacto');
 CREATE TABLE u_codigo_acceso(codigo_id INT PRIMARY KEY,usuario_id INT,usado BOOLEAN) ENGINE=InnoDB;
 INSERT INTO u_codigo_acceso VALUES(1,2,0);
 CREATE TABLE u_correo_codigo(id INT PRIMARY KEY,codigo_id INT,estado VARCHAR(24),contenido_cifrado TEXT,actualizado_en DATETIME) ENGINE=InnoDB;
 INSERT INTO u_correo_codigo VALUES(1,1,'PENDIENTE','solo-sintetico',NOW());""")
 raw=(R/'qa/verification/rollback_quarantine.sql').read_text(encoding='utf-8')
 plan=[x.strip() for x in re.sub(r'(?m)^--.*$','',raw).split(';') if x.strip() and x.strip().upper()!='START TRANSACTION']
 markers=[]
 with SqlSession(client) as s:
  cid=s.query('SELECT CONNECTION_ID()')
  quarantine(s,db,server,plan,lambda:markers.append('attempt'),lambda:markers.append('confirmed'))
  assert s.query('SELECT CONNECTION_ID()')==cid,'CONNECTION_CHANGED'
 assert markers==['attempt','confirmed'],'COMMIT_NOT_CONFIRMED'
 assert sql("SELECT COUNT(*) FROM u_usuario WHERE registro_estado<>'ADMINISTRADO' AND usuario_estado<>0;")=='0','QUARANTINE_FAILED'
 assert sql("SELECT CONCAT(usuario_estado,':',password_hash) FROM u_usuario WHERE usuario_id=1;")=='1:intacto','MANAGED_CHANGED'
 assert sql('SELECT COUNT(*) FROM u_correo_codigo WHERE contenido_cifrado IS NOT NULL;')=='0','PAYLOAD_NOT_CLEARED'
 report.update(single_connection_commit_confirmed=True,managed_preserved=True,auto_accounts_blocked=True,codes_invalidated_and_cipher_cleared=True)
 sql("UPDATE u_usuario SET usuario_estado=1,registro_estado='AUTO_CONFIRMADO' WHERE usuario_id=2; UPDATE u_codigo_acceso SET usado=0;")
 markers=[]
 try:
  with SqlSession(client) as s:quarantine(s,db,server,plan+['SELECT COUNT(*) FROM u_usuario WHERE usuario_id=1'],lambda:markers.append('attempt'),lambda:markers.append('confirmed'))
  raise AssertionError('BAD_ASSERTION_DID_NOT_FAIL')
 except SqlSessionError as e:assert str(e)=='QUARANTINE_ASSERTION_FAILED'
 assert markers==[] and sql('SELECT usuario_estado FROM u_usuario WHERE usuario_id=2;')=='1' and sql('SELECT usado FROM u_codigo_acceso;')=='0','ROLLBACK_FAILED'
 report.update(precommit_assertion_rolls_back=True,verified=True,mysql_version=sql('SELECT VERSION();'))
except BaseException as e:report.update(verified=False,failure_type=type(e).__name__,failure_code=str(e) if re.fullmatch('[A-Z_]+',str(e)) else 'SUPPRESSED')
finally:
 if created:
  check=docker(['inspect',name,'--format','{{index .Config.Labels "dimabug.test"}}'])
  report['disposable_removed']=check.returncode==0 and check.stdout.strip()=='deployment-guard' and docker(['rm','-f',name]).returncode==0
 else:report['disposable_removed']=True
 report['verified']=report.get('verified',False) and report['disposable_removed']
 (V/'native-rollback-executor-tests.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
raise SystemExit(0 if report['verified'] else 1)
