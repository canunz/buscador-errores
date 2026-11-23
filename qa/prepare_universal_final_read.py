from pathlib import Path
import base64,json,zlib
R=Path(__file__).resolve().parents[1];V=R/'qa/artifacts/registro-universal-deploy-20261115'
plan=json.loads((V/'transfer-plan.json').read_text());identity=plan['deployment_id']
code=r'''import json,hashlib,stat,subprocess,gzip
from pathlib import Path
f=Path('/var/backups/dimabug-staging')/IDENTITY
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
try:
 r=json.loads((f/'result-sanitized.json').read_text());c=json.loads((f/'context-private.json').read_text())
 assert r['status']=='DEPLOYMENT_VERIFIED' and (f/'DEPLOY_COMMITTED').is_file()
 assert json.loads((f/'guardian-finished.json').read_text()).get('committed') is True
 assert not (f/'rollback-result.json').exists()
 st=f.stat();assert st.st_uid==0 and st.st_gid==0 and stat.S_IMODE(st.st_mode)==0o700
 expected={k:v['sha256'] for k,v in r['backups'].items() if k.endswith(('.jar','.env','.properties'))}
 expected.update({k+'.tar.gz':r['backups'][k]['sha256'] for k in ('frontend','materiales','tickets')})
 expected['database.sql.gz']=r['backups']['database']['sha256'];expected['database.sql']=r['backups']['database']['uncompressed_sha256']
 assert all(sha(f/k)==h for k,h in expected.items())
 assert all(stat.S_IMODE((f/k).stat().st_mode)==0o600 and (f/k).stat().st_uid==0 for k in expected)
 with gzip.open(f/'database.sql.gz','rb') as h:assert hashlib.file_digest(h,'sha256').hexdigest()==expected['database.sql']
 assert (f/'nginx.conf').read_bytes()==Path(c['nginx']).read_bytes()
 p=subprocess.run(['systemctl','cat','dimabug@staging.service'],capture_output=True);assert p.returncode==0 and p.stdout==(f/'systemd-staging.txt').read_bytes()
 for n in ('nginx.conf','systemd-staging.txt'):expected[n]=sha(f/n)
 assert all(stat.S_IMODE((f/k).stat().st_mode)==0o600 and (f/k).stat().st_uid==0 for k in expected)
 pid=subprocess.check_output(['systemctl','show','dimabug@staging.service','-p','MainPID','--value'],text=True).strip();assert pid!='0'
 env=dict(x.decode().split('=',1) for x in Path('/proc',pid,'environ').read_bytes().split(b'\0') if b'=' in x)
 def values(path):
  result={}
  for line in path.read_text().splitlines():
   line=line.strip()
   if line and not line.startswith('#'):
    k,v=line.split('=',1)
    if len(v)>1 and v[0]==v[-1] and v[0] in (chr(34),chr(39)):v=v[1:-1]
    result[k]=v
  return result
 current=values(Path('/etc/dimabug/staging.env'));original=values(f/'original-staging.env')
 assert all(env.get(k)==v for k,v in current.items())
 for k in ('GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET','GMAIL_TOKEN_ENCRYPTION_KEY','GMAIL_SENDER_EMAIL','DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','JWT_EXPIRATION'):assert current.get(k)==original.get(k)
 for k in ('DIMABUG_AUTH_REGISTRO_UNIVERSAL_HABILITADO','DIMABUG_AUTH_CORREO_HABILITADO','GMAIL_ENVIO_REAL_HABILITADO','DIMABUG_GMAIL_ENVIO_REAL_HABILITADO'):assert current[k]=='true'
 for k in ('DIMABUG_CORREO_BIENVENIDA_HABILITADO','DIMABUG_CORREO_TICKET_CRITICO_HABILITADO','DIMABUG_AUTOMATIZACION_SEGUIMIENTO_HABILITADO'):assert current[k]=='false'
 assert current['DIMABUG_AUTOMATIZACION_MODO']=='DESHABILITADO'
 assert sha(Path('/opt/dimabug/staging/backend.jar'))==r['backend_sha256']
 sql="SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"+c['restore_schema']+"';"
 out=subprocess.run(['mysql','--protocol=socket','--user=root','--batch','--skip-column-names'],input=sql,text=True,capture_output=True);assert out.returncode==0 and out.stdout.strip()=='0'
 print(json.dumps({'status':'FINAL_PRIVATE_RECEIPTS_VERIFIED','deployment_id':IDENTITY,'guardian_commit_acknowledged':True,'rollback_executed':False,'backup_hashes':expected,'backup_root_private_0700':True,'backup_files_0600':True,'dump_gzip_verified':True,'nginx_backup_matches_current':True,'systemd_backup_matches_current':True,'restore_probe_removed':True,'runtime_matches_persistent_environment':True,'oauth_db_jwt_custody_unchanged':True,'registration_enabled':True,'only_auth_mail_enabled':True,'read_only':True,'codes_requested':0}))
except BaseException as e:
 print(json.dumps({'status':'FINAL_PRIVATE_READ_INCOMPLETE','category':type(e).__name__,'read_only':True}))
'''
code=code.replace('IDENTITY',repr(identity));compile(code,'readonly-receipts','exec')
encoded=base64.b64encode(zlib.compress(code.encode(),9)).decode()
remote="sudo python3 -I -B -c \"import base64,zlib;exec(zlib.decompress(base64.b64decode('"+encoded+"')))\""
ps=r"""$ErrorActionPreference='Stop'
& ssh -tt -o StrictHostKeyChecking=yes -o UpdateHostKeys=no -o HostKeyAlgorithms=ssh-ed25519 -o IdentitiesOnly=yes -o ForwardAgent=no -i "$env:USERPROFILE\.ssh\casol_admin" carolina@157.230.187.214 """+"'"+remote.replace("'","''")+"'\n"
(V/'Read-Deployment-FinalReceipts.ps1').write_text(ps,encoding='utf-8-sig')
print(json.dumps({'read_only_launcher':str(V/'Read-Deployment-FinalReceipts.ps1'),'compiled':True}))
