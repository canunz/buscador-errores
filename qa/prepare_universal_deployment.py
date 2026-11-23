"""Build only the deployment tools; never rebuild or alter the approved release."""
from pathlib import Path
import base64,datetime,hashlib,json,os,re,shutil,struct,subprocess,sys,uuid,zipfile
R=Path(__file__).resolve().parents[1];A=R/'qa/artifacts/registro-universal-20261115';V=R/'qa/artifacts/registro-universal-deploy-20261115'
V.mkdir(exist_ok=True);tools=V/'payload/tools';tools.mkdir(parents=True,exist_ok=True)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(A/'artifacts/backend.jar')=='1ae8d4dd644ac5f53bfa1bf68f7f84be64e9c60d9ca112d56051cd26a79c2647'
assert sha(A/'artifacts/frontend-production.zip')=='aded6a14740376801d3763242c6b204f90d7dacbbf8b8c15061d300ad5ca4d73'
old=R/'qa/artifacts/staging-auth-isolation-deploy-20261114'
for n in ['deploy_staging.py','staging_prepare.py','schema_compat.py']:shutil.copy2(old/n,tools/n)
for n in ['deploy_universal.py','deployment_sql_session.py','DeploymentProbe.java']:shutil.copy2(R/'qa/verification'/n,tools/n)
for source,dest in [('backend.jar','backend.jar'),('frontend-production.zip','frontend.zip'),('V20261115_01__registro_universal.sql','migration.sql')]:shutil.copy2(A/'artifacts'/source,V/'payload'/dest)
shutil.copy2(R/'qa/verification/rollback_quarantine.sql',V/'payload/rollback_quarantine.sql')
for n in ['orm-requirements.json','schema-review-policy.json']:shutil.copy2(R/'qa/artifacts/staging-prep-20261114'/n,V/'payload'/n)
for archive,out in [(A/'artifacts/frontend-production.zip','new-frontend-tree.json'),(R/'qa/artifacts/staging-minimo-20261114T025917Z/artifacts/frontend-production.zip','old-frontend-tree.json')]:
 with zipfile.ZipFile(archive) as z:inventory={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/')}
 (V/'payload'/out).write_text(json.dumps(inventory,sort_keys=True),encoding='utf-8')
build=V/'probe-build';classes=build/'classes';classes.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(A/'artifacts/backend.jar') as z:
 for n in z.namelist():
  if n.startswith(('BOOT-INF/classes/','BOOT-INF/lib/')) and not n.endswith('/'):
   p=build/n;p.parent.mkdir(parents=True,exist_ok=True)
   if not p.exists():p.write_bytes(z.read(n))
cp=os.pathsep.join([str(build/'BOOT-INF/classes'),str(build/'BOOT-INF/lib/*')])
jdk=Path(r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin')
env={k:os.environ[k] for k in ['SystemRoot','WINDIR','SystemDrive','USERPROFILE','TEMP','TMP'] if k in os.environ};env['PATH']=str(jdk)+os.pathsep+str(Path(env['SystemRoot'])/'System32')
p=subprocess.run([str(jdk/'javac.exe'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(tools/'DeploymentProbe.java')],env=env,capture_output=True,text=True)
assert p.returncode==0,'PROBE_COMPILATION_FAILED'
probe=tools/'probe.jar'
with zipfile.ZipFile(probe,'w',zipfile.ZIP_DEFLATED) as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
testprofile=build/'test-profile.properties';testprofile.write_text('dimabug.gmail.envio-real-habilitado=${GMAIL_ENVIO_REAL_HABILITADO:false}\n',encoding='utf-8')
evidence={'no_real_credentials_inherited':True,'no_mysql_or_network':True,'bindings':[]}
for enabled in [False,True]:
 child=env|{'DIMABUG_AUTH_REGISTRO_UNIVERSAL_HABILITADO':str(enabled).lower(),'DIMABUG_AUTH_CORREO_HABILITADO':str(enabled).lower(),'GMAIL_ENVIO_REAL_HABILITADO':str(enabled).lower(),'DIMABUG_CORREO_BIENVENIDA_HABILITADO':'false','DIMABUG_CORREO_TICKET_CRITICO_HABILITADO':'false'}
 p=subprocess.run([str(jdk/'java.exe'),'-Xmx96m','-cp',str(probe)+os.pathsep+cp,'DeploymentProbe','bindings',str(testprofile.resolve())],env=child,capture_output=True,text=True,encoding='utf-8')
 assert p.returncode==0,'PROBE_BINDING_FAILED'
 result=json.loads(next(x for x in p.stdout.splitlines() if x.startswith('{')))
 assert result=={'habilitado':enabled,'hora':20,'dia':100,'pendientes':200,'auth':enabled,'gmail':enabled,'welcome':False,'critical':False},'PROBE_BINDING_MISMATCH'
 evidence['bindings'].append(result)
key=b'synthetic-deployment-probe-only-key-12345678901234567890123456789012';email=b'probe@example.invalid'
p=subprocess.run([str(jdk/'java.exe'),'-cp',str(probe)+os.pathsep+cp,'DeploymentProbe','token'],input=struct.pack('>I',len(key))+key+struct.pack('>I',len(email))+email,env=env,capture_output=True)
assert p.returncode==0 and any(re.fullmatch(rb'[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+',x) for x in p.stdout.splitlines()),'TOKEN_PROBE_FAILED'
evidence.update(token_probe_verified_without_disclosure=True,verified=True)
(V/'probe-tests.json').write_text(json.dumps(evidence,indent=2),encoding='utf-8')
print(json.dumps({'tools_prepared':True,'jvm_probe_tests_verified':True,'approved_artifacts_unchanged':True,'directory':str(V)}))
