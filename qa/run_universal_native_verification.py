"""Immutable release verification against disposable native MySQL; no remote access."""
from pathlib import Path
import hashlib,json,os,re,secrets,shutil,subprocess,sys,time,uuid,xml.etree.ElementTree as ET,zipfile
R=Path(__file__).resolve().parents[1]; RELEASE=R/'qa/artifacts/registro-universal-20261115'
V=R/'qa/artifacts/registro-universal-preflight-20261115'
mode=sys.argv[1] if len(sys.argv)>1 else 'concurrency'
assert mode in ['concurrency','race-only','rollback']
database='registro_universal_rollback' if mode=='rollback' else 'registro_universal_native'
IMAGE='mysql@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
PROXY_IMAGE='haproxy:3.2.26-alpine3.24@sha256:5378234d19457d645ed6d5a0ac994ef196614f9a2cabda8f6ccd6ac8815f9098'
tag=uuid.uuid4().hex[:12];container='dimabug-native-check-'+tag;network='dimabug-native-check-'+tag
proxy=container+'-proxy';access=network+'-access'
allowed=['SystemRoot','WINDIR','SystemDrive','USERPROFILE','LOCALAPPDATA','APPDATA','TEMP','TMP','ProgramFiles','ProgramFiles(x86)']
env={k:os.environ[k] for k in allowed if k in os.environ}
env['PATH']=os.pathsep.join([r'C:\Program Files\Docker\Docker\resources\bin',r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin',str(Path(env['SystemRoot'])/'System32'),env['SystemRoot']])
env.update(GMAIL_ENVIO_REAL_HABILITADO='false',GMAIL_OAUTH_HABILITADO='false',DIMABUG_AUTH_CORREO_HABILITADO='false',DIMABUG_AUTOMATIZACION_MODO='DESHABILITADO',DIMABUG_AUTOMATIZACION_SEGUIMIENTO_HABILITADO='false',DIMABUG_CORREO_BIENVENIDA_HABILITADO='false',DIMABUG_CORREO_TICKET_CRITICO_HABILITADO='false')
report={'mode':mode,'release_unchanged':False,'mysql_native':True,'staging_accessed':False,'real_mail':False,'automatic_retry':False,'root_password_persisted':False}
created=False;netcreated=False;proxycreated=False;accesscreated=False
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def docker(args,data=None,timeout=30):return subprocess.run(['docker']+args,input=data,text=True,capture_output=True,encoding='utf-8',errors='replace',env=env,timeout=timeout)
def sql(q):
 p=docker(['exec','-i',container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --batch --skip-column-names --default-character-set=utf8mb4 -uroot '+database],q)
 assert p.returncode==0,'DISPOSABLE_SQL_FAILED';return p.stdout.strip()
V.mkdir(exist_ok=True);(V/'evidence').mkdir(exist_ok=True)
before={n:sha(RELEASE/n) for n in ['artifacts/backend.jar','artifacts/frontend-production.zip','artifacts/sources.zip','artifacts/V20261115_01__registro_universal.sql','release-manifest.json','SHA256SUMS.txt']}
assert before['artifacts/backend.jar']=='1ae8d4dd644ac5f53bfa1bf68f7f84be64e9c60d9ca112d56051cd26a79c2647'
assert before['artifacts/frontend-production.zip']=='aded6a14740376801d3763242c6b204f90d7dacbbf8b8c15061d300ad5ca4d73'
back=V/('native-backend-'+tag);assert not back.exists(),'NO_AUTOMATIC_REPEAT'
source=RELEASE/'sources/dima-bug-backend' if mode!='rollback' else R/'qa/artifacts/staging-auth-isolation-20261114/sources/dima-bug-backend'
testedjar=RELEASE/'artifacts/backend.jar' if mode!='rollback' else R/'qa/artifacts/staging-auth-isolation-20261114/artifacts/backend.jar'
shutil.copytree(source,back,ignore=shutil.ignore_patterns('target','.git'))
for test in (['OldBackendRollbackGuardTest.java','OldBackendValidateRetainedSchemaTest.java'] if mode=='rollback' else ['RegistroUniversalMySqlConcurrencyTest.java']):
 shutil.copy2(R/'qa/verification'/test,back/'src/test/java/cl/casol/backend'/test)
try:
 p=docker(['info','--format','{{.OSType}}/{{.Architecture}}']);assert p.returncode==0 and 'linux/' in p.stdout,'DOCKER_NOT_LOCAL_LINUX'
 p=docker(['image','inspect',IMAGE,'--format','{{.Os}}/{{.Architecture}}']);assert p.returncode==0 and p.stdout.strip()=='linux/amd64','IMAGE_MISMATCH'
 p=docker(['image','inspect',PROXY_IMAGE,'--format','{{.Os}}/{{.Architecture}}']);assert p.returncode==0 and p.stdout.strip()=='linux/amd64','PROXY_IMAGE_MISMATCH'
 p=docker(['network','create','--internal','--label','dimabug.test=universal-native',network]);assert p.returncode==0,'NETWORK_CREATE';netcreated=True
 env['MYSQL_ROOT_PASSWORD']=secrets.token_urlsafe(36)
 p=docker(['run','--pull=never','--rm','--detach','--name',container,'--label','dimabug.test=universal-native','--network',network,'--memory=1g','--tmpfs','/var/lib/mysql:rw,nosuid,size=768m','--tmpfs','/var/run/mysqld:rw,nosuid,size=16m','--env','MYSQL_ROOT_PASSWORD','--env','MYSQL_ROOT_HOST=%','--env','MYSQL_DATABASE='+database,IMAGE,'--innodb-lock-wait-timeout=10','--event-scheduler=OFF','--local-infile=OFF','--secure-file-priv=NULL','--skip-log-bin'],timeout=90)
 assert p.returncode==0,'CONTAINER_CREATE';created=True
 info=json.loads(docker(['inspect',container]).stdout)[0];net=json.loads(docker(['network','inspect',network]).stdout)[0]
 assert net['Internal'] and len(net['Containers'])==1,'INTERNAL_NETWORK'
 assert not any(m['Type'] in ('bind','volume') for m in info['Mounts']),'PERSISTENT_STORAGE'
 assert not info['HostConfig'].get('PortBindings'),'MYSQL_PORT_NOT_PRIVATE'
 ready=False
 for _ in range(55):
  p=docker(['exec',container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '+database+' --batch --skip-column-names -e "SELECT 1"'],timeout=8)
  if p.returncode==0:ready=True;break
  time.sleep(1)
 assert ready,'MYSQL_NOT_READY'
 # Reuse the already verified QA proxy architecture, with independent disposable names/config.
 cfg=V/('haproxy-'+tag+'.cfg')
 cfg.write_text((R/'qa/mysql/haproxy.qa-local.cfg').read_text().replace('bind :13318','bind :3310').replace('server mysql mysql:3306','server mysql '+container+':3306'),encoding='utf-8')
 p=docker(['network','create','--label','dimabug.test=universal-native',access]);assert p.returncode==0,'ACCESS_NETWORK_CREATE';accesscreated=True
 p=docker(['run','--pull=never','--rm','--detach','--name',proxy,'--label','dimabug.test=universal-native','--network',access,'--network',network,'--publish','127.0.0.1::3310','--user','65534:65534','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges:true','--sysctl','net.ipv4.ip_forward=0','--memory','128m','--pids-limit','64','--mount','type=bind,src='+str(cfg.resolve())+',dst=/usr/local/etc/haproxy/haproxy.cfg,readonly','--entrypoint','haproxy',PROXY_IMAGE,'-db','-f','/usr/local/etc/haproxy/haproxy.cfg'],timeout=45)
 assert p.returncode==0,'PROXY_CREATE';proxycreated=True
 pi=json.loads(docker(['inspect',proxy]).stdout)[0]
 assert set(pi['NetworkSettings']['Networks'])=={access,network},'PROXY_NETWORKS'
 assert pi['HostConfig']['ReadonlyRootfs'] and pi['HostConfig']['CapDrop']==['ALL'] and pi['HostConfig']['Sysctls']['net.ipv4.ip_forward']=='0','PROXY_ISOLATION'
 bindings=pi['NetworkSettings']['Ports']['3310/tcp'];assert len(bindings)==1 and bindings[0]['HostIp']=='127.0.0.1','LOOPBACK_BIND'
 port=int(bindings[0]['HostPort'])
 server_uuid=sql('SELECT @@server_uuid;');assert re.fullmatch('[a-f0-9-]{36}',server_uuid),'SERVER_IDENTITY'
 report.update(mysql_version=sql('SELECT VERSION();'),image=IMAGE,proxy_image=PROXY_IMAGE,internal_network_verified=True,loopback_only_verified=True,persistent_mysql_volumes=False,reused_qa_resources=False)
 url=f'jdbc:mysql://127.0.0.1:{port}/{database}?sslMode=REQUIRED&connectionTimeZone=UTC'
 env.update(UNIVERSAL_TEST_DB_URL=url,UNIVERSAL_ROLLBACK_DB_URL=url,UNIVERSAL_TEST_DB_PASSWORD=env.pop('MYSQL_ROOT_PASSWORD'),UNIVERSAL_TEST_SERVER_UUID=server_uuid,UNIVERSAL_MIGRATION_FILE=str(RELEASE/'artifacts/V20261115_01__registro_universal.sql'),UNIVERSAL_QUARANTINE_FILE=str(R/'qa/verification/rollback_quarantine.sql'))
 m=Path(env['USERPROFILE'])/'.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn.cmd'
 agent=Path(env['USERPROFILE'])/'.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
 selector='OldBackendRollbackGuardTest,OldBackendValidateRetainedSchemaTest' if mode=='rollback' else 'RegistroUniversalMySqlConcurrencyTest'+('#confirmacionPrimeroNoReviveCuentaTrasBloqueoConcurrente' if mode=='race-only' else '')
 command=[str(m),'-o','-B','-ntp','-f','pom-release-isolated.xml','test','-DargLine=-javaagent:'+str(agent),'-Dtest='+selector,'-Dsurefire.runOrder=alphabetical','-Dlogging.level.root=OFF','-Dspring.main.banner-mode=off']
 print(json.dumps({'phase':'NATIVE_MYSQL_READY_ISOLATED','mail_disabled':True,'release_untouched':True}),flush=True)
 p=subprocess.run(command,cwd=back,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=480)
 report['maven_exit']=p.returncode;report['cases']=[]
 report['totals']={k:0 for k in ['tests','errors','failures','skipped']}
 for f in (back/'target/surefire-reports').glob('TEST-*.xml'):
  root=ET.parse(f).getroot()
  if root.attrib['name'].rsplit('.',1)[-1] not in selector.split('#')[0].split(','):continue
  for k in report['totals']:report['totals'][k]+=int(root.attrib[k])
  for case in root.findall('testcase'):
   error=case.find('error');failure=case.find('failure');issue=error if error is not None else failure
   item={'name':case.attrib['name'],'passed':issue is None}
   if issue is not None:
    text=(issue.text or '')
    item.update(error_type=issue.attrib.get('type'),deadlock='Deadlock' in text or 'deadlock' in text,lock_timeout='Lock wait timeout' in text,sql_state=re.findall(r'SQLState[^0-9A-Z]*([0-9A-Z]{5})',text)[:3])
   report['cases'].append(item)
 with zipfile.ZipFile(testedjar) as z:
  classes={n.removeprefix('BOOT-INF/classes/'):hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith('BOOT-INF/classes/') and n.endswith('.class')}
 report['production_classes_identical_to_release']=all((back/'target/classes'/n).is_file() and sha(back/'target/classes'/n)==h for n,h in classes.items())
 report['tested_jar_sha256']=sha(testedjar)
 report['verified']=p.returncode==0 and report['production_classes_identical_to_release']
except Exception as ex:
 report.update(verified=False,failure_type=type(ex).__name__,failure_code=str(ex) if re.fullmatch('[A-Z_]+',str(ex)) else 'SUPPRESSED')
finally:
 if proxycreated:
  tagcheck=docker(['inspect',proxy,'--format','{{index .Config.Labels "dimabug.test"}}'])
  report['test_proxy_removed']=tagcheck.returncode==0 and tagcheck.stdout.strip()=='universal-native' and docker(['rm','--force',proxy]).returncode==0
 else:report['test_proxy_removed']=True
 if created:
  tagcheck=docker(['inspect',container,'--format','{{index .Config.Labels "dimabug.test"}}'])
  report['test_container_removed']=tagcheck.returncode==0 and tagcheck.stdout.strip()=='universal-native' and docker(['rm','--force',container]).returncode==0
 else:report['test_container_removed']=True
 if netcreated:
  tagcheck=docker(['network','inspect',network,'--format','{{index .Labels "dimabug.test"}}'])
  report['test_network_removed']=tagcheck.returncode==0 and tagcheck.stdout.strip()=='universal-native' and docker(['network','rm',network]).returncode==0
 else:report['test_network_removed']=True
 if accesscreated:
  tagcheck=docker(['network','inspect',access,'--format','{{index .Labels "dimabug.test"}}'])
  report['test_access_network_removed']=tagcheck.returncode==0 and tagcheck.stdout.strip()=='universal-native' and docker(['network','rm',access]).returncode==0
 else:report['test_access_network_removed']=True
 report['release_unchanged']=all(sha(RELEASE/n)==h for n,h in before.items())
 report['verified']=report.get('verified',False) and report['release_unchanged'] and all(report[k] for k in ['test_container_removed','test_proxy_removed','test_network_removed','test_access_network_removed'])
 (V/('evidence/native-'+mode+'-'+tag+'.json')).write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
 (V/('evidence/native-'+mode+'.json')).write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
 print(json.dumps(report))
raise SystemExit(0 if report['verified'] else 1)
