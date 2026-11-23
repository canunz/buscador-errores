"""Exercise the exact recovery-probe implementation against disposable synthetic MySQL."""
from pathlib import Path
import sys,subprocess,json,time,uuid,importlib.util,re,hashlib
R=Path(__file__).resolve().parents[1];V=R/'qa/artifacts/registro-universal-deploy-20261115'
sys.path[:0]=[str(V/'payload/tools'),str(R/'qa/verification')]
spec=importlib.util.spec_from_file_location('tested_deployment',R/'qa/verification/deploy_universal.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
name='dimabug-restore-check-'+uuid.uuid4().hex[:12];db='dima_bug_staging'
image='mysql@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
def docker(a,data=None,timeout=30):return subprocess.run(['docker']+a,input=data,capture_output=True,timeout=timeout)
def client(database=db):return ['docker','exec','-i',name,'mysql','--protocol=socket','--user=root','--skip-reconnect','--skip-force','--unbuffered','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database='+database]
def run(args,timeout=30,input=None,**unused):
 assert args[:3]==['docker','exec','-i'],'NON_DISPOSABLE_COMMAND'
 p=subprocess.run(args,input=input,capture_output=True,timeout=timeout);assert p.returncode==0,'DISPOSABLE_SQL_FAILED';return p.stdout
def sql(q,database=db):return [x.split('\t') for x in run(client(database),input=q.encode('utf-8')).decode('utf-8').splitlines()]
report={'real_staging_accessed':False,'real_data_used':False,'real_mail':False};created=False
try:
 p=docker(['run','--pull=never','--rm','--detach','--name',name,'--label','dimabug.test=restore-probe','--network=none','--memory=1g','--tmpfs','/var/lib/mysql:rw,nosuid,size=768m','--tmpfs','/var/run/mysqld:rw,nosuid,size=16m','--env','MYSQL_ALLOW_EMPTY_PASSWORD=yes','--env','MYSQL_DATABASE='+db,image],timeout=90)
 assert p.returncode==0,'CONTAINER_START';created=True
 ready=False
 for _ in range(55):
  p=docker(['exec',name,'mysql','-uroot',db,'-e','SELECT 1'],timeout=8)
  if p.returncode==0:ready=True;break
  time.sleep(1)
 assert ready,'MYSQL_NOT_READY'
 sql("CREATE TABLE parent(id INT PRIMARY KEY) ENGINE=InnoDB; INSERT INTO parent VALUES(1); CREATE TABLE example(id INT PRIMARY KEY,parent_id INT,nombre VARCHAR(100),contenido LONGBLOB,fecha DATETIME(6),FOREIGN KEY(parent_id) REFERENCES parent(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4; INSERT INTO example VALUES(1,1,'Prueba sintética áéíóú Ñ',0x0001FF,CURRENT_TIMESTAMP(6));")
 folder=V/('restore-fixture-'+uuid.uuid4().hex[:8]);folder.mkdir()
 p=docker(['exec',name,'mysqldump','--protocol=socket','--user=root','--default-character-set=utf8mb4','--single-transaction','--skip-lock-tables','--no-tablespaces','--set-gtid-purged=OFF','--skip-extended-insert','--complete-insert','--order-by-primary','--hex-blob','--routines','--events','--triggers','--skip-dump-date','--databases',db])
 assert p.returncode==0,'DUMP_FAILED';(folder/'database.sql').write_bytes(p.stdout)
 m.FOLDER=folder;m.client=client;m.run=run;m.sql=sql;m.d.prep.sql=sql
 m.atomic=lambda p,data,**kw:p.write_bytes(data)
 meta=m.d.prep.metadata();baseline={'schema':meta,'data':m.d.prep.data_state(meta)}
 assert m.restore_probe({'restore_schema':'dima_bug_staging_restore_'+uuid.uuid4().hex[:12]},baseline),'RESTORE_NOT_VERIFIED'
 assert m.d.prep.data_state(meta)==baseline['data'],'SOURCE_CHANGED'
 report.update(exact_recovery_probe_verified=True,utf8_and_binary_preserved=True,foreign_key_schema_verified=True,source_unchanged=True,temporary_schema_removed=True,verified=True,mysql_version=sql('SELECT VERSION()')[0][0])
except BaseException as e:report.update(verified=False,failure_type=type(e).__name__,failure_code=str(e) if re.fullmatch('[A-Z_]+',str(e)) else 'SUPPRESSED')
finally:
 if created:
  check=docker(['inspect',name,'--format','{{index .Config.Labels "dimabug.test"}}'])
  report['disposable_removed']=check.returncode==0 and check.stdout.strip()==b'restore-probe' and docker(['rm','-f',name]).returncode==0
 else:report['disposable_removed']=True
 report['verified']=report.get('verified',False) and report['disposable_removed']
 (V/'native-restore-probe-tests.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
raise SystemExit(0 if report['verified'] else 1)
