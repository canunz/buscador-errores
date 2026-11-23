"""Staging-only authorized deployment. No OTP requests, custody changes or DB restoration."""
import argparse,base64,copy,gzip,hashlib,json,os,re,shutil,signal,socket,stat,struct,subprocess,sys,tarfile,time,uuid,zipfile
try:import fcntl
except ImportError:fcntl=None
from pathlib import Path
import deploy_staging as d
from deployment_sql_session import SqlSession,quarantine

RELEASE='registro-universal-20261115';DB='dima_bug_staging';UNIT='dimabug@staging.service'
OLD='a74f4750ee4c9b3c166449ab669655bd00bb14e2b234ccc3de0c903aa0597c8d'
NEW='1ae8d4dd644ac5f53bfa1bf68f7f84be64e9c60d9ca112d56051cd26a79c2647'
FRONT_SHA='aded6a14740376801d3763242c6b204f90d7dacbbf8b8c15061d300ad5ca4d73'
SQL_SHA='10dd7fc3f54d815f4dd0a67ae0681142bd49a80a40e98a78ec8b00b2aa1e0723'
ENV=Path('/etc/dimabug/staging.env');JAR=Path('/opt/dimabug/staging/backend.jar');FRONT=Path('/var/www/dimabug-staging')
PROFILE=Path('/etc/dimabug-staging-releases/staging-minimo-20261114T025917Z/application-staging.properties')
R=d.require;FOLDER=None;PHASE='PREFLIGHT';ORIGINAL_CMD=d.cmd;PAYLOAD=None
REG='DIMABUG_AUTH_REGISTRO_UNIVERSAL_HABILITADO';AUTH='DIMABUG_AUTH_CORREO_HABILITADO'
MAIL=['GMAIL_ENVIO_REAL_HABILITADO','DIMABUG_GMAIL_ENVIO_REAL_HABILITADO',AUTH]
SAFE={'DIMABUG_CORREO_BIENVENIDA_HABILITADO':'false','DIMABUG_CORREO_TICKET_CRITICO_HABILITADO':'false','DIMABUG_AUTOMATIZACION_MODO':'DESHABILITADO','DIMABUG_AUTOMATIZACION_SEGUIMIENTO_HABILITADO':'false'}
QUOTAS={'DIMABUG_AUTH_REGISTRO_UNIVERSAL_MAXIMO_HORA':'20','DIMABUG_AUTH_REGISTRO_UNIVERSAL_MAXIMO_DIA':'100','DIMABUG_AUTH_REGISTRO_UNIVERSAL_MAXIMO_PENDIENTES':'200'}

def digest(p):return d.prep.digest(p)
def atomic(p,data,**kw):d.atomic(p,data,**kw)
def write(p,value):d.write_json(p,value)
def phase(name):
 global PHASE
 PHASE=name;print(json.dumps({'phase':name,'release':RELEASE}),flush=True)
def start_tick(pid):return Path('/proc',str(pid),'stat').read_text().split(') ',1)[1].split()[19]
def op_started(p):
 if FOLDER:
  try:write(FOLDER/'active-operation.json',{'pid':p.pid,'start':start_tick(p.pid),'owner':os.getpid()})
  except FileNotFoundError:pass
def op_finished(p):
 if FOLDER:
  f=FOLDER/'active-operation.json'
  if f.exists() and json.loads(f.read_text()).get('pid')==p.pid:f.unlink()
def run(args,timeout=30,input=None,output=None,env=None):
 if args[0]=='systemctl' and args[1] in ('start','stop','restart','reload'):
  R(args in (['systemctl','start',UNIT],['systemctl','stop',UNIT],['systemctl','reload','nginx']),'UNAUTHORIZED_SERVICE_OPERATION')
 p=subprocess.Popen(args,stdin=subprocess.PIPE if input is not None else subprocess.DEVNULL,stdout=output or subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True,env=env)
 op_started(p)
 try:
  try:out,err=p.communicate(input,timeout=timeout)
  except subprocess.TimeoutExpired:
   os.killpg(p.pid,signal.SIGKILL);p.communicate(timeout=10);raise RuntimeError('CONTROL_TIMEOUT') from None
  R(p.returncode==0,'CONTROL_COMMAND_FAILED');return out or b''
 finally:op_finished(p)
def client(database=DB):return ['mysql','--protocol=socket','--user=root','--skip-reconnect','--skip-force','--unbuffered','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database='+database]
def sql(q,database=DB):return [x.split('\t') for x in run(client(database),input=q.encode(),timeout=30).decode().splitlines()]
def attrs(p):
 s=p.lstat();R(stat.S_ISREG(s.st_mode) and s.st_nlink==1,'UNSAFE_REGULAR_FILE')
 return {'mode':stat.S_IMODE(s.st_mode),'uid':s.st_uid,'gid':s.st_gid}
def runtime(expected=None):
 state=d.show();R(state['MainPID']!='0' and state['ActiveState']=='active','STAGING_NOT_ACTIVE')
 proc=Path('/proc',state['MainPID']);R((proc/'cwd').resolve()==JAR.parent,'WRONG_CWD')
 args=(proc/'cmdline').read_bytes().split(b'\0');R(str(JAR).encode() in args,'WRONG_LIVE_JAR')
 R(not any(x.startswith((b'-Dspring.',b'--spring.',b'-Ddimabug.',b'--dimabug.',b'-Djwt.',b'--jwt.',b'-Dhibernate.',b'-Djakarta.persistence.',b'-Dserver.',b'--server.')) for x in args),'JVM_OVERRIDE')
 env=dict(v.decode().split('=',1) for v in (proc/'environ').read_bytes().split(b'\0') if b'=' in v)
 R(env.get('SPRING_PROFILES_ACTIVE')=='staging' and env.get('SERVER_PORT')=='8081','WRONG_PROFILE_OR_PORT')
 R(env.get('SPRING_CONFIG_ADDITIONAL_LOCATION')=='file:'+str(PROFILE.parent)+'/','WRONG_PROFILE_PATH')
 R(re.fullmatch(r'jdbc:mysql://(?:127\.0\.0\.1|localhost)(?::3306)?/dima_bug_staging(?:\?.*)?',env.get('DB_URL','')),'WRONG_DB_URL')
 for alias,key in [('SPRING_DATASOURCE_URL','DB_URL'),('SPRING_DATASOURCE_USERNAME','DB_USERNAME'),('SPRING_DATASOURCE_PASSWORD','DB_PASSWORD')]:R(alias not in env or env[alias]==env[key],'DATASOURCE_OVERRIDE')
 R(not any(env.get(k) for k in ('SPRING_CONFIG_LOCATION','SPRING_CONFIG_NAME','SPRING_PROFILES_INCLUDE','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','JAVA_OPTS')),'CONFIG_OVERRIDE')
 props=d.prep.flat(json.loads(env.get('SPRING_APPLICATION_JSON') or '{}'))
 R(set(props)<={'logging.level.cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter','logging.level.cl.casol.backend.identidad.infrastructure.security.JwtTokenServiceAdapter'},'JSON_OVERRIDE')
 for k in env:
  R(not(k.startswith('SPRING_JPA_PROPERTIES_') and ('SCHEMA' in k or 'HBM2DDL' in k)),'JPA_ACTION_OVERRIDE')
 R(env.get('SPRING_JPA_HIBERNATE_DDL_AUTO','validate')=='validate' and env.get('SPRING_SQL_INIT_MODE','never')=='never' and env.get('SPRING_JPA_GENERATE_DDL','false')=='false','DDL_OVERRIDE')
 for key,value in SAFE.items():R(env.get(key,value)==value,'OTHER_EMITTER_OR_AUTOMATION_ENABLED')
 R(d.prep.MAIL_EXCLUSION in env.get('SPRING_AUTOCONFIGURE_EXCLUDE','').split(','),'SMTP_NOT_EXCLUDED')
 R(all(env.get(k) for k in ('DB_PASSWORD','DB_USERNAME','JWT_SECRET','JWT_EXPIRATION','GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET','GMAIL_TOKEN_ENCRYPTION_KEY')),'MISSING_CUSTODY')
 R(len(base64.b64decode(env['GMAIL_TOKEN_ENCRYPTION_KEY'],validate=True))==32 and len(env['JWT_SECRET'].encode())>=32,'INVALID_KEY_CONFIGURATION')
 R(env.get('GMAIL_SENDER_EMAIL')=='dimabug507@gmail.com' and env.get('GMAIL_OAUTH_HABILITADO')=='true','OAUTH_CHANGED')
 rows=run(['ss','-H','-ltnp','( sport = :8081 )']).decode().splitlines()
 R(bool(rows) and all(len(x.split())>=5 and x.split()[3] in ('127.0.0.1:8081','[::1]:8081','[::ffff:127.0.0.1]:8081') and ('pid='+state['MainPID']+',') in x for x in rows),'BACKEND_NOT_PRIVATE_OR_PID_MISMATCH')
 if expected:R(all(env.get(k)==v for k,v in expected.items()),'RUNTIME_ENV_CHANGED')
 return env
def configure(raw,enabled):
 old=d.prep.assignments(raw);updates={**SAFE,**QUOTAS,REG:str(enabled).lower(),**{k:str(enabled).lower() for k in MAIL}}
 lines=[x for x in raw.decode().splitlines() if x.strip().split('=',1)[0] not in updates]
 result=('\n'.join(lines)+'\n'+''.join(k+"='"+v+"'\n" for k,v in updates.items())).encode()
 new=d.prep.assignments(result)
 R({k:v for k,v in old.items() if k not in updates}=={k:v for k,v in new.items() if k not in updates},'CUSTODY_CONFIG_MUTATION')
 return result,new
def route(ctx,blocked):
 path=Path(ctx['nginx']);original=(FOLDER/'nginx.conf').read_bytes();maintenance=d.maintenance(original)
 R(path.read_bytes() in (original,maintenance),'NGINX_CONCURRENT_CHANGE')
 atomic(path,maintenance if blocked else original,**ctx['nginx_attrs']);run(['nginx','-t']);run(['systemctl','reload','nginx'])
 for origin,public in ((True,False),(False,True)):d.wait_http('/api/health',503 if blocked else 200,origin=origin,public=public)
def queue_clear():
 R(sql("SELECT COUNT(*) FROM u_correo_codigo WHERE estado IN ('PENDIENTE','ERROR_TEMPORAL','ENVIANDO') OR contenido_cifrado IS NOT NULL")==[['0']],'LIVE_QUEUED_EMAIL_REQUIRES_REVIEW')
def managed(meta):
 columns=[c for c in sorted(meta['columns']['u_usuario']) if c not in ('registro_estado','usuario_ultimo_ingreso','usuario_fecha_actualizacion')]
 parts=["IF(`"+c+"` IS NULL,'N',CONCAT('V',HEX(`"+c+"`)))" for c in columns]
 condition=" WHERE registro_estado='ADMINISTRADO'" if sql("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='u_usuario' AND COLUMN_NAME='registro_estado'")==[['1']] else ''
 rows=sql("SELECT SHA2(CONCAT_WS('|',"+','.join(parts)+"),256) FROM u_usuario"+condition)
 return {'rows':len(rows),'sha256':hashlib.sha256('\n'.join(sorted(x[0] for x in rows)).encode()).hexdigest()}
def closed_checks(ctx):
 R(d.show()['MainPID']=='0','STOP_NOT_CONFIRMED');R(d.app_sessions(ctx['db_user'])==0,'EXTERNAL_STAGING_DB_CLIENTS')
def extract_archive(archive,destination,label,expected):
 destination.mkdir(mode=0o700)
 with tarfile.open(archive,'r:gz') as t:
  dirs=[]
  for m in t:
   p=Path(m.name);R(not p.is_absolute() and '..' not in p.parts and p.parts[0]==label and (m.isfile() or m.isdir()),'ARCHIVE_UNSAFE')
   out=destination.joinpath(*p.parts[1:])
   if m.isdir():out.mkdir(parents=True,exist_ok=True);dirs.append((out,m))
   else:
    out.parent.mkdir(parents=True,exist_ok=True)
    with t.extractfile(m) as src,out.open('xb') as dst:shutil.copyfileobj(src,dst)
    os.chmod(out,m.mode);os.chown(out,m.uid,m.gid)
  for out,m in reversed(dirs):os.chmod(out,m.mode);os.chown(out,m.uid,m.gid)
 R(d.prep.tree(destination)==expected,'ARCHIVE_RESTORE_READBACK_FAILED')
def restore_probe(ctx,baseline):
 name=ctx['restore_schema'];R(re.fullmatch('dima_bug_staging_restore_[a-f0-9]{12}',name) and name!=DB,'UNSAFE_RESTORE_PROBE')
 R(sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"+name+"'")==[['0']],'RESTORE_PROBE_ALREADY_EXISTS')
 R(sql("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='"+DB+"'")==[['0']],'TRIGGERS_REQUIRE_RESTORE_REVIEW')
 R(sql("SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='"+DB+"'")==[['0']],'ROUTINES_REQUIRE_RESTORE_REVIEW')
 R(sql("SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA='"+DB+"'")==[['0']],'EVENTS_REQUIRE_RESTORE_REVIEW')
 R(sql("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='"+DB+"' AND REFERENCED_TABLE_SCHEMA IS NOT NULL AND REFERENCED_TABLE_SCHEMA<>'"+DB+"'")==[['0']],'CROSS_SCHEMA_FOREIGN_KEYS_REQUIRE_REVIEW')
 charset,collation=sql("SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"+DB+"'")[0]
 R(re.fullmatch('[a-z0-9_]+',charset) and re.fullmatch('[a-z0-9_]+',collation),'RESTORE_CHARSET')
 sql('CREATE DATABASE `'+name+'` CHARACTER SET '+charset+' COLLATE '+collation)
 atomic(FOLDER/'restore-probe-owned',name.encode())
 transformed=FOLDER/'restore-probe-private.sql'
 with (FOLDER/'database.sql').open('rb') as src,transformed.open('xb') as dst:
  os.chmod(transformed,0o600)
  for line in src:
   if line.startswith(b'CREATE DATABASE '):continue
   if line.startswith(b'USE '):
    R(line.strip()==('USE `'+DB+'`;').encode(),'UNEXPECTED_DUMP_DATABASE');line=('USE `'+name+'`;\n').encode()
   if not line.startswith(b'INSERT INTO '):R(('`'+DB+'`.').encode() not in line and not line.startswith((b'ALTER DATABASE ',b'DROP DATABASE ')),'QUALIFIED_RESTORE_SOURCE_REFERENCE')
   dst.write(line)
 with transformed.open('rb') as f:run(client(name),input=f.read(),timeout=180)
 old=d.prep.DB
 try:
  d.prep.DB=name;R(d.prep.metadata()==baseline['schema'],'RESTORE_PROBE_SCHEMA_MISMATCH');R(d.prep.data_state(baseline['schema'])==baseline['data'],'RESTORE_PROBE_DATA_MISMATCH')
 finally:d.prep.DB=old
 sql('DROP DATABASE `'+name+'`')
 R(sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"+name+"'")==[['0']],'RESTORE_PROBE_CLEANUP_FAILED')
 transformed.unlink();R(d.prep.data_state(baseline['schema'])==baseline['data'],'SOURCE_CHANGED_DURING_RESTORE_PROBE')
 (FOLDER/'restore-probe-owned').unlink()
 return True
def backup(ctx):
 phase('FROZEN_STAGING_BACKUPS_AND_RECOVERY_PROBE')
 R(ENV.read_bytes()==(FOLDER/'original-staging.env').read_bytes(),'ENV_CONCURRENT_CHANGE')
 meta=d.prep.metadata();R(all(x=='InnoDB' for x in meta['engines'].values()),'NON_TRANSACTIONAL_BACKUP_TABLE');base={'schema':meta,'data':d.prep.data_state(meta),'privileges':d.prep.privileges(ctx['db_user']),'managed':managed(meta)}
 write(FOLDER/'baseline-private.json',base)
 files={'backend.jar':JAR,'staging.env':ENV,'application-staging.properties':PROFILE}
 for name,p in files.items():atomic(FOLDER/name,p.read_bytes());R(digest(FOLDER/name)==digest(p),'BACKUP_COMPONENT_MISMATCH')
 atomic(FOLDER/'systemd-staging.txt',run(['systemctl','cat',UNIT]))
 report={k:{'sha256':digest(FOLDER/k),'readback_verified':True} for k in files}
 for label,path in [('frontend',FRONT),('materiales',Path('/opt/dimabug/staging/uploads/materiales')),('tickets',Path('/var/lib/dimabug/staging/tickets'))]:
  R(path.is_dir() and not path.is_symlink(),'ATTACHMENT_PATH_CHANGED')
  tree=d.prep.tree(path);ctx['trees'][label]={'path':str(path),'files':tree}
  report[label]=d.prep.archive(path,FOLDER/(label+'.tar.gz'),label,tree)
  extract_archive(FOLDER/(label+'.tar.gz'),FOLDER/('recovery-check-'+label),label,tree);report[label]['restore_to_private_copy_verified']=True
 # Same dump format already validated by the existing preparation tooling.
 target=FOLDER/'database.sql'
 with target.open('xb') as out:
  os.chmod(target,0o600);run(['mysqldump','--protocol=socket','--user=root','--default-character-set=utf8mb4','--single-transaction','--skip-lock-tables','--no-tablespaces','--set-gtid-purged=OFF','--skip-extended-insert','--complete-insert','--order-by-primary','--hex-blob','--routines','--events','--triggers','--skip-dump-date','--databases',DB],output=out,timeout=180)
 counts={t:0 for t in meta['columns']};created=set();footer=False
 for line in target.read_text().splitlines():
  m=re.match(r'CREATE TABLE `([a-z0-9_]+)`',line)
  if m:created.add(m[1])
  m=re.match(r'INSERT INTO `([a-z0-9_]+)`',line)
  if m:R(m[1] in counts,'DUMP_UNEXPECTED_TABLE');counts[m[1]]+=1
  if line.startswith('-- Dump completed'):footer=True
 R(footer and created==set(counts) and counts=={t:x['rows'] for t,x in base['data'].items()},'DUMP_READBACK_FAILED')
 with target.open('rb') as src,gzip.open(FOLDER/'database.sql.gz','xb') as dst:shutil.copyfileobj(src,dst)
 os.chmod(FOLDER/'database.sql.gz',0o600)
 with gzip.open(FOLDER/'database.sql.gz','rb') as f:R(hashlib.file_digest(f,'sha256').hexdigest()==digest(target),'DUMP_GZIP_MISMATCH')
 report['database']={'sha256':digest(FOLDER/'database.sql.gz'),'uncompressed_sha256':digest(target),'tables':len(counts),'rows':sum(counts.values()),'readback_verified':True,'isolated_restore_verified':restore_probe(ctx,base)}
 R(d.prep.metadata()==meta and d.prep.data_state(meta)==base['data'],'FROZEN_BASELINE_CHANGED')
 ctx['old_frontend']={n:v['sha256'] for n,v in ctx['trees']['frontend']['files'].items()};write(FOLDER/'context-private.json',ctx)
 write(FOLDER/'backups-sanitized.json',report);atomic(FOLDER/'BACKUPS_VERIFIED',b'VERIFIED\n');return base,report
def migrated(base):
 m=d.prep.metadata();expected=copy.deepcopy(base['schema'])
 expected['columns']['u_usuario']['registro_estado']={'type':'varchar(24)','nullable':False}
 indexes=m['indexes']['u_usuario'];extra=[i for i in indexes if i['name'] in ('uq_usuario_email_normalizado','idx_usuario_registro_creacion')]
 R(len(extra)==2,'MIGRATION_INDEXES_MISSING')
 m['indexes']['u_usuario']=[i for i in indexes if i not in extra]
 R(m==expected,'UNEXPECTED_SCHEMA_DELTA')
 R(sql("SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='u_usuario' AND COLUMN_NAME='registro_estado'")==[['ADMINISTRADO']],'WRONG_REGISTRATION_DEFAULT')
 R(sql("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='u_usuario' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES' AND CONSTRAINT_NAME IN ('chk_usuario_registro_estado','chk_usuario_registro_inactivo')")==[['2']],'MIGRATION_CHECKS_NOT_ENFORCED')
 expr=sql("SELECT NON_UNIQUE,EXPRESSION FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='u_usuario' AND INDEX_NAME='uq_usuario_email_normalizado'")
 R(len(expr)==1 and expr[0][0]=='0' and re.sub(r'[\s`]','',expr[0][1]).lower()=='lower(trim(usuario_email))','CANONICAL_UNIQUE_INDEX_INVALID')
 R(sql("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='u_usuario' AND INDEX_NAME='idx_usuario_registro_creacion' ORDER BY SEQ_IN_INDEX")==[['registro_estado'],['usuario_fecha_creacion']],'ADMISSION_INDEX_INVALID')
 R(sql("SELECT COUNT(*) FROM u_usuario WHERE registro_estado<>'ADMINISTRADO'")==[['0']],'UNEXPECTED_AUTOREGISTRATION_BEFORE_PUBLIC_OPEN')
 R(d.prep.data_state(base['schema'])==base['data'],'DATA_CHANGED_DURING_DEPLOYMENT')
 R(d.prep.privileges(CONTEXT['db_user'])==base['privileges'],'PRIVILEGES_CHANGED')
 for t in CONTEXT['trees'].values():
  if t['path']!=str(FRONT):R(d.prep.tree(Path(t['path']))==t['files'],'ATTACHMENTS_CHANGED')
def prepare_jvm(jar):
 root=FOLDER/('probe-jvm-'+digest(jar)[:12])
 if not root.exists():
  root.mkdir(mode=0o700)
  with zipfile.ZipFile(jar) as z:
   for n in z.namelist():
    if not n.endswith('/') and n.startswith(('BOOT-INF/classes/','BOOT-INF/lib/')):
     R('..' not in Path(n).parts,'JAR_UNSAFE_PATH');out=root/n;out.parent.mkdir(parents=True,exist_ok=True)
     with z.open(n) as src,out.open('xb') as dst:shutil.copyfileobj(src,dst)
 return str(PAYLOAD/'tools/probe.jar')+':'+str(root/'BOOT-INF/classes')+':'+str(root/'BOOT-INF/lib/*')
def bindings(env,enabled):
 clean={k:v for k,v in env.items() if k.startswith(('SPRING_','SERVER_','DIMABUG_','GMAIL_','GOOGLE_','DB_','JWT_'))};clean.update(PATH='/usr/bin:/bin',LANG='C.UTF-8')
 out=run(['/usr/bin/java','-Xmx96m','-XX:ActiveProcessorCount=1','-cp',prepare_jvm(JAR),'DeploymentProbe','bindings',str(PROFILE)],env=clean,timeout=30).decode()
 data=json.loads(next(x for x in reversed(out.splitlines()) if x.startswith('{')))
 R(data=={'habilitado':enabled,'hora':20,'dia':100,'pendientes':200,'auth':enabled,'gmail':enabled,'welcome':False,'critical':False},'EFFECTIVE_BINDINGS_NOT_CONFIRMED')
 return data
def role_probes(env):
 import urllib.request,urllib.error
 email='dimabug507@gmail.com';literal='CONVERT(0x'+email.encode().hex()+' USING utf8mb4)'
 R(sql("SELECT u.usuario_estado,r.rol_estado,r.rol_nombre FROM u_usuario u JOIN u_rol r ON r.rol_id=u.rol_id WHERE BINARY u.usuario_email=BINARY "+literal)==[['1','1','TECNICO']],'AUTHORIZED_PROBE_ACCOUNT_CHANGED')
 key=env['JWT_SECRET'].encode();address=email.encode();payload=struct.pack('>I',len(key))+key+struct.pack('>I',len(address))+address
 out=run(['/usr/bin/java','-Xmx64m','-XX:ActiveProcessorCount=1','-cp',prepare_jvm(JAR),'DeploymentProbe','token'],input=payload,env={'PATH':'/usr/bin:/bin','LANG':'C.UTF-8'},timeout=20).decode()
 token=next(x for x in out.splitlines() if re.fullmatch(r'[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+',x))
 opener=urllib.request.build_opener(urllib.request.ProxyHandler({}));probes=[]
 for path,expected in [('/api/auth/me',200),('/api/sucursales',200),('/api/conocimientos',200),('/api/procedimientos',200),('/api/usuarios',403)]:
  request=urllib.request.Request('http://127.0.0.1:8081'+path,headers={'Authorization':'Bearer '+token})
  try:
   with opener.open(request,timeout=10) as response:status=response.status;body=response.read()
  except urllib.error.HTTPError as error:status=error.code;body=error.read()
  R(status==expected,'EXISTING_AUTHORIZATION_PROBE_FAILED')
  if path=='/api/auth/me' and digest(JAR)==NEW:R(json.loads(body).get('accesoBasico') is False,'MANAGED_USER_SCOPE_CHANGED')
  probes.append({'path':path,'http':status})
 return probes
def boot(env,sha,guard):
 R(digest(PROFILE)==CONTEXT['profile_sha'] and d.prep.assignments(ENV.read_bytes())==env,'PRESTART_CONFIGURATION_CHANGED')
 run(['systemctl','start',UNIT],timeout=90);info,_=d.ready(sha,env,guard);actual=runtime(env)
 lines=run(['journalctl','_SYSTEMD_INVOCATION_ID='+info['InvocationID'],'--no-pager','-o','json','-n','400']).decode().splitlines()
 text='\n'.join(str(json.loads(x).get('MESSAGE','')) for x in lines)
 R('Initialized JPA EntityManagerFactory' in text and 'Started BackendApplication in ' in text,'JPA_BOOT_NOT_CONFIRMED')
 R(not any(x in text for x in ['APPLICATION FAILED TO START','Schema-validation:','SchemaManagementException']),'HIBERNATE_FAILED')
 R(d.app_sessions(actual['DB_USERNAME'])>0,'MYSQL_NOT_CONNECTED');return actual
def wait_migration(ctx):
 token=ctx['sql_marker'];deadline=time.monotonic()+180
 while sql("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND INFO LIKE '%"+token+"%'")!=[['0']]:
  R(time.monotonic()<deadline,'MIGRATION_OUTCOME_UNKNOWN');time.sleep(.3)
def quarantine_before_legacy(ctx):
 R(not (FOLDER/'quarantine-commit-attempted').exists() or (FOLDER/'quarantine-commit-confirmed').exists(),'QUARANTINE_COMMIT_UNKNOWN')
 wait_migration(ctx)
 present=sql("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='u_usuario' AND COLUMN_NAME='registro_estado'")==[['1']]
 if not present:
  if (FOLDER/'baseline-private.json').exists():
   base=json.loads((FOLDER/'baseline-private.json').read_text());R(managed(base['schema'])==base['managed'],'MANAGED_ACCOUNTS_CHANGED');R(d.prep.privileges(ctx['db_user'])==base['privileges'],'PRIVILEGES_CHANGED')
  return {'required':False,'reason':'REGISTRATION_COLUMN_ABSENT'}
 R(sql("SELECT COUNT(*) FROM u_usuario WHERE registro_estado IS NULL OR registro_estado NOT IN ('ADMINISTRADO','AUTO_PENDIENTE','AUTO_CONFIRMADO','AUTO_CANCELADO')")==[['0']],'UNKNOWN_REGISTRATION_STATE')
 if not (FOLDER/'quarantine-commit-confirmed').exists():
  plan=(PAYLOAD/'rollback_quarantine.sql').read_text();plan=re.sub(r'(?m)^--.*$','',plan)
  statements=[q.strip() for q in plan.split(';') if q.strip() and q.strip().upper()!='START TRANSACTION']
  # One connection, no reconnection, commit after checks only. No retry of unknown COMMIT.
  with SqlSession(client(),started=op_started,finished=op_finished) as session:
   quarantine(session,DB,ctx['mysql_uuid'],statements,lambda:atomic(FOLDER/'quarantine-commit-attempted',b'ATTEMPTED\n'),lambda:atomic(FOLDER/'quarantine-commit-confirmed',b'CONFIRMED\n'))
 for q in ["SELECT COUNT(*) FROM u_usuario WHERE registro_estado<>'ADMINISTRADO' AND usuario_estado<>0","SELECT COUNT(*) FROM u_codigo_acceso c JOIN u_usuario u ON u.usuario_id=c.usuario_id WHERE u.registro_estado<>'ADMINISTRADO' AND c.usado=0","SELECT COUNT(*) FROM u_correo_codigo m JOIN u_codigo_acceso c ON c.codigo_id=m.codigo_id JOIN u_usuario u ON u.usuario_id=c.usuario_id WHERE u.registro_estado<>'ADMINISTRADO' AND m.contenido_cifrado IS NOT NULL"]:
  R(sql(q)==[['0']],'QUARANTINE_POSTCOMMIT_NOT_CONFIRMED')
 if (FOLDER/'baseline-private.json').exists():
  base=json.loads((FOLDER/'baseline-private.json').read_text());R(managed(base['schema'])==base['managed'],'MANAGED_ACCOUNTS_CHANGED');R(d.prep.privileges(ctx['db_user'])==base['privileges'],'PRIVILEGES_CHANGED')
 return {'required':True,'transaction':'COMMITTED_AND_INDEPENDENTLY_CONFIRMED'}
def fail_closed(ctx):
 result={'backend_stopped':False,'gmail_registration_disabled':False,'maintenance_verified':False}
 try:d.stopped();result['backend_stopped']=d.show()['MainPID']=='0'
 except BaseException:pass
 try:
  current=d.prep.assignments(ENV.read_bytes());saved=d.prep.assignments((FOLDER/'original-staging.env').read_bytes())
  immutable=set(saved)-set(SAFE)-set(QUOTAS)-set(MAIL)-{REG}
  R(all(current.get(k)==saved[k] for k in immutable),'CUSTODY_CONCURRENT_CHANGE')
  closed,_=configure((FOLDER/'original-staging.env').read_bytes(),False);atomic(ENV,closed)
  result['gmail_registration_disabled']=True
 except BaseException:pass
 try:route(ctx,True);result['maintenance_verified']=True
 except BaseException:pass
 return result
def rollback(ctx):
 result={'status':'ROLLBACK_UNKNOWN','database_restored':False,'attachments_restored':False}
 try:
  result.update(fail_closed(ctx));R(all(result[k] for k in ('backend_stopped','gmail_registration_disabled','maintenance_verified')),'SAFE_CLOSURE_NOT_CONFIRMED');closed_checks(ctx)
  probe=ctx['restore_schema'];R(re.fullmatch('dima_bug_staging_restore_[a-f0-9]{12}',probe),'UNSAFE_RESTORE_PROBE')
  if sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"+probe+"'")==[['1']]:
   R((FOLDER/'restore-probe-owned').is_file() and (FOLDER/'restore-probe-owned').read_text()==probe,'RESTORE_PROBE_OWNERSHIP_UNKNOWN')
   deadline=time.monotonic()+60
   while sql("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB='"+probe+"'")!=[['0']]:R(time.monotonic()<deadline,'RESTORE_PROBE_SESSION_UNKNOWN');time.sleep(.2)
   sql('DROP DATABASE `'+probe+'`');R(sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"+probe+"'")==[['0']],'RESTORE_PROBE_CLEANUP_UNKNOWN')
  result['quarantine']=quarantine_before_legacy(ctx)
  R(digest(PROFILE)==ctx['profile_sha'],'PROFILE_CONCURRENT_CHANGE')
  if (FOLDER/'BACKUPS_VERIFIED').exists():
   R(digest(FOLDER/'backend.jar')==OLD,'OLD_BACKUP_INVALID');atomic(JAR,(FOLDER/'backend.jar').read_bytes(),**ctx['jar_attrs'])
   if d.frontend_tree(FRONT)!=ctx['old_frontend']:
    restored=Path(ctx['work'])/('restored-'+uuid.uuid4().hex[:8]);d.unpack_frontend(FOLDER/'frontend.tar.gz',restored,ctx['old_frontend'],backup=True)
    if FRONT.exists():os.rename(FRONT,Path(ctx['work'])/('failed-'+uuid.uuid4().hex[:8]))
    os.rename(restored,FRONT)
  else:R(digest(JAR)==OLD and d.frontend_tree(FRONT)==ctx['old_frontend'],'UNVERIFIED_BACKUP_OR_ACTIVE_COMPONENTS')
  closed,env=configure((FOLDER/'original-staging.env').read_bytes(),False);atomic(ENV,closed)
  R(digest(JAR)==OLD and d.frontend_tree(FRONT)==ctx['old_frontend'],'OLD_PAIR_RESTORE_FAILED')
  actual=boot(env,OLD,None);role_probes(actual)
  R(all(actual.get(k)=='false' for k in MAIL+[REG]),'OLD_MAIL_OR_REGISTRATION_ENABLED')
  result.update(status='ROLLBACK_VERIFIED_MAINTENANCE',backend_restored=True,frontend_restored=True,configuration_restored_with_mail_registration_disabled=True,backend_active=True,maintenance_kept=True)
 except BaseException as error:
  result.update(fail_closed(ctx));result['failure_code']=safe_error(error);result['manual_review_required']=True
 write(FOLDER/'rollback-result.json',result);return result
def safe_error(error):return str(error) if re.fullmatch('[A-Z_]+',str(error)) else type(error).__name__
def guardian(ctx,parent,tick):
 os.setsid()
 with open(os.devnull,'r+b',buffering=0) as n:
  for fd in (0,1,2):os.dup2(n.fileno(),fd)
 write(FOLDER/'guardian-ready.json',{'pid':os.getpid(),'deadline_seconds':1200,'quarantine_mandatory_before_legacy':True})
 began=time.monotonic()
 while True:
  if (FOLDER/'DEPLOY_COMMITTED').exists():write(FOLDER/'guardian-finished.json',{'committed':True});os._exit(0)
  if (FOLDER/'rollback.request').exists() or not d.parent_alive(parent,tick) or time.monotonic()-began>1200:
   if not (FOLDER/'parent-parked').exists() and d.parent_alive(parent,tick):
    os.kill(parent,signal.SIGTERM);time.sleep(.3)
   op=FOLDER/'active-operation.json'
   if op.exists():
    item=json.loads(op.read_text())
    try:
     if start_tick(item['pid'])==item['start'] and os.getpgid(item['pid'])==item['pid']:os.killpg(item['pid'],signal.SIGKILL)
    except (ProcessLookupError,FileNotFoundError):pass
    op.unlink(missing_ok=True)
   try:rollback(ctx)
   except BaseException as error:write(FOLDER/'rollback-result.json',{'status':'UNKNOWN','failure_code':safe_error(error),**fail_closed(ctx)})
   os._exit(0)
  time.sleep(.25)
def preflight(payload):
 R(os.geteuid()==0,'ROOT_REQUIRED');R(payload.resolve().is_relative_to(Path('/var/lib/dimabug/staging-release-payloads')),'WRONG_PAYLOAD_LOCATION')
 R(socket.gethostname()=='casol-server','WRONG_SERVER_HOSTNAME')
 ips=json.loads(run(['ip','-j','-4','addr','show','scope','global']))
 R(any(x.get('local')=='157.230.187.214' for i in ips for x in i.get('addr_info',[])),'WRONG_SERVER_IP')
 manifest=json.loads((payload/'deployment-manifest.json').read_text());R(manifest['release']==RELEASE,'WRONG_RELEASE')
 for name,h in manifest['files'].items():R(digest(payload/name)==h,'PAYLOAD_HASH_CHANGED')
 R(digest(payload/'backend.jar')==NEW and digest(payload/'frontend.zip')==FRONT_SHA and digest(payload/'migration.sql')==SQL_SHA,'WRONG_ARTIFACTS')
 R(digest(JAR)==OLD,'INSTALLED_VERSION_CHANGED');env=runtime();R(attrs(ENV)=={'mode':0o600,'uid':0,'gid':0},'ENV_PERMISSIONS')
 for path in [JAR,ENV,PROFILE,FRONT,Path('/opt/dimabug/staging/uploads/materiales'),Path('/var/lib/dimabug/staging/tickets')]:R(path.resolve()==path,'STAGING_PATH_SYMLINK')
 R(env.get('DIMABUG_STORAGE_MATERIALES_DIR')=='/opt/dimabug/staging/uploads/materiales' and env.get('DIMABUG_STORAGE_TICKETS_DIR')=='/var/lib/dimabug/staging/tickets','STORAGE_CONFIGURATION_CHANGED')
 R(all(env.get(k)==v for k,v in d.prep.assignments(ENV.read_bytes()).items()),'RUNTIME_FILE_MISMATCH')
 R(d.external_session_count(env['DB_USERNAME'],d.show()['MainPID'])==0,'EXTERNAL_DATABASE_CLIENTS');R(d.app_sessions(env['DB_USERNAME'])>0,'EXPECTED_MYSQL_POOL_MISSING')
 R(sql('SELECT @@port')==[['3306']],'MYSQL_PORT_MISMATCH');R(sql("SELECT VERSION()")[0][0].startswith('8.0.46'),'MYSQL_VERSION_CHANGED')
 R(sql("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='u_usuario' AND COLUMN_NAME='registro_estado'")==[['0']],'MIGRATION_ALREADY_PRESENT')
 R(sql("SELECT COUNT(*) FROM (SELECT LOWER(TRIM(usuario_email)) FROM u_usuario GROUP BY LOWER(TRIM(usuario_email)) HAVING COUNT(*)>1) x")==[['0']],'DUPLICATE_EMAILS')
 R(sql("SELECT COUNT(*) FROM u_rol WHERE BINARY rol_nombre=BINARY 'USUARIO' AND rol_estado=1")==[['1']],'USUARIO_ROLE_AMBIGUOUS');queue_clear()
 R(sql("SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA=DATABASE() AND STATUS='ENABLED'")==[['0']],'DATABASE_EVENTS_ENABLED')
 for k in ('/','/opt/dimabug/staging','/var/www','/var/backups'):R(shutil.disk_usage(k).free>2*1024**3,'INSUFFICIENT_DISK')
 props={k.strip():v.strip() for line in PROFILE.read_text().splitlines() if line.strip() and not line.lstrip().startswith('#') and '=' in line for k,v in [line.split('=',1)]}
 R(props.get('spring.jpa.hibernate.ddl-auto')=='validate' and props.get('spring.sql.init.mode')=='never' and props.get('spring.jpa.generate-ddl')=='false' and not any('schema-generation.database.action' in k for k in props),'HIBERNATE_VALIDATE_NOT_EFFECTIVE')
 R(props.get('spring.jpa.properties.hibernate.hbm2ddl.auto','validate')=='validate','HBM_ACTION_OVERRIDE')
 meta=d.prep.metadata();req=json.loads((payload/'orm-requirements.json').read_text());review=json.loads((payload/'schema-review-policy.json').read_text())['reviewed_varchar_contracts']
 R(d.compare(req,**meta,reviewed_contracts=review)['compatible'],'UNEXPECTED_CURRENT_SCHEMA_INCOMPATIBILITY')
 R(d.frontend_tree(FRONT)==json.loads((payload/'old-frontend-tree.json').read_text()),'CURRENT_FRONTEND_CHANGED')
 R(run(['systemctl','show','dimabug@staging7.service','-p','MainPID','--value']).strip()==b'0','STAGING7_NOT_STOPPED')
 ng=Path('/etc/nginx/sites-enabled/dimabug-staging').resolve(strict=True);d.maintenance(ng.read_bytes())
 R('staging' in ng.name,'WRONG_NGINX_FILE');run(['nginx','-t'])
 return env,ng
CONTEXT={}
def execute(payload,deployment_id):
 global PAYLOAD,FOLDER,CONTEXT
 PAYLOAD=payload;os.umask(0o077);R(re.fullmatch('registro-universal-20261115-[0-9TZ]+-[a-f0-9]{8}',deployment_id),'INVALID_DEPLOYMENT_ID')
 d.cmd=run;d.prep.command=run;d.prep.sql=sql
 env,ng=preflight(payload)
 lock=os.open('/run/dimabug-staging-release.lock',os.O_CREAT|os.O_RDWR,0o600);fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
 FOLDER=Path('/var/backups/dimabug-staging')/deployment_id;FOLDER.mkdir(mode=0o700)
 atomic(FOLDER/'original-staging.env',ENV.read_bytes());atomic(FOLDER/'nginx.conf',ng.read_bytes())
 work=Path('/var/www')/('.'+deployment_id);work.mkdir(mode=0o755);os.chmod(work,0o755)
 ctx={'deployment_id':deployment_id,'nginx':str(ng),'nginx_attrs':attrs(ng),'jar_attrs':attrs(JAR),'profile_sha':digest(PROFILE),'db_user':env['DB_USERNAME'],'mysql_uuid':sql('SELECT @@server_uuid')[0][0],'old_frontend':d.frontend_tree(FRONT),'trees':{},'work':str(work),'restore_schema':'dima_bug_staging_restore_'+uuid.uuid4().hex[:12],'sql_marker':'DIMABUG_MIGRATION_'+uuid.uuid4().hex}
 CONTEXT=ctx;write(FOLDER/'context-private.json',ctx)
 parent=os.getpid();tick=start_tick(parent);guard=os.fork()
 if guard==0:
  try:guardian(ctx,parent,tick)
  except BaseException as error:
   try:write(FOLDER/'rollback-result.json',{'status':'UNKNOWN','failure_code':safe_error(error),**fail_closed(ctx)})
   except BaseException:pass
  os._exit(1)
 report={'deployment_id':deployment_id,'release':RELEASE,'report_directory':str(FOLDER),'status':'DEPLOYMENT_NOT_VERIFIED','otp_requests_by_controller':0,'real_accounts_created_by_controller':0,'database_restored':False,'attachments_restored':False}
 try:
  deadline=time.monotonic()+5
  while not (FOLDER/'guardian-ready.json').exists():R(time.monotonic()<deadline,'GUARDIAN_NOT_READY');time.sleep(.1)
  d.check_guard(guard);print(json.dumps({'phase':'QUARANTINE_GUARDIAN_READY','deployment_id':deployment_id,'report_directory':str(FOLDER)}),flush=True)
  phase('MAINTENANCE_AND_STOP_ONLY_STAGING');atomic(FOLDER/'mutation-started',b'AUTHORIZED\n');route(ctx,True);d.stopped();closed_checks(ctx);queue_clear()
  base,backups=backup(ctx);d.check_guard(guard)
  phase('APPLY_APPROVED_MIGRATION_ONCE');atomic(FOLDER/'migration-started',b'ONCE_NO_RETRY\n')
  migration=(payload/'migration.sql').read_text();statements=[x.strip() for x in re.sub(r'(?m)^--.*$','',migration).split(';') if x.strip()]
  sqltext='\n'.join('/* '+ctx['sql_marker']+' */ '+x+';' for x in statements)
  run(client(),input=sqltext.encode(),timeout=180);migrated(base);atomic(FOLDER/'migration-confirmed',b'CONFIRMED\n')
  phase('INSTALL_APPROVED_PAIR_REGISTRATION_AND_MAIL_OFF')
  newfront=json.loads((payload/'new-frontend-tree.json').read_text());dest=work/'new-frontend';d.unpack_frontend(payload/'frontend.zip',dest,newfront)
  atomic(JAR,(payload/'backend.jar').read_bytes(),**ctx['jar_attrs'])
  os.rename(FRONT,work/'previous-frontend');os.rename(dest,FRONT);d.verify_nginx_frontend_readable(d.nginx_worker_user())
  dry,dryenv=configure((FOLDER/'original-staging.env').read_bytes(),False);R(ENV.read_bytes()==(FOLDER/'original-staging.env').read_bytes(),'ENV_CONCURRENT_CHANGE');atomic(ENV,dry)
  actual=boot(dryenv,NEW,guard);off=bindings(actual,False);permissions=role_probes(actual);migrated(base);queue_clear()
  phase('ENABLE_UNIVERSAL_REGISTRATION_AND_AUTH_ONLY_PERSISTENTLY')
  d.check_guard(guard);d.stopped();closed_checks(ctx);live,liveenv=configure((FOLDER/'original-staging.env').read_bytes(),True);R(ENV.read_bytes()==dry,'ENV_CONCURRENT_CHANGE');atomic(ENV,live)
  actual=boot(liveenv,NEW,guard);effective=bindings(actual,True);permissions=role_probes(actual);migrated(base);queue_clear()
  R(ENV.read_bytes()==live and digest(JAR)==NEW and d.frontend_tree(FRONT)==newfront,'INSTALLED_COMPONENT_CHANGED')
  R(run(['systemctl','cat',UNIT])==(FOLDER/'systemd-staging.txt').read_bytes() and digest(PROFILE)==ctx['profile_sha'],'UNIT_OR_PROFILE_CHANGED')
  R(run(['systemctl','show','dimabug@staging7.service','-p','MainPID','--value']).strip()==b'0','STAGING7_CHANGED')
  # All critical checks precede public opening. No valid OTP/body/real account is ever submitted.
  phase('OPEN_VERIFIED_STAGING_AND_PUBLIC_CONTRACTS');d.check_guard(guard);route(ctx,False)
  probes=[]
  for origin,public in ((True,False),(False,True)):
   for path in ('/','/login','/solicitudes'):
    code,body=d.request(path,origin=origin,public=public);R(code==200 and hashlib.sha256(body).hexdigest()==newfront['index.html'],'PUBLIC_ANGULAR_MISMATCH')
   for path,status,body in [('/api/health',200,None),('/api/auth/me',401,None),('/api/auth/codigo',400,{}),('/api/auth/codigo/verificar',400,{})]:probes.append(d.contract_probe(path,status,body,origin,public))
  d.check_guard(guard);R(runtime(liveenv).get(REG)=='true','REGISTRATION_NOT_PERSISTENT')
  report.update(status='DEPLOYMENT_VERIFIED',backups=backups,migration={'sha256':SQL_SHA,'confirmed':True,'existing_data_preserved':True,'constraints_indexes_verified':True},backend_sha256=digest(JAR),frontend_zip_sha256=FRONT_SHA,frontend_tree_verified=True,backend_active=True,hibernate_validate=True,mysql_connected=True,angular_https_verified=True,nginx_verified=True,registration_enabled=True,effective_bindings=effective,auth_gmail=True,welcome=False,critical_ticket=False,smtp_excluded=True,automation=False,custody_preserved=True,privileges_preserved=True,attachments_preserved=True,staging7_stopped=True,configuration_persistence_after_restart_verified=True,existing_account_role='TECNICO',existing_authorization_probes=permissions,public_probes=probes,real_otp_end_to_end='NOT_REQUESTED_BY_AUTHORIZATION',maintenance=False)
  write(FOLDER/'result-sanitized.json',report);atomic(FOLDER/'DEPLOY_COMMITTED',b'CONFIRMED\n');os.waitpid(guard,0)
  print(json.dumps(report),flush=True);return True
 except BaseException as error:
  report.update(failure_phase=PHASE,failure_code=safe_error(error));write(FOLDER/'failure-sanitized.json',report)
  atomic(FOLDER/'parent-parked',b'PARKED\n');atomic(FOLDER/'rollback.request',b'REQUESTED\n')
  deadline=time.monotonic()+420
  while not (FOLDER/'rollback-result.json').exists() and time.monotonic()<deadline and d.child_running(guard):time.sleep(.3)
  if (FOLDER/'rollback-result.json').exists():report['rollback']=json.loads((FOLDER/'rollback-result.json').read_text())
  else:report['rollback']={'status':'UNKNOWN',**fail_closed(ctx)}
  write(FOLDER/'result-sanitized.json',report);print(json.dumps(report),flush=True);return False
 finally:os.close(lock)

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--execute-authorized-staging',action='store_true');p.add_argument('--payload',type=Path);p.add_argument('--deployment-id');a=p.parse_args()
 if not a.execute_authorized_staging:print('{"status":"PREPARED_NO_ACTION"}')
 else:
  try:ok=execute(a.payload,a.deployment_id)
  except BaseException as error:print(json.dumps({'status':'STOPPED_BEFORE_MUTATION','phase':PHASE,'failure_code':safe_error(error),'automatic_retry':False}));raise SystemExit(1)
  raise SystemExit(0 if ok else 1)
