"""Offline QA extension compilation only; no Spring startup, SQL, Docker or secrets."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main(test):
    manifest=json.loads((ROOT/'qa/candidate.json').read_text())
    original=ROOT/manifest['artifacts']['backend']['bundle']['path']
    bundle=json.loads(original.read_text())
    for item in bundle['files'].values():
        if sha(ROOT/item['path'])!=item['sha256']:raise RuntimeError('QA_BUNDLE_CHANGED')
    folder=ROOT/'qa/runtime/auth-builds'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');folder.mkdir(parents=True)
    deps=folder/'lib';deps.mkdir();classes=folder/'backend-classes';classes.mkdir()
    with zipfile.ZipFile(ROOT/bundle['files']['backend']['path']) as jar:
        for name in jar.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):(deps/Path(name).name).write_bytes(jar.read(name))
            elif name.startswith('BOOT-INF/classes/') and name.endswith('.class'):
                target=classes/name.removeprefix('BOOT-INF/classes/');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(jar.read(name))
    java=Path(shutil.which('java.exe'));cp=str(classes)+os.pathsep+str(deps/'*')
    env={k:os.environ[k] for k in ['SystemRoot','WINDIR','USERPROFILE','LOCALAPPDATA'] if k in os.environ}
    env['PATH']=str(java.parent)+os.pathsep+str(Path(os.environ['SystemRoot'])/'System32')
    env['TEMP']=env['TMP']=str(ROOT/'qa/runtime/tmp')
    def run(args,label):
        with (folder/(label+'.log')).open('xb') as log:r=subprocess.run([str(a) for a in args],env=env,cwd=folder,stdout=log,stderr=subprocess.STDOUT,timeout=120)
        if r.returncode:raise RuntimeError(label.upper()+'_FAILED')
    output=folder/'otp-classes';output.mkdir()
    sources=sorted((HERE/'src').rglob('*.java'))
    run([java.parent/'javac.exe','--release','21','-cp',cp,'-d',output,*sources],'compile')
    if test:
        tests=sorted((HERE/'test').rglob('*.java'));testclasses=folder/'test-classes';testclasses.mkdir()
        run([java.parent/'javac.exe','--release','21','-cp',cp+os.pathsep+str(output),'-d',testclasses,*tests],'compile-tests')
        run([java,'-Duser.timezone=UTC','-cp',cp+os.pathsep+str(output)+os.pathsep+str(testclasses),
             'cl.casol.backend.identidad.application.service.QaAuthTests',folder/'test-spool'],'unit-tests')
    artifact=ROOT/'qa/artifacts'/('auth-'+folder.name);artifact.mkdir()
    # Keep the tested backend/TLS artifacts intact; extension is separate.
    for role,item in bundle['files'].items():
        source=ROOT/item['path'];target=artifact/source.name;shutil.copy2(source,target)
        bundle['files'][role]={'path':target.relative_to(ROOT).as_posix(),'sha256':sha(target)}
    plugin=artifact/'qa-otp.jar';run([java.parent/'jar.exe','--create','--file',plugin,'-C',output,'.'],'package')
    bundle['files']['qa_otp']={'path':plugin.relative_to(ROOT).as_posix(),'sha256':sha(plugin)}
    bundle['otp_source_snapshot']={p.relative_to(ROOT).as_posix():sha(p) for p in sources}
    path=artifact/'bundle.json';path.write_text(json.dumps(bundle,indent=2)+'\n')
    print(json.dumps({'status':'QA_AUTH_EXTENSION_COMPILED','bundle':path.relative_to(ROOT).as_posix(),
                      'test_log':str((folder/'unit-tests.log').relative_to(ROOT)) if test else None,
                      'spring_started':False,'sql_executed':False,'credentials_read':False}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--test',action='store_true');a=p.parse_args()
    try:main(a.test)
    except Exception as e:
        print(json.dumps({'status':'QA_AUTH_BUILD_FAILED','code':str(e) if type(e) is RuntimeError else type(e).__name__}));raise SystemExit(1)
