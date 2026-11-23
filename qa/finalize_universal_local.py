"""Inventory and package only the isolated candidate after successful checks."""
import datetime,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parents[1]
A=R/'qa/artifacts/registro-universal-20261115'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def files(p):
 for root,dirs,names in os.walk(p):
  dirs[:]=sorted(d for d in dirs if d not in {'node_modules','target','.angular','dist','.git'})
  for n in sorted(names):yield Path(root)/n
def inventory(p):return {f.relative_to(p).as_posix():sha(f) for f in files(p)}
def fingerprint(d):return hashlib.sha256(json.dumps(d,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def dump(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def zip_files(target,root,paths):
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for p in paths:
   info=zipfile.ZipInfo(p.relative_to(root).as_posix(),(2026,11,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
   z.writestr(info,p.read_bytes())
 with zipfile.ZipFile(target) as z:assert z.testzip() is None
evidence={}
for name in ['backend-tests','backend-ticket-regressions','build-tests','build-maintenance-tests','frontend-tests','frontend-build','build-package','native-mysql-migration']:
 p=A/'evidence'/f'{name}.json';d=json.loads(p.read_text(encoding='utf-8'))
 assert d.get('verified'),name
 evidence[name]={'path':p.relative_to(A).as_posix(),'sha256':sha(p)}
totals={'backend':331,'frontend':12}
assert sum(json.loads((A/'evidence'/f'{n}.json').read_text())['totals']['tests'] for n in ['backend-tests','backend-ticket-regressions','build-tests','build-maintenance-tests'])==totals['backend']
oldjar=R/'qa/artifacts/staging-auth-isolation-20261114/artifacts/backend.jar'
assert sha(oldjar)=='a74f4750ee4c9b3c166449ab669655bd00bb14e2b234ccc3de0c903aa0597c8d'
jars=list((A/'sources/dima-bug-backend/target').glob('*.jar'));assert len(jars)==1
backend=A/'artifacts/backend.jar';shutil.copy2(jars[0],backend)
with zipfile.ZipFile(oldjar) as z:old={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/')}
with zipfile.ZipFile(backend) as z:new={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/')}
assert {n:h for n,h in old.items() if n.startswith('BOOT-INF/lib/')}=={n:h for n,h in new.items() if n.startswith('BOOT-INF/lib/')}
assert not any('/whatsapp/' in n.lower() or '/ocr/' in n.lower() for n in set(new)-set(old))
dump(A/'evidence/binary-delta.json',{'added':sorted(set(new)-set(old)),'removed':sorted(set(old)-set(new)),'changed':sorted(n for n in set(old)&set(new) if old[n]!=new[n]),'dependency_jars_unchanged':True})
tree=A/'artifacts/frontend/browser';assert (tree/'index.html').is_file()
front=A/'artifacts/frontend-production.zip';zip_files(front,tree,sorted(p for p in tree.rglob('*') if p.is_file()))
with zipfile.ZipFile(R/'qa/artifacts/staging-minimo-20261114T025917Z/artifacts/frontend-production.zip') as z:
 ocr={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith('ocr/') and not n.endswith('/')}
for n,h in ocr.items():assert (tree/n).is_file() and sha(tree/n)==h,'BASELINE_ASSET_CHANGED'
assert set(ocr)=={p.relative_to(tree).as_posix() for p in (tree/'ocr').rglob('*') if p.is_file()}
source=inventory(A/'sources');dump(A/'source-inventory.json',source)
deltas={}
for module,base in [('dima-bug-backend',R/'qa/artifacts/staging-auth-isolation-20261114/sources/dima-bug-backend'),('frontend-dimabug',R/'qa/artifacts/staging-minimo-20261114T025917Z/sources/frontend-dimabug')]:
 before=inventory(base);after=inventory(A/'sources'/module)
 deltas[module]={'added':sorted(set(after)-set(before)),'removed':sorted(set(before)-set(after)),'changed':sorted(n for n in set(before)&set(after) if before[n]!=after[n])}
 assert not deltas[module]['removed']
 for n in deltas[module]['added']+deltas[module]['changed']:
  assert 'whatsapp' not in n.lower() and 'ocr' not in n.lower(),'OUT_OF_SCOPE_DELTA'
dump(A/'source-delta.json',deltas)
sourcezip=A/'artifacts/sources.zip';zip_files(sourcezip,A/'sources',list(files(A/'sources')))
shutil.copy2(R/'docs/REGISTRO_UNIVERSAL_LOCAL.md',A/'README.md')
sql=R/'dima-bug-backend/src/main/resources/db/migration/V20261115_01__registro_universal.sql'
sqlcopy=A/'artifacts'/sql.name;shutil.copy2(sql,sqlcopy)
native=json.loads((A/'evidence/native-mysql-migration.json').read_text())
assert native['exact_migration_sha256']==sha(sqlcopy)
def git(*args):return subprocess.check_output(['git',*args],cwd=R,text=True).strip()
manifest={
 'release':'registro-universal-20261115','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'status':'LOCAL_IMPLEMENTATION_VERIFIED_NOT_DEPLOYED','git_head':git('rev-parse','HEAD'),'branch':git('branch','--show-current'),'committed':False,
 'baseline_backend_sha256':sha(oldjar),'source_tree_sha256':fingerprint(source),
 'artifacts':{p.name:{'path':p.relative_to(A).as_posix(),'sha256':sha(p),'bytes':p.stat().st_size} for p in [backend,front,sourcezip,sqlcopy]},
 'tests':totals,'evidence':evidence,'native_migration_mysql_version':native['mysql_version'],
 'frontend_contract':{'additive_login_accesoBasico':True,'basic_portal':'/solicitudes','api':'/api','backend_authoritative':True},
 'legacy_accounts':{'migration_default':'ADMINISTRADO','roles_active_flags_hashes_preserved':True},
 'out_of_scope':{'staging_modified':False,'production_modified':False,'oauth_custody_modified':False,'real_mail_sent':False,'new_whatsapp_ocr_changes':False,'baseline_ocr_static_assets_unchanged':len(ocr)},
 'before_deployment':['Repetir concurrencia de la aplicación con JDBC/MySQL nativo (H2 pasó).','Preflight del esquema destino: unicidad de correos canónicos, rol USUARIO y respaldo nuevo.','Despliegue conjunto backend/frontend y migración bajo autorización independiente.','Reversión debe conservar el alcance básico; el JAR previo no reconoce los autorregistros.','Prueba posterior autorizada de Gmail real y navegación de un autorregistro.'],
 'local_registration_default_enabled':False,
 'reproduction':['python qa/build_universal_local.py sync','python qa/run_universal_checks.py backend-tests','python qa/run_universal_checks.py backend-ticket-regressions','python qa/artifacts/registro-universal-20261115/build_release.py tests','python qa/artifacts/registro-universal-20261115/build_release.py maintenance-tests','python qa/run_universal_checks.py frontend-tests','python qa/run_universal_checks.py frontend-build','python qa/artifacts/registro-universal-20261115/build_release.py package']
}
dump(A/'release-manifest.json',manifest)
checks=[f'{sha(p)}  {p.relative_to(A).as_posix()}' for p in [backend,front,sourcezip,sqlcopy,A/'source-inventory.json',A/'source-delta.json',A/'release-manifest.json',A/'README.md']]
(A/'SHA256SUMS.txt').write_text('\n'.join(checks)+'\n',encoding='utf-8')
print(json.dumps({'status':manifest['status'],'tests':totals,'backend_sha256':sha(backend),'frontend_sha256':sha(front),'migration_sha256':sha(sqlcopy),'sources_sha256':sha(sourcezip),'manifest_sha256':sha(A/'release-manifest.json'),'baseline_ocr_assets_unchanged':len(ocr)}))
