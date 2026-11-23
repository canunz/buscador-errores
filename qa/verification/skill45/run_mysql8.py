"""Dedicated disposable MySQL 8 validation; never connect to an existing database/server."""
from pathlib import Path
import os,sys,subprocess,json,secrets,hashlib,uuid,re,time,shutil,ssl,gzip,traceback,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[3]
TAG=uuid.uuid4().hex[:12]; DB='s45_ticket_'+TAG
OUT=R/'qa/artifacts/skill45-mysql8'/TAG;OUT.mkdir(parents=True,exist_ok=False)
NAME='dimabug-s45-ticket-'+TAG; PROXY=NAME+'-proxy'; NET=NAME+'-internal'; ACCESS=NAME+'-access'
IMAGE='mysql@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
PROXY_IMAGE='haproxy@sha256:5378234d19457d645ed6d5a0ac994ef196614f9a2cabda8f6ccd6ac8815f9098'
DOCKER=shutil.which('docker');assert DOCKER,'DOCKER_CLI_MISSING'
allowed=['SystemRoot','WINDIR','SystemDrive','USERPROFILE','LOCALAPPDATA','APPDATA','TEMP','TMP','ProgramFiles','ProgramFiles(x86)']
env={k:os.environ[k] for k in allowed if k in os.environ}
JDK=Path(r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin')
env['PATH']=os.pathsep.join([str(Path(DOCKER).parent),str(JDK),str(Path(env['SystemRoot'])/'System32'),env['SystemRoot']])
env.update(GMAIL_ENVIO_REAL_HABILITADO='false',GMAIL_OAUTH_HABILITADO='false',DIMABUG_AUTH_CORREO_HABILITADO='false',DIMABUG_AUTOMATIZACION_MODO='DESHABILITADO',DIMABUG_AUTOMATIZACION_SEGUIMIENTO_HABILITADO='false',DIMABUG_CORREO_BIENVENIDA_HABILITADO='false',DIMABUG_CORREO_TICKET_CRITICO_HABILITADO='false')
root_password=secrets.token_urlsafe(36);password=secrets.token_urlsafe(36);jwt=secrets.token_urlsafe(64)
report={'run_id':TAG,'database':DB,'mysql_image':IMAGE,'shared_databases_accessed':False,'staging_accessed':False,'production_accessed':False,'real_data':False,'emails_sent':0,'oauth_modified':False,'automatic_retry':False}
phase='LOCAL_PREFLIGHT'; source_hashes={}; original_containers=set();original_volumes=set();original_networks=set()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def docker(args,data=None,timeout=35):
 return subprocess.run([DOCKER]+args,input=data,text=True,capture_output=True,encoding='utf-8',errors='replace',env=env,timeout=timeout)
def require(p,code):
 if p.returncode:raise RuntimeError(code)
 return p.stdout.strip()
def sql(q,database=DB):
 assert re.fullmatch('s45_ticket_[a-f0-9]{12}(_restore)?',database),'DATABASE_GUARD'
 p=docker(['exec','-i',NAME,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket --batch --raw --skip-column-names --skip-reconnect --skip-force --default-character-set=utf8mb4 -uroot '+database],q)
 if p.returncode:raise RuntimeError('DISPOSABLE_SQL_FAILED')
 return p.stdout.rstrip('\r\n')
def rows(q,database=DB):return [line.split('\t') for line in sql(q,database).splitlines()]
def legacy_state(database=DB):
 columns=','.join('`'+c+'`' for c in original_columns)
 values=rows('SELECT '+columns+' FROM ticket WHERE id IN(10,11,12) ORDER BY id',database)
 return {'rows':len(values),'sha256':hashlib.sha256(json.dumps(values,ensure_ascii=False).encode()).hexdigest()}
def emit(stage):
 global phase
 phase=stage;print(json.dumps({'phase':stage,'run_id':TAG}),flush=True)
def sanitized(s):
 for v in [root_password,password,jwt]:s=s.replace(v,'[SECRET_REDACTED]')
 return re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[JWT_REDACTED]',s)
def maven(selector,name):
 m=Path(env['USERPROFILE'])/'.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn.cmd'
 agent=Path(env['USERPROFILE'])/'.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
 args=[str(m),'-o','-B','-ntp','test','-DargLine=-javaagent:'+str(agent),'-Dtest='+selector,'-Dlogging.level.root=OFF','-Dspring.main.banner-mode=off']
 proc=subprocess.Popen(args,cwd=BACK,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=subprocess.CREATE_NEW_PROCESS_GROUP|subprocess.CREATE_NO_WINDOW)
 try:output,_=proc.communicate(timeout=600)
 except subprocess.TimeoutExpired:
  subprocess.run(['taskkill','/PID',str(proc.pid),'/T','/F'],capture_output=True,env=env);output,_=proc.communicate(timeout=20);raise RuntimeError('TEST_TIMEOUT')
 (OUT/(name+'.log')).write_text(sanitized(output),encoding='utf-8')
 results=[]
 for file in (BACK/'target/surefire-reports').glob('TEST-*.xml'):
  text=sanitized(file.read_text(encoding='utf-8'));file.write_text(text,encoding='utf-8');node=ET.fromstring(text)
  if node.attrib['name'].rsplit('.',1)[-1]!=selector:continue
  results.append({'suite':node.attrib['name'],**{k:int(node.attrib[k]) for k in ['tests','failures','errors','skipped']},'cases':[{'name':c.attrib['name'],'passed':c.find('failure') is None and c.find('error') is None and c.find('skipped') is None} for c in node.findall('testcase')]})
 report[name]={'exit_code':proc.returncode,'suites':results,'tls_pin_observed':'QA_JDBC_TLS_CA_PIN_VERIFIED' in output}
 if proc.returncode:
  report[name]['diagnostic']=[line[:350] for line in sanitized(output).splitlines() if any(w in line for w in ['[ERROR]','Caused by:']) and 'WebMergedContextConfiguration' not in line][:16]
  raise RuntimeError('TEST_FAILED_'+name.upper())
 assert results and all(x['failures']==0 and x['errors']==0 and x['skipped']==0 for x in results),'TEST_EVIDENCE_MISSING'
try:
 require(docker(['info','--format','{{.OSType}}/{{.Architecture}}']),'DOCKER_ENGINE_UNAVAILABLE')
 context=require(docker(['context','show']),'CONTEXT_UNAVAILABLE');assert context=='desktop-linux','LOCAL_DOCKER_REQUIRED'
 endpoint=json.loads(require(docker(['context','inspect',context]),'CONTEXT_INSPECT'))[0]['Endpoints']['docker']['Host'];assert endpoint.startswith('npipe://'),'LOCAL_ENDPOINT_REQUIRED'
 for image in [IMAGE,PROXY_IMAGE]:assert require(docker(['image','inspect',image,'--format','{{.Os}}/{{.Architecture}}']),'IMAGE_MISSING')=='linux/amd64','ARCH_MISMATCH'
 assert shutil.disk_usage(R).free>2*1024**3,'INSUFFICIENT_DISK'
 original_containers=set(require(docker(['ps','-aq']),'CONTAINER_INVENTORY').splitlines());original_volumes=set(require(docker(['volume','ls','-q']),'VOLUME_INVENTORY').splitlines());original_networks=set(require(docker(['network','ls','-q']),'NETWORK_INVENTORY').splitlines())
 SOURCE=R/'dima-bug-backend';BACK=OUT/'backend';BACK.mkdir()
 for path in [SOURCE/'pom.xml',*(SOURCE/'src/main').rglob('*')]:
  if path.is_file():source_hashes[path.relative_to(SOURCE).as_posix()]=sha(path)
 shutil.copy2(SOURCE/'pom.xml',BACK/'pom.xml');shutil.copytree(SOURCE/'src/main',BACK/'src/main');shutil.copytree(SOURCE/'src/test',BACK/'src/test')
 for test in ['Skill45BeforeMigrationValidationTest.java','Skill45TicketMySqlTest.java']:
  shutil.copy2(Path(__file__).parent/test,BACK/'src/test/java/cl/casol/backend'/test)
 tls=BACK/'src/test/java/dimabug/qa';tls.mkdir(parents=True,exist_ok=True);shutil.copy2(Path(__file__).parent/'PinnedSocketFactory.java',tls/'PinnedSocketFactory.java')
 (OUT/'source-manifest.json').write_text(json.dumps(source_hashes,sort_keys=True,indent=2)+'\n',encoding='utf-8')
 # A freshly made directory only; all photos will be deleted after the run, evidence remains separate.
 STORAGE=OUT/'temporary-storage';STORAGE.mkdir();MATERIALS=OUT/'temporary-materials';MATERIALS.mkdir()
 fixture=SOURCE/'src/test/resources/tickets/foto.png'
 for filename in ['11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222']:shutil.copy2(fixture,STORAGE/filename)
 emit('CREATE_EXCLUSIVE_DISPOSABLE_MYSQL')
 require(docker(['network','create','--internal','--label','dimabug.skill45='+TAG,NET]),'NETWORK_CREATE_FAILED')
 env['MYSQL_ROOT_PASSWORD']=root_password
 require(docker(['run','--pull=never','--rm','--detach','--name',NAME,'--label','dimabug.skill45='+TAG,'--network',NET,'--memory=1g','--cpus=2','--pids-limit=256','--tmpfs','/var/lib/mysql:rw,nosuid,size=768m','--tmpfs','/var/run/mysqld:rw,nosuid,size=16m','--env','MYSQL_ROOT_PASSWORD','--env','MYSQL_DATABASE='+DB,IMAGE,'--event-scheduler=OFF','--local-infile=OFF','--secure-file-priv=NULL','--skip-log-bin'],timeout=100),'CONTAINER_CREATE_FAILED')
 env.pop('MYSQL_ROOT_PASSWORD',None)
 info=json.loads(require(docker(['inspect',NAME]),'MYSQL_INSPECT_FAILED'))[0];network=json.loads(require(docker(['network','inspect',NET]),'NETWORK_INSPECT_FAILED'))[0]
 assert network['Internal'] and len(network['Containers'])==1 and not info['HostConfig'].get('PortBindings'),'ISOLATION_FAILED'
 assert not any(m['Type'] in ['bind','volume'] for m in info['Mounts']),'PERSISTENT_VOLUME_REJECTED'
 ready=False
 for _ in range(65):
  p=docker(['exec',NAME,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -uroot '+DB+' -e "SELECT 1"'],timeout=10)
  if p.returncode==0:ready=True;break
  time.sleep(1)
 assert ready,'MYSQL_NOT_READY'
 report['mysql_version']=sql('SELECT VERSION()');assert report['mysql_version'].startswith('8.0.'),'MYSQL_VERSION_REJECTED'
 server_uuid=sql('SELECT @@server_uuid');assert re.fullmatch('[a-f0-9-]{36}',server_uuid),'SERVER_UUID_MISSING'
 emit('REPRODUCE_VERSIONED_SCHEMA_AND_SYNTHETIC_LEGACY')
 schema=(R/'qa/mysql/schema/20_schema_final.sql').read_text(encoding='utf-8');assert 'USE `dima_bug_qa_mvp`;' in schema
 schema=schema.replace('USE `dima_bug_qa_mvp`;', 'USE `'+DB+'`;')
 original_ddl=(SOURCE/'src/main/resources/db/migration/V20261017_01__modulo_tickets.sql').read_text(encoding='utf-8')
 block=lambda s: re.sub(r'\s+',' ',re.search(r'CREATE TABLE(?: IF NOT EXISTS)? ticket \(.*?\) ENGINE=InnoDB[^;]*;',s,re.S).group(0)).replace(' IF NOT EXISTS','')
 assert block(schema)==block(original_ddl),'TICKET_BASELINE_SOURCE_MISMATCH'
 sql(schema);sql((SOURCE/'src/main/resources/db/migration/V20261115_01__registro_universal.sql').read_text(encoding='utf-8'))
 report['baseline_tables']=int(sql('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()'))
 sql("INSERT INTO u_organizacion_control VALUES(1); INSERT INTO u_rol VALUES(1,'USUARIO','Sintético',1,NOW(),NOW()),(2,'TECNICO','Sintético',1,NOW(),NOW());"+
     "INSERT INTO u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion,registro_estado) VALUES(1,1,'Cuenta sintética 1','uno@example.invalid','NO_LOGIN_HASH',1,NOW(),NOW(),'AUTO_CONFIRMADO'),(2,1,'Cuenta sintética 2','dos@example.invalid','NO_LOGIN_HASH',1,NOW(),NOW(),'AUTO_CONFIRMADO'),(3,2,'Técnico sintético','tecnico@example.invalid','NO_LOGIN_HASH',1,NOW(),NOW(),'ADMINISTRADO');"+
     "INSERT INTO u_sucursal(sucursal_id,sucursal_codigo,sucursal_nombre,responsable_nombre,responsable_email,sucursal_activa,revision) VALUES(1,'QA','Sucursal sintética QA','Solo QA','contacto@example.invalid',1,0),(2,'QA-OFF','Inactiva QA','Solo QA','off@example.invalid',0,0);"+
     "INSERT INTO u_departamento VALUES(1,'Área sintética QA',1,0),(2,'Inactiva QA',0,0);"+
     "INSERT INTO ticket(id,codigo,titulo,descripcion,estado,prioridad,solicitante_id,asignado_id,fecha_creacion,fecha_actualizacion,fecha_cierre) VALUES(10,'TK-2020-0010','Legado sintético á Ñ','Contenido de prueba','ABIERTO','MEDIA',1,3,'2020-01-01','2020-01-02',NULL),(11,'TK-2020-0011','Legado cerrado','Prueba','CERRADO','ALTA',2,3,'2020-01-01','2020-01-02','2020-01-03'),(12,'TK-2020-0012','Legado en espera','Prueba','EN_ESPERA','BAJA',3,NULL,'2020-01-01','2020-01-02',NULL);"+
     "INSERT INTO ticket_comentario(id,ticket_id,autor_id,texto,interno,fecha) VALUES(1,10,3,'Nota interna sintética',1,'2020-01-02');"+
     "INSERT INTO ticket_adjunto(id,ticket_id,comentario_id,nombre,mime,tamano,referencia,autor_id,fecha) VALUES(1,10,NULL,'foto-anterior.png','image/png',"+str(fixture.stat().st_size)+",'11111111-1111-4111-8111-111111111111',1,'2020-01-02'),(2,10,1,'interna.png','image/png',"+str(fixture.stat().st_size)+",'22222222-2222-4222-8222-222222222222',3,'2020-01-02');")
 columns_before=rows("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,IFNULL(COLUMN_DEFAULT,'<NULL>'),EXTRA FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ticket' ORDER BY ordinal_position")
 report['column_metadata_before']=columns_before
 original_columns=[x[0] for x in columns_before]; baseline=legacy_state();old_attachments=rows('SELECT * FROM ticket_adjunto ORDER BY id')
 report['legacy_before']=baseline;report['ticket_baseline_matches_versioned_ddl']=True
 # Application DML privileges only, TLS mandatory, account unique to this disposable server.
 sql("CREATE USER 'qa_runtime'@'%' IDENTIFIED BY '"+password+"' REQUIRE SSL; GRANT SELECT,INSERT,UPDATE,DELETE ON `"+DB+"`.* TO 'qa_runtime'@'%';")
 # All output is captured in memory; only synthetic application schema/data are backed up.
 dump=require(docker(['exec',NAME,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --protocol=socket -uroot --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --skip-dump-date --default-character-set=utf8mb4 '+DB]),'SYNTHETIC_BACKUP_FAILED')
 compressed=gzip.compress(dump.encode('utf-8'));assert gzip.decompress(compressed)==dump.encode('utf-8'),'BACKUP_INTEGRITY'
 (OUT/'synthetic-pre-migration.sql.gz').write_bytes(compressed)
 sql('CREATE DATABASE `'+DB+'_restore` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;');sql(dump,DB+'_restore');assert legacy_state(DB+'_restore')==baseline,'RESTORE_MISMATCH';sql('DROP DATABASE `'+DB+'_restore`;')
 report['synthetic_backup']={'sha256':hashlib.sha256(compressed).hexdigest(),'readback_verified':True,'restore_to_exclusive_schema_verified':True}
 emit('PRIVATE_TLS_PROXY_AND_PIN')
 ca=require(docker(['exec',NAME,'cat','/var/lib/mysql/ca.pem']),'PUBLIC_CA_READ_FAILED');cert=require(docker(['exec',NAME,'cat','/var/lib/mysql/server-cert.pem']),'PUBLIC_CERT_READ_FAILED')
 ca_match=re.search(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----',ca,re.S);cert_match=re.search(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----',cert,re.S)
 assert ca_match and cert_match,'PUBLIC_PEM_MISSING'
 (OUT/'public-ca.pem').write_text(ca_match.group(0)+'\n',encoding='ascii');pin=hashlib.sha256(ssl.PEM_cert_to_DER_cert(cert_match.group(0))).hexdigest()
 store=OUT/'public-ca.jks'
 create=subprocess.run([str(JDK/'java.exe'),str(R/'qa/backend-tls/src/dimabug/qa/CreatePublicTruststore.java'),str(OUT/'public-ca.pem'),str(store)],env=env,capture_output=True,text=True,timeout=45);assert create.returncode==0,'PUBLIC_TRUSTSTORE_FAILED'
 cfg=OUT/'haproxy-public-config.cfg';cfg.write_text((R/'qa/mysql/haproxy.qa-local.cfg').read_text(encoding='utf-8').replace('bind :13318','bind :3310').replace('server mysql mysql:3306','server mysql '+NAME+':3306'),encoding='utf-8')
 require(docker(['network','create','--label','dimabug.skill45='+TAG,ACCESS]),'ACCESS_NETWORK_CREATE')
 require(docker(['run','--pull=never','--rm','--detach','--name',PROXY,'--label','dimabug.skill45='+TAG,'--network',ACCESS,'--network',NET,'--publish','127.0.0.1::3310','--user','65534:65534','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges:true','--sysctl','net.ipv4.ip_forward=0','--memory','128m','--pids-limit','64','--mount','type=bind,src='+str(cfg.resolve())+',dst=/usr/local/etc/haproxy/haproxy.cfg,readonly','--entrypoint','haproxy',PROXY_IMAGE,'-db','-f','/usr/local/etc/haproxy/haproxy.cfg']),'PROXY_CREATE_FAILED')
 pi=json.loads(require(docker(['inspect',PROXY]),'PROXY_INSPECT'))[0]
 binds=pi['NetworkSettings']['Ports']['3310/tcp'];assert len(binds)==1 and binds[0]['HostIp']=='127.0.0.1','NON_LOOPBACK_PROXY'
 port=int(binds[0]['HostPort']);assert port not in [3306,3307,13316,13318,8081],'SHARED_PORT_REJECTED'
 assert set(pi['NetworkSettings']['Networks'])=={ACCESS,NET} and pi['HostConfig']['Sysctls']['net.ipv4.ip_forward']=='0','PROXY_ISOLATION_FAILED'
 env.update(SKILL45_DATABASE=DB,SKILL45_SERVER_UUID=server_uuid,SKILL45_PORT=str(port),SKILL45_PASSWORD=password,SKILL45_JWT=jwt,SKILL45_PIN=pin,SKILL45_STORE_URI=store.resolve().as_uri(),SKILL45_STORE_SHA=sha(store),SKILL45_STORAGE=str(STORAGE.resolve()),SKILL45_MATERIALS=str(MATERIALS.resolve()))
 env['SKILL45_URL']='jdbc:mysql://127.0.0.1:'+str(port)+'/'+DB+'?sslMode=VERIFY_CA&fallbackToSystemTrustStore=false&allowPublicKeyRetrieval=false&tlsVersions=TLSv1.2,TLSv1.3&socketFactory=dimabug.qa.PinnedSocketFactory&connectionTimeZone=UTC&connectTimeout=5000&socketTimeout=20000'
 emit('VERIFY_HIBERNATE_REJECTS_PRE_MIGRATION_SCHEMA');maven('Skill45BeforeMigrationValidationTest','before_migration_validate')
 assert legacy_state()==baseline,'BEFORE_PROBE_MUTATED_DATA'
 emit('APPLY_EXACT_MIGRATION_ONCE')
 migration=SOURCE/'src/main/resources/db/migration/V20261116_01__ticket_datos_solicitante.sql';report['migration_sha256']=sha(migration);sql(migration.read_text(encoding='utf-8'))
 columns_after=rows("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,IFNULL(COLUMN_DEFAULT,'<NULL>'),EXTRA FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ticket' ORDER BY ordinal_position")
 report['column_metadata_after']=columns_after
 expected={'sol_nombre_completo':'varchar(150)','sol_sucursal_id':'int','sol_sucursal_nombre':'varchar(100)','sol_departamento_id':'int','sol_departamento_nombre':'varchar(100)','sol_contacto':'varchar(50)','sol_equipo':'varchar(100)','sol_codigo_usuario':'varchar(80)'}
 added=[x for x in columns_after if x[0] not in original_columns]
 assert len(added)==8 and {x[0]:x[1] for x in added}==expected and all(x[2]=='YES' for x in added),'NEW_COLUMN_MISMATCH'
 assert [x for x in columns_after if x[0] in original_columns]==columns_before,'OLD_COLUMN_CHANGED'
 assert legacy_state()==baseline and rows('SELECT * FROM ticket_adjunto ORDER BY id')==old_attachments,'LEGACY_DATA_CHANGED'
 assert sql("SELECT ENFORCED FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='ticket' AND constraint_name='ck_ticket_sol_datos'")=='YES','CHECK_NOT_ENFORCED'
 report['migration']={'applied_once':True,'columns':added,'old_columns_unchanged':True,'legacy_rows_unchanged':True,'legacy_attachments_unchanged':True,'check_enforced':True}
 emit('FUNCTIONAL_AUTHORIZATION_PHOTOS_AND_HIBERNATE_VALIDATE');maven('Skill45TicketMySqlTest','native_functional')
 assert legacy_state()==baseline,'FUNCTIONAL_TEST_CHANGED_LEGACY_ROWS';assert rows('SELECT * FROM ticket_adjunto WHERE id IN(1,2) ORDER BY id')==old_attachments,'LEGACY_PHOTO_METADATA_CHANGED'
 assert all((STORAGE/n).read_bytes()==fixture.read_bytes() for n in ['11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222']),'LEGACY_PHOTOS_CHANGED'
 assert sql('SELECT COUNT(*) FROM gm_cuenta_oauth')=='0','UNEXPECTED_OAUTH_RECORD'
 report.update(legacy_after=legacy_state(),legacy_photographs_preserved=True,hibernate_validate_verified=True,tls_ca_and_pin_before_auth_verified=True,loopback_only=True,tmpfs_mysql_only=True,shared_resources_reused=False,functional_new_ticket_count=int(sql('SELECT COUNT(*) FROM ticket WHERE id NOT IN(10,11,12)')))
 report['production_classes_match_current_compilation']=all((SOURCE/'target/classes'/f.relative_to(BACK/'target/classes')).is_file() and sha(f)==sha(SOURCE/'target/classes'/f.relative_to(BACK/'target/classes')) for f in (BACK/'target/classes').rglob('*.class'))
 assert report['production_classes_match_current_compilation'],'COMPILED_SOURCE_MISMATCH'
 report['verified']=True
except BaseException as ex:
 report['failure_trace']=[{'file':Path(t.filename).name,'line':t.lineno,'function':t.name} for t in traceback.extract_tb(ex.__traceback__)]
 report.update(verified=False,failure_phase=phase,failure_type=type(ex).__name__,failure_code=str(ex) if re.fullmatch('[A-Z_]+',str(ex)) else 'DETAILS_SUPPRESSED')
finally:
 cleanup=[]
 engine_healthy=docker(['info','--format','{{.OSType}}'],timeout=15).returncode==0
 if phase=='LOCAL_PREFLIGHT':
  cleanup.append(True)  # No mutating Docker command is reached before CREATE_EXCLUSIVE_DISPOSABLE_MYSQL.
 elif not engine_healthy:
  cleanup.append(False)
 else:
  for name in [PROXY,NAME]:
   inspect=docker(['inspect',name,'--format','{{index .Config.Labels "dimabug.skill45"}}'])
   if inspect.returncode==0:
    ok=inspect.stdout.strip()==TAG and docker(['rm','--force',name],timeout=50).returncode==0
    cleanup.append(ok and docker(['inspect',name]).returncode!=0)
   else:cleanup.append('No such' in inspect.stderr)
  for name in [NET,ACCESS]:
   inspect=docker(['network','inspect',name,'--format','{{index .Labels "dimabug.skill45"}}'])
   if inspect.returncode==0:
    ok=inspect.stdout.strip()==TAG and docker(['network','rm',name],timeout=30).returncode==0;cleanup.append(ok)
   else:cleanup.append('No such' in inspect.stderr)
 report['resource_counts_before']={'containers':len(original_containers),'volumes':len(original_volumes),'networks':len(original_networks)}
 final_containers=set();final_volumes=set();final_networks=set()
 if engine_healthy:
  inventory=[docker(args) for args in [['ps','-aq'],['volume','ls','-q'],['network','ls','-q']]]
  if all(p.returncode==0 for p in inventory):
   final_containers,final_volumes,final_networks=[set(p.stdout.strip().splitlines()) for p in inventory]
   report['resource_counts_after']={'containers':len(final_containers),'volumes':len(final_volumes),'networks':len(final_networks)}
   report['inventory_delta']={'containers_added':len(final_containers-original_containers),'containers_removed':len(original_containers-final_containers),'volumes_added':len(final_volumes-original_volumes),'volumes_removed':len(original_volumes-final_volumes),'networks_added':len(final_networks-original_networks),'networks_removed':len(original_networks-final_networks)}
  else:cleanup.append(False)
 # Delete only the verified, exclusive temporary photograph directories inside this run.
 for name in ['temporary-storage','temporary-materials']:
  directory=(OUT/name).resolve();directory.relative_to(OUT.resolve());assert directory.name in ['temporary-storage','temporary-materials'] and directory!=OUT.resolve()
  if directory.exists():shutil.rmtree(directory)
 report['cleanup_verified']=all(cleanup)
 if original_containers or original_volumes or original_networks:
  report['preexisting_resource_inventory_unchanged']=(final_containers==original_containers and final_volumes==original_volumes and final_networks==original_networks)
 report['source_files_unchanged']=bool(source_hashes) and all(sha(R/'dima-bug-backend'/n)==h for n,h in source_hashes.items())
 report['verified']=report.get('verified',False) and report['cleanup_verified'] and report['source_files_unchanged'] and report.get('preexisting_resource_inventory_unchanged',False)
 (OUT/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 print(json.dumps({'status':'MYSQL8_VALIDATION_VERIFIED' if report['verified'] else 'VALIDATION_NOT_VERIFIED','run_id':TAG,'result':str(OUT/'result.json'),'failure_phase':report.get('failure_phase'),'failure_code':report.get('failure_code'),'cleanup_verified':report['cleanup_verified'],'staging_modified':False}),flush=True)
sys.exit(0 if report['verified'] else 1)
