"""JPA contra MySQL exclusivo: esquema aprobado, usuario DML y reinicio real.
No utiliza configuraciones, credenciales ni contenedores existentes de DimaBug.
"""
from pathlib import Path
import json, os, re, secrets, shutil, subprocess, sys, time, uuid

ROOT=Path(__file__).resolve().parents[2]
IMAGE='sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
REUSE=sys.argv[1] if len(sys.argv)==2 else None
if REUSE and not re.fullmatch('[a-f0-9]{10}',REUSE):raise ValueError('Identidad QA inválida')
RUNID=REUSE or uuid.uuid4().hex[:10]
NAME='dimabug-wa-jpa-'+RUNID
DB='wa_durable_'+RUNID
VOLUME=NAME+'-data'
BASE=ROOT/'qa/runtime'/NAME
RUN=BASE/('reintento-'+uuid.uuid4().hex[:8]) if REUSE else BASE
RUN.mkdir(parents=True)
DOCKER=shutil.which('docker.exe') or shutil.which('docker')
PASSWORD=(BASE/'secrets/worker').read_text() if REUSE else secrets.token_urlsafe(32)
ROOT_PASSWORD=(BASE/'secrets/root').read_text() if REUSE else secrets.token_urlsafe(32)
RESULTS=[]

def docker(args,stdin=None,check=True):
    p=subprocess.run([DOCKER,'--context','desktop-linux',*args],input=stdin,text=True,encoding='utf-8',errors='replace',capture_output=True,timeout=120)
    if check and p.returncode:
        raise RuntimeError(('Docker '+args[0]+': '+p.stderr).replace(PASSWORD,'[omitida]').replace(ROOT_PASSWORD,'[omitida]'))
    return p

def sql(query):
    return docker(['exec','-i',NAME,'mysql','--defaults-extra-file=/tmp/wa-root.cnf','--batch','--skip-column-names'],query).stdout.strip()

def identity():
    obj=json.loads(docker(['inspect',NAME]).stdout)[0]
    assert obj['Image']==IMAGE and obj['Config']['Labels']['dimabug.wa-jpa']==RUNID
    assert any(m.get('Name')==VOLUME and m['Destination']=='/var/lib/mysql' for m in obj['Mounts'])
    ports=obj['HostConfig']['PortBindings']['3306/tcp']
    assert len(ports)==1 and ports[0]['HostIp']=='127.0.0.1'
    return obj

def ready():
    for _ in range(90):
        p=docker(['exec','-i',NAME,'mysql','--defaults-extra-file=/tmp/wa-root.cnf','--batch','--skip-column-names','-e','SELECT 1'],check=False)
        if p.returncode==0 and p.stdout.strip()=='1':return
        time.sleep(1)
    raise RuntimeError('MySQL de prueba no quedó disponible')

def tests(label,selector='PersistenciaWhatsAppTest',reinicio=''):
    target=RUN/label
    permitidas={k.upper() for k in ('SystemRoot','WINDIR','SystemDrive','ComSpec','PATHEXT','USERPROFILE','LOCALAPPDATA','APPDATA','ProgramFiles','ProgramFiles(x86)','PATH','TEMP','TMP')}
    env={k:v for k,v in os.environ.items() if k.upper() in permitidas}
    port=identity()['NetworkSettings']['Ports']['3306/tcp'][0]['HostPort']
    url=f'jdbc:mysql://127.0.0.1:{port}/{DB}?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=5000&socketTimeout=10000'
    env.update(WA_QA_JDBC_URL=url,WA_QA_JDBC_PASSWORD=PASSWORD,WA_QA_REINICIO=reinicio)
    p=subprocess.run(['pwsh','-NoProfile','-File',str(ROOT/'qa/whatsapp/Run-PruebasDurables.ps1'),'-Pruebas',selector,'-Evidencias',str(target)],cwd=ROOT,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=240)
    (RUN/(label+'.txt')).write_text(p.stdout+p.stderr,encoding='utf-8')
    RESULTS.append({'etapa':label,'exit_code':p.returncode})
    print(label,p.returncode,flush=True)
    if p.returncode:raise RuntimeError('Fallaron pruebas: '+label)

try:
    # Secretos sintéticos por fichero: no aparecen en argumentos ni docker inspect.
    if REUSE:
        identity();docker(['start',NAME])
    else:
        sec=BASE/'secrets';sec.mkdir()
        subprocess.run(['icacls',str(sec),'/inheritance:r','/grant:r',os.environ['USERDOMAIN']+'\\'+os.environ['USERNAME']+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],capture_output=True,check=True)
        (sec/'root').write_text(ROOT_PASSWORD);(sec/'worker').write_text(PASSWORD)
        docker(['volume','create','--label','dimabug.wa-jpa='+RUNID,VOLUME])
        docker(['run','-d','--name',NAME,'--label','dimabug.wa-jpa='+RUNID,'--network','bridge',
                '-p','127.0.0.1::3306','--cpus','2','--memory','2g','--pids-limit','256',
                '--mount',f'type=volume,source={VOLUME},target=/var/lib/mysql',
                '--mount',f'type=bind,source={sec},target=/run/wa-secrets,readonly',
                '-e','MYSQL_ROOT_PASSWORD_FILE=/run/wa-secrets/root','-e','MYSQL_PASSWORD_FILE=/run/wa-secrets/worker',
                '-e','MYSQL_DATABASE='+DB,'-e','MYSQL_USER=wa_worker',IMAGE,
                '--innodb-buffer-pool-size=268435456','--default-time-zone=+00:00','--skip-name-resolve'])
        docker(['exec','-i',NAME,'sh','-c','umask 077; cat > /tmp/wa-root.cnf'],f'[client]\nuser=root\npassword={ROOT_PASSWORD}\nprotocol=socket\n')
    ready();obj=identity()
    if not REUSE:
        sql('USE '+DB+';\n'+(ROOT/'qa/whatsapp/mysql-stage22a/schema.sql').read_text())
        sql(f"REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'wa_worker'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON `{DB}`.* TO 'wa_worker'@'%';")
    assert sql(f"SELECT count(*) FROM information_schema.tables WHERE table_schema='{DB}'")=='3'
    tests('jpa-mysql')
    tests('antes-reinicio','PersistenciaWhatsAppTest#reinicioCrear','crear')
    identity();docker(['restart','-t','20',NAME]);ready();identity()
    tests('despues-reinicio','PersistenciaWhatsAppTest#reinicioRecuperar','recuperar')
finally:
    try:
        identity();docker(['stop','-t','20',NAME]);estado='DETENIDO'
    except Exception:estado='REVISAR'
    informe={'contenedor':NAME,'volumen':VOLUME,'base':DB,'imagen':IMAGE,'estado_final':estado,'resultados':RESULTS,
             'alcance':'Solo tres tablas nuevas; puerto loopback; datos sintéticos; sin Meta; volumen conservado.'}
    (RUN/'informe.json').write_text(json.dumps(informe,indent=2),encoding='utf-8')
    print(str(RUN/'informe.json'),flush=True)
