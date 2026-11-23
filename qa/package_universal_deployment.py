from pathlib import Path
import base64,datetime,hashlib,json,os,subprocess,sys,uuid,zipfile,zlib
R=Path(__file__).resolve().parents[1];V=R/'qa/artifacts/registro-universal-deploy-20261115';payload=V/'payload'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for name in ['probe-tests.json','native-rollback-executor-tests.json','native-restore-probe-tests.json']:assert json.loads((V/name).read_text())['verified'],name
tests=subprocess.run([sys.executable,str(R/'qa/verification/test_deployment_controls.py')],capture_output=True,text=True)
assert tests.returncode==0,'CONTROLS_FAILED'
(V/'control-tests.json').write_text(json.dumps({'tests':8,'verified':True,'real_staging_accessed':False}),encoding='utf-8')
identity='registro-universal-20261115-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8]
files={p.relative_to(payload).as_posix():sha(p) for p in payload.rglob('*') if p.is_file() and p.name!='deployment-manifest.json' and '__pycache__' not in p.parts}
manifest={'release':'registro-universal-20261115','deployment_id':identity,'files':files,'no_valid_otp_requests':True,'quarantine_required_before_legacy_restore':True,'rollback_keeps_maintenance':True,'native_executor_tests_verified':True,'backup_restore_probe_tests_verified':True}
(payload/'deployment-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
package=V/(identity+'.zip')
with zipfile.ZipFile(package,'x',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for name in list(files)+['deployment-manifest.json']:z.write(payload/name,name)
with zipfile.ZipFile(package) as z:assert z.testzip() is None
incoming='/home/carolina/dimabug-universal-incoming-'+identity.rsplit('-',1)[-1]
remote_zip=incoming+'/deployment.zip';expected=sha(package);size=package.stat().st_size
bootstrap=f'''
import hashlib,json,os,sys,stat,pwd,zipfile,runpy
from pathlib import Path
try:
    os.umask(0o077)
    source=Path({remote_zip!r});s=source.lstat()
    assert stat.S_ISREG(s.st_mode) and s.st_nlink==1 and s.st_uid==pwd.getpwnam('carolina').pw_uid and s.st_size=={size}
    base=Path('/var/lib/dimabug/staging-release-payloads')
    if not base.exists():base.mkdir(mode=0o700)
    bs=base.lstat();assert stat.S_ISDIR(bs.st_mode) and bs.st_uid==0 and stat.S_IMODE(bs.st_mode)==0o700
    destination=base/{identity!r};destination.mkdir(mode=0o700)
    stored=destination/'verified-package.zip';h=hashlib.sha256()
    fd=os.open(source,os.O_RDONLY|os.O_NOFOLLOW)
    with os.fdopen(fd,'rb') as src,stored.open('xb') as out:
        for block in iter(lambda:src.read(1048576),b''):h.update(block);out.write(block)
        out.flush();os.fsync(out.fileno())
    assert h.hexdigest()=={expected!r}
    with zipfile.ZipFile(stored) as z:
        assert z.testzip() is None
        for n in z.namelist():
            p=Path(n);assert not p.is_absolute() and '..' not in p.parts and n!='verified-package.zip'
            assert (z.getinfo(n).external_attr>>16)&0o170000!=0o120000
            target=destination/p;target.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
            with z.open(n) as src,target.open('xb') as out:
                for block in iter(lambda:src.read(1048576),b''):out.write(block)
            os.chmod(target,0o400)
    print(json.dumps({{'phase':'ROOT_PACKAGE_VERIFIED','deployment_id':{identity!r}}}),flush=True)
except BaseException as e:
    print(json.dumps({{'status':'PACKAGE_REJECTED_BEFORE_EXECUTION','category':type(e).__name__}}));raise SystemExit(1)
sys.path.insert(0,str(destination/'tools'))
sys.argv=[str(destination/'tools/deploy_universal.py'),'--execute-authorized-staging','--payload',str(destination),'--deployment-id',{identity!r}]
runpy.run_path(str(destination/'tools/deploy_universal.py'),run_name='__main__')
'''
encoded=base64.b64encode(zlib.compress(bootstrap.encode(),9)).decode()
remote="sudo python3 -I -B -c \"import base64,zlib;exec(zlib.decompress(base64.b64decode('"+encoded+"')))\""
launcher=r'''$ErrorActionPreference='Stop'
# One authorized deployment; never automatically repeat after an error or lost terminal.
& ssh -tt -o StrictHostKeyChecking=yes -o UpdateHostKeys=no -o HostKeyAlgorithms=ssh-ed25519 -o IdentitiesOnly=yes -o ForwardAgent=no -i "$env:USERPROFILE\.ssh\casol_admin" carolina@157.230.187.214 '''+"'"+remote.replace("'","''")+"'\n"
(V/'Start-Staging-UniversalDeployment.ps1').write_text(launcher,encoding='utf-8-sig')
info={'deployment_id':identity,'package':str(package),'package_sha256':expected,'package_bytes':size,'incoming':incoming,'remote_zip':remote_zip,'launcher':str(V/'Start-Staging-UniversalDeployment.ps1')}
(V/'transfer-plan.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf-8')
(V/'SHA256SUMS.txt').write_text('\n'.join(sha(p)+'  '+p.name for p in [package,payload/'deployment-manifest.json',V/'Start-Staging-UniversalDeployment.ps1'])+'\n',encoding='utf-8')
print(json.dumps(info))
