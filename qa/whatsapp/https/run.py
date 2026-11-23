"""Etapa 3.2-B: TLS/proxy JDK de pruebas -> Spring TCP -> MySQL nuevo y exclusivo.
Sin dependencias nuevas, Internet, Meta, Ubuntu ni alteración de recursos previos.
"""
from pathlib import Path
import hashlib,json,os,secrets,shutil,socket,subprocess,sys,threading,time,uuid

ROOT=Path(__file__).resolve().parents[3]
ID=uuid.uuid4().hex[:10];NAME='dimabug-wa-https-'+ID;VOLUME=NAME+'-data';DB='wa_durable_'+ID
RUN=ROOT/'qa/runtime'/NAME;RUN.mkdir();(RUN/'tmp').mkdir()
MYSQL='sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
HAPROXY='sha256:5378234d19457d645ed6d5a0ac994ef196614f9a2cabda8f6ccd6ac8815f9098'
DOCKER=shutil.which('docker.exe') or shutil.which('docker');JAVA=shutil.which('java.exe') or shutil.which('java')
PASSWORD=secrets.token_urlsafe(32);ROOT_PASSWORD=secrets.token_urlsafe(32)
CREATED=False;MAVEN=None;STOP=threading.Event();COORDINATION=[];ERRORS=[]

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def docker(args,stdin=None,check=True):
 p=subprocess.run([DOCKER,'--context','desktop-linux',*args],input=stdin,text=True,encoding='utf-8',errors='replace',capture_output=True,timeout=120)
 if check and p.returncode:raise RuntimeError(('Docker '+args[0]+': '+p.stderr).replace(PASSWORD,'[omitida]').replace(ROOT_PASSWORD,'[omitida]'))
 return p
def identidad():
 o=json.loads(docker(['inspect',NAME]).stdout)[0]
 assert o['Config']['Labels'].get('dimabug.wa32b')==ID and o['Image']==MYSQL
 assert o['HostConfig']['PortBindings']['3306/tcp']==[{'HostIp':'127.0.0.1','HostPort':str(PORT)}]
 assert any(m.get('Name')==VOLUME and m['Destination']=='/var/lib/mysql' for m in o['Mounts'])
 return o
def sql(query):return docker(['exec','-i',NAME,'mysql','--defaults-extra-file=/tmp/root.cnf','--batch','--skip-column-names'],query).stdout.strip()
def disponible():
 for _ in range(90):
  p=docker(['exec',NAME,'mysql','--defaults-extra-file=/tmp/root.cnf','--batch','--skip-column-names','-e','SELECT 1'],check=False)
  if p.returncode==0 and p.stdout.strip()=='1':return
  time.sleep(1)
 raise RuntimeError('MySQL exclusivo no disponible')
def tcp_disponible():
 for _ in range(100):
  try:
   with socket.create_connection(('127.0.0.1',PORT),timeout=2) as s:
    s.settimeout(2);datos=s.recv(16)
    if len(datos)>=5 and datos[4]==10:return
  except OSError:pass
  time.sleep(.2)
 raise RuntimeError('MySQL exclusivo no disponible por TCP loopback')
def coordinar():
 try:
  while not STOP.wait(.2):
   if (RUN/'solicitar-parada-mysql').exists():
    identidad();docker(['stop','-t','15',NAME]);COORDINATION.append('MySQL exclusivo detenido');(RUN/'mysql-detenido').write_text('QA')
    while not STOP.wait(.2) and not (RUN/'solicitar-reinicio-mysql').exists():pass
    if not STOP.is_set():
     identidad();docker(['start',NAME]);disponible();tcp_disponible();COORDINATION.append('MySQL exclusivo reiniciado');(RUN/'mysql-reiniciado').write_text('QA')
    return
 except Exception as e:ERRORS.append(type(e).__name__)

def main():
 global CREATED,PORT,MAVEN
 print('EVIDENCIAS',RUN,flush=True)
 sec=RUN/'secrets';sec.mkdir()
 subprocess.run(['icacls',str(sec),'/inheritance:r','/grant:r',os.environ['USERDOMAIN']+'\\'+os.environ['USERNAME']+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],capture_output=True,check=True)
 # El certificado y el build también quedan dentro de una carpeta privada de QA.
 subprocess.run(['icacls',str(RUN),'/inheritance:r','/grant:r',os.environ['USERDOMAIN']+'\\'+os.environ['USERNAME']+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],capture_output=True,check=True)
 (sec/'root').write_text(ROOT_PASSWORD);(sec/'worker').write_text(PASSWORD)
 with socket.socket() as s:s.bind(('127.0.0.1',0));PORT=s.getsockname()[1]
 # Solo validación de sintaxis del proxy estándar; no inicia ningún listener.
 cfg=ROOT/'qa/whatsapp/https/haproxy.local.cfg'
 p=docker(['run','--pull','never','--name',NAME+'-config','--label','dimabug.wa32b='+ID,'--network','none','--read-only',
  '--cap-drop','ALL','--user','65534:65534','--mount',f'type=bind,source={cfg},target=/qa/haproxy.cfg,readonly',
  '--entrypoint','haproxy',HAPROXY,'-c','-f','/qa/haproxy.cfg'],check=False)
 (RUN/'haproxy-sintaxis.txt').write_text(p.stdout+p.stderr,encoding='utf-8');assert p.returncode==0,'Configuración HAProxy inválida'
 docker(['volume','create','--label','dimabug.wa32b='+ID,VOLUME])
 docker(['run','-d','--pull','never','--name',NAME,'--label','dimabug.wa32b='+ID,'--network','bridge','-p',f'127.0.0.1:{PORT}:3306',
  '--cpus','2','--memory','2g','--pids-limit','256','--mount',f'type=volume,source={VOLUME},target=/var/lib/mysql',
  '--mount',f'type=bind,source={sec},target=/run/qa,readonly','-e','MYSQL_ROOT_PASSWORD_FILE=/run/qa/root',
  '-e','MYSQL_PASSWORD_FILE=/run/qa/worker','-e','MYSQL_USER=wa_worker','-e','MYSQL_DATABASE='+DB,MYSQL,
  '--innodb-buffer-pool-size=268435456','--default-time-zone=+00:00','--skip-name-resolve'])
 CREATED=True
 docker(['exec','-i',NAME,'sh','-c','umask 077; cat > /tmp/root.cnf'],f'[client]\nuser=root\npassword={ROOT_PASSWORD}\nprotocol=socket\n')
 disponible();tcp_disponible();identidad()
 sql('USE '+DB+';\n'+(ROOT/'qa/whatsapp/mysql-stage22a/schema.sql').read_text())
 sql(f"REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'wa_worker'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON `{DB}`.* TO 'wa_worker'@'%';")
 assert sql(f"SELECT count(*) FROM information_schema.tables WHERE table_schema='{DB}'")=='3'
 copy=RUN/'build';(copy/'src/main').mkdir(parents=True)
 source=ROOT/'dima-bug-backend';shutil.copy2(source/'pom.xml',copy/'pom.xml');shutil.copytree(source/'src/main/java',copy/'src/main/java')
 (copy/'src/main/resources/META-INF').mkdir(parents=True)
 for name in ['application-qa-whatsapp-meta.properties','META-INF/whatsapp.orm.xml']:
  shutil.copy2(source/'src/main/resources'/name,copy/'src/main/resources'/name)
 testpath=Path('cl/casol/backend/whatsapp/https')
 shutil.copytree(source/'src/test/java'/testpath,copy/'src/test/java'/testpath)
 hashes={p.relative_to(source).as_posix():sha(p) for p in (source/'src/main/java').rglob('*.java')}
 assert all(sha(copy/p)==h for p,h in hashes.items());(RUN/'fuentes.json').write_text(json.dumps(hashes,indent=2),encoding='utf-8')
 allow={k.upper() for k in ['SystemRoot','WINDIR','SystemDrive','ComSpec','PATHEXT','USERPROFILE','LOCALAPPDATA','APPDATA','ProgramFiles','ProgramFiles(x86)']}
 env={k:v for k,v in os.environ.items() if k.upper() in allow}
 env.update(PATH=str(Path(JAVA).parent)+os.pathsep+str(Path(os.environ['SystemRoot'])/'System32'),TEMP=str(RUN/'tmp'),TMP=str(RUN/'tmp'),NO_PROXY='127.0.0.1,localhost')
 env.update(WA_QA_HTTPS_RUN=str(RUN),WA_QA_JDBC_PASSWORD=PASSWORD,
  WA_QA_JDBC_URL=f'jdbc:mysql://127.0.0.1:{PORT}/{DB}?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=3000&socketTimeout=5000')
 m2=Path(os.environ['USERPROFILE'])/'.m2';boot=next((m2/'wrapper/dists').glob('apache-maven-3.9.16*/**/plexus-classworlds*.jar'));mvn=boot.parent.parent
 mockito=m2/'repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
 args=[JAVA,'-Dmaven.home='+str(mvn),'-Dmaven.multiModuleProjectDirectory='+str(copy),'-Dclassworlds.conf='+str(mvn/'bin/m2.conf'),'-classpath',str(boot),
  'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-Dstyle.color=never','-Dtest=ProxyMetaHttpsTest',
  '-Dspring.datasource.hikari.connection-timeout=3000','-Dspring.datasource.hikari.validation-timeout=1000',
  '-DargLine=--add-modules=jdk.httpserver -Dsun.net.httpserver.maxReqTime=10 -Dsun.net.httpserver.maxRspTime=30 -Dsun.net.httpserver.idleInterval=5 -javaagent:'+str(mockito),'test']
 t=threading.Thread(target=coordinar,daemon=True);t.start()
 with (RUN/'maven-test.log').open('wb') as log:
  MAVEN=subprocess.Popen(args,cwd=copy,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  result=MAVEN.wait(timeout=300)
 STOP.set();t.join(timeout=10)
 for line in (RUN/'maven-test.log').read_text(encoding='utf-8',errors='replace').splitlines():
  if 'Tests run:' in line or 'BUILD SUCCESS' in line or 'BUILD FAILURE' in line:print(line,flush=True)
 assert result==0 and not ERRORS,'Fallaron las pruebas; revisar evidencias'
 assert COORDINATION==['MySQL exclusivo detenido','MySQL exclusivo reiniciado'],'Faltó comprobar caída real de MySQL'
 assert sql(f"SELECT count(*) FROM information_schema.tables WHERE table_schema='{DB}'")=='3'
 return 0

if __name__=='__main__':
 code=2
 try:code=main()
 except Exception as e:
  print('QA_NO_APROBADO',type(e).__name__,str(e).replace(PASSWORD,'[omitida]').replace(ROOT_PASSWORD,'[omitida]'),flush=True)
 finally:
  STOP.set()
  if MAVEN is not None and MAVEN.poll() is None:MAVEN.terminate()
  estado='NO_INICIADO'
  if CREATED:
   try:identidad();docker(['stop','-t','20',NAME]);estado='DETENIDO'
   except Exception:estado='REVISAR'
  (RUN/'informe.json').write_text(json.dumps({'exit_code':code,'contenedor':NAME,'volumen':VOLUME,'base':DB,'estado_final':estado,
   'coordinacion':COORDINATION,'errores_coordinacion':ERRORS,'solo_loopback':True,'meta':False,'ubuntu':False,'recursos_previos_modificados':False},indent=2),encoding='utf-8')
  print('INFORME',RUN/'informe.json',flush=True)
 sys.exit(code)
