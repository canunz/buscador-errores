"""Authorized preparation only. Offline compilation, public CA read; NO SQL, unlock or app startup."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import ssl
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
PIN = 'afee3b9f5f0b8ad162198bfe96b3b5f5be45499afa7f69acb1d89a9a812d7b9e'
CONNECTOR = '9.7.0'

def require(ok, code):
    if not ok: raise RuntimeError(code)

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def environment(java):
    allowed = ('SystemRoot','WINDIR','SystemDrive','USERPROFILE','LOCALAPPDATA','APPDATA','ProgramFiles','ProgramFiles(x86)')
    env = {k: os.environ[k] for k in allowed if k in os.environ}
    env['PATH'] = str(java.parent) + os.pathsep + str(Path(os.environ['SystemRoot'])/'System32')
    env['JAVA_HOME'] = str(java.parent.parent)
    env['TEMP'] = env['TMP'] = str(ROOT/'qa/runtime/tmp')
    return env

def run(args, folder, env, name, timeout=180):
    # Diagnostics remain ignored locally, never echo provider output or exceptions.
    with (folder/(name+'.log')).open('xb') as log:
        result = subprocess.run([str(x) for x in args], cwd=folder, env=env,
                                stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
    require(result.returncode == 0, name.upper()+'_FAILED')

def archive_check(path):
    with zipfile.ZipFile(path) as jar:
        resources = [n for n in jar.namelist() if n.startswith('BOOT-INF/classes/') and not n.endswith('/') and not n.endswith('.class')]
        expected = {'BOOT-INF/classes/application-qa-local.properties', 'BOOT-INF/classes/correo/banner-codigo-acceso.jpg'}
        require(set(resources) == expected, 'QA_RESOURCES_ALLOWLIST_FAILED')
        require(jar.read('BOOT-INF/classes/application-qa-local.properties') ==
                (ROOT/'dima-bug-backend/src/main/resources/application-qa-local.properties').read_bytes(), 'QA_PROFILE_MISMATCH')
        require('BOOT-INF/lib/mysql-connector-j-9.7.0.jar' in jar.namelist(), 'CONNECTOR_VERSION_CHANGED')
        require('org/springframework/boot/loader/launch/PropertiesLauncher.class' in jar.namelist(), 'LOADER_UNAVAILABLE')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--prepare', action='store_true')
    args = parser.parse_args()
    if not args.prepare:
        print(json.dumps({'status':'PREPARATION_REQUIRES_EXPLICIT_FLAG','sql':False,'app_started':False})); return
    java = Path(shutil.which('java.exe') or '')
    require(java.is_file(), 'JAVA_MISSING')
    cache = Path(os.environ['USERPROFILE'])/'.m2'
    connector = cache/f'repository/com/mysql/mysql-connector-j/{CONNECTOR}/mysql-connector-j-{CONNECTOR}.jar'
    require(connector.is_file(), 'CACHED_CONNECTOR_MISSING')
    mvns = sorted((cache/'wrapper/dists/apache-maven-3.9.16').glob('*/boot/plexus-classworlds-*.jar'))
    require(len(mvns) == 1, 'CACHED_MAVEN_AMBIGUOUS')
    boot = mvns[0]; maven = boot.parent.parent
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    folder = ROOT/'qa/runtime/backend-builds'/stamp
    folder.mkdir(parents=True, exist_ok=False)
    env = environment(java)
    artifact = ROOT/'qa/artifacts'/('backend-'+stamp)
    artifact.mkdir(parents=True, exist_ok=False)
    # Snapshot ONLY current Java sources and two explicitly allowed public resources.
    source = ROOT/'dima-bug-backend'
    shutil.copytree(source/'src/main/java', folder/'src/main/java')
    resources = folder/'src/main/resources'; resources.mkdir(parents=True)
    shutil.copy2(source/'src/main/resources/application-qa-local.properties', resources)
    (resources/'correo').mkdir()
    shutil.copy2(source/'src/main/resources/correo/banner-codigo-acceso.jpg', resources/'correo')
    source_files = [source/'pom.xml', *sorted((source/'src/main/java').rglob('*.java')),
                    source/'src/main/resources/application-qa-local.properties', source/'src/main/resources/correo/banner-codigo-acceso.jpg']
    snapshot = {p.relative_to(ROOT).as_posix():digest(p) for p in source_files}
    # Keep the application POM untouched; build a clean copy with no ambient Maven settings.
    shutil.copy2(source/'pom.xml', folder/'pom.xml')
    (folder/'settings.xml').write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>', encoding='utf-8')
    command = [java, '-Dmaven.home='+str(maven), '-Dclassworlds.conf='+str(maven/'bin/m2.conf'),
               '-Dmaven.multiModuleProjectDirectory='+str(folder), '-cp',boot,
               'org.codehaus.plexus.classworlds.launcher.Launcher','--offline','--batch-mode',
               '--settings',folder/'settings.xml','--global-settings',folder/'settings.xml',
               '-Dmaven.test.skip=true','package']
    run(command, folder, env, 'maven-package', 300)
    built = folder/'target/backend-0.0.1-SNAPSHOT.jar'
    archive_check(built)
    jar = artifact/'backend-qa.jar'; shutil.copy2(built, jar)
    classes = folder/'tls-classes'; classes.mkdir()
    java_sources = sorted((HERE/'src').rglob('*.java'))
    run([java.parent/'javac.exe','--release','21','-cp',connector,'-d',classes,*java_sources],folder,env,'compile-tls')
    helper = artifact/'qa-jdbc-tls.jar'
    run([java.parent/'jar.exe','--create','--file',helper,'-C',classes,'.'],folder,env,'package-tls')
    # Only two PUBLIC certificates, never private keys or configuration files.
    docker = shutil.which('docker.exe'); require(bool(docker), 'DOCKER_MISSING')
    for name in ('ca.pem','server-cert.pem'):
        result = subprocess.run([docker,'--context','desktop-linux','exec','dimabug-qa-mvp-13318-mysql',
                                 'cat','/var/lib/mysql/'+name],capture_output=True,text=True,env=env,timeout=15)
        require(result.returncode == 0, 'PUBLIC_CERT_READ_FAILED')
        match = re.fullmatch(r'(-----BEGIN CERTIFICATE-----[\s\S]+?-----END CERTIFICATE-----)[\r\n\x00 ]*',result.stdout)
        require(match is not None and result.stdout.count('-----BEGIN CERTIFICATE-----') == 1 and 'PRIVATE KEY' not in result.stdout,'PUBLIC_CERT_FORMAT')
        (folder/name).write_text(match[1]+'\n',encoding='ascii')
    leaf = ssl.PEM_cert_to_DER_cert((folder/'server-cert.pem').read_text())
    require(hashlib.sha256(leaf).hexdigest() == PIN,'APPROVED_CERT_PIN_CHANGED')
    ca = artifact/'ca-public.pem'; shutil.copy2(folder/'ca.pem',ca)
    store = artifact/'qa-public-ca.jks'
    run([java,'-cp',helper,'dimabug.qa.CreatePublicTruststore',ca,store],folder,env,'create-public-store')
    # Explicit empty loader config prevents ambient loader.properties discovery.
    loader = artifact/'loader.properties'; loader.write_text('# QA sealed loader; all settings explicit in JVM arguments.\n',encoding='ascii')
    require(all(digest(ROOT/p) == sha for p,sha in snapshot.items()),'SOURCE_CHANGED_DURING_BUILD')
    document = {'format':'dimabug-qa-backend-bundle-v1','connector_version':CONNECTOR,
                'leaf_sha256':PIN,'truststore_integrity_password_is_public':True,
                'source_snapshot':snapshot,
                'helper_source_snapshot':{p.relative_to(ROOT).as_posix():digest(p) for p in java_sources}, 'files':{}}
    for role,path in {'backend':jar,'helper':helper,'ca':ca,'truststore':store,'loader':loader}.items():
        document['files'][role]={'path':path.relative_to(ROOT).as_posix(),'sha256':digest(path)}
    bundle = artifact/'bundle.json'; bundle.write_text(json.dumps(document,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':'QA_BACKEND_BUNDLE_PREPARED','bundle':bundle.relative_to(ROOT).as_posix(),
                      'backend_sha256':digest(jar),'sql_executed':False,'accounts_unlocked':False,
                      'applications_started':False,'credentials_generated':False,'jdbc_authenticated':False}))

if __name__ == '__main__':
    try: main()
    except Exception as exc:
        print(json.dumps({'status':'QA_BACKEND_PREPARATION_FAILED','category':type(exc).__name__,
                          'code':str(exc) if type(exc) is RuntimeError else 'SANITIZED_ERROR'}))
        raise SystemExit(1)
