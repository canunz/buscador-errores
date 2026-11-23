"""Run only with sudo in the existing staging SSH session; stdout is sanitized JSON."""
import base64,hashlib,json,os,re,shlex,shutil,socket,stat,subprocess,urllib.request
from pathlib import Path
from urllib.parse import urlsplit

def cmd(args):
 p=subprocess.run(args,capture_output=True,text=True,timeout=25)
 if p.returncode:raise RuntimeError('READ_COMMAND_FAILED')
 return p.stdout
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def assignments(path):
 out={}
 for line in path.read_text().splitlines():
  line=line.strip()
  if not line or line.startswith('#'):continue
  k,v=line.split('=',1)
  if k in out:raise RuntimeError('DUPLICATE_ENVIRONMENT_KEY')
  parsed=shlex.split(v);out[k]=parsed[0] if len(parsed)==1 else v
 return out
def sql(q):
 p=subprocess.run(['mysql','--batch','--raw','--skip-column-names'],input=q,text=True,capture_output=True,timeout=25)
 if p.returncode:raise RuntimeError('READ_ONLY_SQL_FAILED')
 return [x.split('\t') for x in p.stdout.splitlines()]
def emit(d):print(json.dumps(d),flush=True)
try:
 assert socket.gethostname()=='casol-server','SERVER_IDENTITY'
 state=dict(x.split('=',1) for x in cmd(['systemctl','show','dimabug@staging.service','-p','Id','-p','ActiveState','-p','SubState','-p','MainPID','-p','NRestarts','-p','EnvironmentFiles']).splitlines() if '=' in x)
 assert state['ActiveState']=='active' and int(state['MainPID'])>0,'STAGING_NOT_RUNNING'
 assert state['EnvironmentFiles']=='/etc/dimabug/staging.env (ignore_errors=no)','ENVIRONMENT_FILE_CHANGED'
 proc=Path('/proc')/state['MainPID'];assert (proc/'cwd').resolve()==Path('/opt/dimabug/staging'),'WRONG_CWD'
 args=(proc/'cmdline').read_bytes().split(b'\0');assert b'/opt/dimabug/staging/backend.jar' in args,'WRONG_JAR'
 assert not any(x.startswith((b'-Dspring.',b'--spring.',b'-Ddimabug.',b'--dimabug.',b'-Djwt.',b'--jwt.')) for x in args),'UNREVIEWED_JVM_OVERRIDES'
 env=dict(x.decode().split('=',1) for x in (proc/'environ').read_bytes().split(b'\0') if b'=' in x)
 db=urlsplit(env.get('DB_URL','').removeprefix('jdbc:'))
 assert db.scheme=='mysql' and db.hostname in ('localhost','127.0.0.1') and db.path=='/dima_bug_staging','WRONG_DATABASE'
 assert env.get('SPRING_PROFILES_ACTIVE')=='staging' and env.get('SERVER_PORT')=='8081','WRONG_PROFILE_OR_PORT'
 assert not any(env.get(k) for k in ['SPRING_CONFIG_LOCATION','SPRING_CONFIG_NAME','SPRING_PROFILES_INCLUDE','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','JAVA_OPTS']),'UNREVIEWED_CONFIG_OVERRIDES'
 properties=json.loads(env.get('SPRING_APPLICATION_JSON') or '{}')
 # Report only property names, never values; any non-logging JSON requires review.
 def flat(d,p=''):
  out={}
  for k,v in d.items():
   key=(p+'.'+k).strip('.')
   if isinstance(v,dict):out.update(flat(v,key))
   else:out[key]=v
  return out
 jsonprops=flat(properties)
 json_safe=set(jsonprops)<={'logging.level.cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter','logging.level.cl.casol.backend.identidad.infrastructure.security.JwtTokenServiceAdapter'}
 cfgdir=env.get('SPRING_CONFIG_ADDITIONAL_LOCATION','')
 assert cfgdir=='file:/etc/dimabug-staging-releases/staging-minimo-20261114T025917Z/','CONFIG_PATH_CHANGED'
 config=Path(cfgdir.removeprefix('file:'))/'application-staging.properties';assert config.is_file(),'CONFIG_MISSING'
 disk=assignments(Path('/etc/dimabug/staging.env'))
 required=['DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','JWT_EXPIRATION','GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET','GMAIL_TOKEN_ENCRYPTION_KEY','GMAIL_SENDER_EMAIL']
 version=sql('SELECT VERSION(),@@port,@@character_set_server,@@collation_server;')[0]
 assert int(version[1])==(db.port or 3306),'MYSQL_PORT_CHANGED'
 columns={};engines={};indexes={};foreign={}
 for table,name,typ,nullable in sql("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dima_bug_staging' ORDER BY TABLE_NAME,ORDINAL_POSITION;"):
  columns.setdefault(table,{})[name]={'type':typ,'nullable':nullable=='YES'}
 for table,engine in sql("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_staging';"):engines[table]=engine
 grouped={}
 for table,name,unique,kind,sequence,column in sql("SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,INDEX_TYPE,SEQ_IN_INDEX,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dima_bug_staging' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;"):
  row=grouped.setdefault((table,name),{'name':name,'unique':unique=='0','type':kind,'columns':[]});row['columns'].append(column)
 for (table,name),row in grouped.items():indexes.setdefault(table,[]).append(row)
 grouped={}
 for table,name,column,ref,refcolumn in sql("SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='dima_bug_staging' AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION;"):
  row=grouped.setdefault((table,name),{'columns':[],'parent':ref,'parent_columns':[]});row['columns'].append(column);row['parent_columns'].append(refcolumn)
 for (table,name),row in grouped.items():foreign.setdefault(table,[]).append(row)
 requirements=PAYLOAD['requirements']
 requirements['tables']['u_usuario']['columns']['registro_estado']={'sql_type':'varchar(24)','nullable':False,'unique':False}
 comparison=compare(requirements,columns,indexes,foreign,engines,reviewed_contracts=PAYLOAD['reviewed'])
 expected=[b for b in comparison['blockers'] if b.get('table')=='u_usuario' and b.get('column')=='registro_estado' and b.get('issue')=='MISSING_COLUMN']
 extra=[b for b in comparison['blockers'] if b not in expected]
 aggregate=sql("SELECT COUNT(*),SUM(usuario_estado=1),SUM(usuario_estado=0),SUM(usuario_email<>TRIM(usuario_email)),SUM(usuario_email NOT LIKE '%@%') FROM dima_bug_staging.u_usuario;")[0]
 duplicates=int(sql("SELECT COUNT(*) FROM (SELECT LOWER(TRIM(usuario_email)) FROM dima_bug_staging.u_usuario GROUP BY LOWER(TRIM(usuario_email)) HAVING COUNT(*)>1) d;")[0][0])
 canonical_role=int(sql("SELECT COUNT(*) FROM dima_bug_staging.u_rol WHERE BINARY rol_nombre=BINARY 'USUARIO' AND rol_estado=1;")[0][0])
 conflicts=sql("SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='dima_bug_staging' AND TABLE_NAME='u_usuario' AND CONSTRAINT_NAME IN ('chk_usuario_registro_estado','chk_usuario_registro_inactivo','uq_usuario_email_normalizado','idx_usuario_registro_creacion');")
 conflicts+=sql("SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dima_bug_staging' AND TABLE_NAME='u_usuario' AND INDEX_NAME IN ('uq_usuario_email_normalizado','idx_usuario_registro_creacion');")
 queue=sql("SELECT estado,COUNT(*),SUM(expira>NOW()),SUM(contenido_cifrado IS NOT NULL) FROM dima_bug_staging.u_correo_codigo GROUP BY estado;")
 oauth=sql("SELECT estado,version,access_token_cifrado IS NOT NULL,refresh_token_cifrado IS NOT NULL FROM dima_bug_staging.gm_cuenta_oauth;")
 userhex=env['DB_USERNAME'].encode().hex();grantee="CONCAT(CHAR(39),CONVERT(0x"+userhex+" USING utf8mb4),CHAR(39),'@%')"
 grants=sql("SELECT PRIVILEGE_TYPE,IS_GRANTABLE FROM information_schema.SCHEMA_PRIVILEGES WHERE TABLE_SCHEMA='dima_bug_staging' AND GRANTEE LIKE "+grantee+" ORDER BY PRIVILEGE_TYPE;")
 globalgrants=sql("SELECT PRIVILEGE_TYPE,IS_GRANTABLE FROM information_schema.USER_PRIVILEGES WHERE GRANTEE LIKE "+grantee+";")
 emit({'type':'schema','mysql_version':version[0],'tables':len(columns),'requirements_tables':len(requirements['tables']),'planned_missing_columns':expected,'unexpected_blockers':extra,'warnings':comparison['warnings'],'users_total':int(aggregate[0]),'users_active':int(aggregate[1]),'users_inactive':int(aggregate[2]),'emails_with_spaces':int(aggregate[3] or 0),'invalid_email_shape':int(aggregate[4] or 0),'normalized_duplicate_groups':duplicates,'canonical_active_usuario_roles':canonical_role,'new_object_name_conflicts':len(conflicts),'compatible_after_planned_migration':not extra and len(expected)==1 and duplicates==0 and canonical_role==1 and not conflicts})
 seven=cmd(['systemctl','show','dimabug@staging7.service','-p','ActiveState','-p','SubState','-p','MainPID'])
 stats=Path('/etc/dimabug/staging.env').stat();cfgtext=config.read_text()
 bindings={k:('${'+v) in cfgtext for k,v in {'db':'DB_URL','db_user':'DB_USERNAME','db_password':'DB_PASSWORD','jwt':'JWT_SECRET','gmail_client':'GOOGLE_CLIENT_ID','gmail_secret':'GOOGLE_CLIENT_SECRET','gmail_encryption':'GMAIL_TOKEN_ENCRYPTION_KEY','gmail_sender':'GMAIL_SENDER_EMAIL'}.items()}
 emit({'type':'runtime','backend_sha256':sha(Path('/opt/dimabug/staging/backend.jar')),'expected_backend':sha(Path('/opt/dimabug/staging/backend.jar'))=='a74f4750ee4c9b3c166449ab669655bd00bb14e2b234ccc3de0c903aa0597c8d','service_active':state['ActiveState']=='active','service_restarts':int(state['NRestarts']),'staging7_stopped':'MainPID=0' in seven and 'ActiveState=inactive' in seven,'profile_staging_only':True,'backend_port':8081,'database_staging_only':True,'environment_mode_0600':stat.S_IMODE(stats.st_mode)==0o600,'environment_root_owned':stats.st_uid==0 and stats.st_gid==0,'required_values_present':all(env.get(k) for k in required),'custody_runtime_matches_disk':all(env.get(k)==disk.get(k) for k in required),'json_overrides_logging_only':json_safe,'template_bindings':bindings,'gmail_auth_enabled':env.get('GMAIL_ENVIO_REAL_HABILITADO')=='true' and env.get('DIMABUG_AUTH_CORREO_HABILITADO')=='true','welcome_disabled':env.get('DIMABUG_CORREO_BIENVENIDA_HABILITADO','false')=='false','critical_disabled':env.get('DIMABUG_CORREO_TICKET_CRITICO_HABILITADO','false')=='false','smtp_excluded':'org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration' in env.get('SPRING_AUTOCONFIGURE_EXCLUDE','').split(','),'automation_disabled':env.get('DIMABUG_AUTOMATIZACION_MODO')=='DESHABILITADO' and env.get('DIMABUG_AUTOMATIZACION_SEGUIMIENTO_HABILITADO')=='false','registration_override_present':any('REGISTRO_UNIVERSAL' in k for k in env),'schema_privileges':grants,'global_privileges':globalgrants})
 front=Path('/var/www/dimabug-staging');actual={}
 for p in front.rglob('*'):
  if p.is_file():
   assert p.resolve().is_relative_to(front.resolve()),'FRONTEND_EXTERNAL_LINK';actual[p.relative_to(front).as_posix()]=sha(p)
 nginx=Path('/etc/nginx/sites-enabled/dimabug-staging').read_text()
 endpoints={}
 for label,url in [('origin_health','http://127.0.0.1:8081/api/health'),('public_health','https://staging.dimabug.casol.cl/api/health'),('angular','https://staging.dimabug.casol.cl/')]:
  try:
   with urllib.request.urlopen(url,timeout=8) as res:endpoints[label]=res.status
  except Exception:endpoints[label]=None
 spaces={str(p):{'free_bytes':shutil.disk_usage(p).free,'total_bytes':shutil.disk_usage(p).total} for p in ['/','/opt/dimabug/staging','/var/www','/var/backups']}
 backups=Path('/var/backups/dimabug-staging');dirs=[p for p in backups.iterdir() if p.is_dir()] if backups.is_dir() else []
 emit({'type':'availability','frontend_matches_deployed_release':actual==PAYLOAD['frontend'],'frontend_files':len(actual),'nginx_running':cmd(['systemctl','is-active','nginx']).strip()=='active','nginx_api_loopback_8081':bool(re.search(r'location\s+/api/\s*\{[^}]*proxy_pass\s+http://127\.0\.0\.1:8081\s*;',nginx)),'http':endpoints,'disk':spaces,'private_backup_directories':len(dirs),'fresh_backup_created':False,'queue':[{'state':s,'count':int(n),'valid':int(v),'encrypted_present':int(c)} for s,n,v,c in queue],'oauth':[{'state':s,'version':int(v),'access_present':a=='1','refresh_present':r=='1'} for s,v,a,r in oauth],'sql_writes':False,'services_modified':False,'oauth_modified':False,'codes_requested':0})
 emit({'status':'READ_ONLY_PREFLIGHT_COMPLETED','staging_modified':False})
except Exception as ex:
 emit({'status':'READ_ONLY_PREFLIGHT_INCOMPLETE','category':type(ex).__name__,'safe_code':str(ex) if re.fullmatch('[A-Z_]+',str(ex)) else 'SUPPRESSED','staging_modified':False})
