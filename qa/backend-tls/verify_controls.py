"""Repeatable offline control tests; NEVER invokes JdbcProbe, Spring, SQL or custody."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--bundle',required=True);parser.add_argument('--leaf',required=True)
    args=parser.parse_args()
    spec=importlib.util.spec_from_file_location('qa_manifest',ROOT/'qa/qa_manifest.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    path,bundle=module.validate_bundle(args.bundle)
    leaf=Path(args.leaf).resolve(strict=True)
    if not leaf.is_relative_to(ROOT/'qa/runtime/backend-builds'):raise RuntimeError('PUBLIC_LEAF_LOCATION')
    public_files={k:ROOT/v['path'] for k,v in bundle['files'].items()}
    java=Path(shutil.which('java.exe'))
    env={k:os.environ[k] for k in ('SystemRoot','WINDIR','USERPROFILE','LOCALAPPDATA','APPDATA') if k in os.environ}
    env['PATH']=str(java.parent)+os.pathsep+str(Path(os.environ['SystemRoot'])/'System32')
    env['TEMP']=env['TMP']=str(ROOT/'qa/runtime/tmp')
    connector=Path(os.environ['USERPROFILE'])/'.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar'
    tests=leaf.parent/'repeatable-control-tests';tests.mkdir(exist_ok=True)
    cp=str(connector)+os.pathsep+str(public_files['helper'])
    def run(arguments):
        result=subprocess.run([str(x) for x in arguments],env=env,cwd=path.parent,capture_output=True,text=True,timeout=30)
        if result.returncode:raise RuntimeError('QA_OFFLINE_TEST_FAILED')
        return result.stdout
    run([java.parent/'javac.exe','--release','21','-cp',cp,'-d',tests,ROOT/'qa/backend-tls/test/ControlsTest.java'])
    output=run([java,'-cp',cp+os.pathsep+str(tests),'dimabug.qa.ControlsTest',public_files['truststore'],leaf,public_files['ca']])
    if 'QA_JDBC_CONTROLS_PASSED=26' not in output:raise RuntimeError('QA_TEST_COUNT')
    loader=run([java,'-Dloader.path='+str(public_files['helper']),'-Dloader.main=dimabug.qa.VerifyLoader',
                '-Dloader.config.location='+public_files['loader'].as_uri(),'-Dloader.home='+str(path.parent),
                '-cp',public_files['backend'],'org.springframework.boot.loader.launch.PropertiesLauncher'])
    if 'QA_LOADER_ISOLATION_OK_NO_SPRING_NO_CONNECTION' not in loader:raise RuntimeError('QA_LOADER_FAILED')
    print(json.dumps({'status':'QA_JDBC_OFFLINE_CONTROLS_OK','checks_passed':26,'loader_isolation_verified':True,
                      'jdbc_authenticated':False,'sql_executed':False,'spring_started':False,'credentials_read':False}))

if __name__=='__main__':
    try:main()
    except Exception as exc:
        print(json.dumps({'status':'QA_JDBC_OFFLINE_CONTROLS_FAILED','category':type(exc).__name__}));raise SystemExit(1)
