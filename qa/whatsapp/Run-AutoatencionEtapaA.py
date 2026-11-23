"""Ventana QA explÃ­cita: base validada existente, autoatenciÃ³n y respuestas SIMULADAS."""
import argparse,hashlib,json,os,secrets,shutil,socket,subprocess,sys,time,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--approval',required=True)
parser.add_argument('--stage',choices=['A','B'],default='A')
parser.add_argument('--token-file',type=Path)
args=parser.parse_args()
if args.approval!=f'AUTORIZO_AUTOATENCION_QA_ETAPA_{args.stage}_60_MINUTOS':parser.error('Autorizacion expresa requerida para esta etapa')
if (args.stage=='B')!=(args.token_file is not None):parser.error('Token externo requerido exclusivamente para etapa B')
sys.path.insert(0,str(ROOT/'qa/whatsapp/local-isolation'))
from nginx_https import Harness
from temporary_tunnel import Session
base=ROOT/'qa/runtime/autoatencion/wa-auto-base-6cbea583bc'
prior=json.loads((base/'result.json').read_text());assert prior['status']=='PASS' and prior['services_stopped']
jar=Path(prior['jar']);assert hashlib.sha256(jar.read_bytes()).hexdigest()==prior['jar_sha256']
prepared=base/'private/application-etapa-a.properties'
props=dict(l.split('=',1) for l in prepared.read_text(encoding='utf-8').splitlines() if '=' in l)
assert '/wa_durable_b8fedc24e152?' in props['spring.datasource.url']
assert props['spring.jpa.hibernate.ddl-auto']=='validate'
for flag in ['recepcion','procesamiento','envio']:assert props['dimabug.whatsapp.meta-qa.'+flag]=='true'
for flag in ['medios','envio-real','conexion-meta']:assert props['dimabug.whatsapp.meta-qa.'+flag]=='false'
assert props['dimabug.whatsapp.autoatencion.habilitada']=='true'
token_mount=[]
if args.stage=='B':
    from etapa_b_config import prepare
    props,token_mount=prepare(props,args.token_file)
ID=uuid.uuid4().hex[:10];NAME='wa-auto-'+args.stage.lower()+'-'+ID;RUN=ROOT/'qa/runtime/autoatencion'/NAME;RUN.mkdir()
subprocess.run(['icacls',str(RUN),'/inheritance:r','/grant:r',os.environ['USERDOMAIN']+'\\'+os.environ['USERNAME']+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],capture_output=True,check=True)
SEC=RUN/'private';SEC.mkdir();CLASSES=RUN/'probe';CLASSES.mkdir()
created=[];tunnel=None;report={'stage':args.stage,'database':prior['database'],'real_send':args.stage=='B','media':False,'outbound_meta':args.stage=='B','jar_sha256':prior['jar_sha256']}
MYSQL='sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
JAVA='eclipse-temurin@sha256:dff6eb46527bd293a3c06bbbe9e64e8c9f91f01905b5abbdad31761c03059d2c'
def docker(*args,stdin=None,check=True,timeout=120):
    r=subprocess.run(['docker','--context','desktop-linux',*args],input=stdin,text=True,encoding='utf-8',capture_output=True,timeout=timeout)
    if check and r.returncode:
        (SEC/'docker-error.log').write_text(r.stdout+r.stderr,encoding='utf-8');raise RuntimeError('Docker '+args[0]+' fallo; detalle privado')
    return r
def owned(name):
    o=json.loads(docker('inspect',name).stdout)[0];assert o['Config']['Labels'].get('dimabug.wa.c3')==ID;return o
def sql(query):return docker('exec','-i',NAME+'-mysql','mysql','--defaults-extra-file=/tmp/root.cnf','-N','-B',stdin=query).stdout.strip()
try:
    old=json.loads(docker('inspect','wa-auto-base-6cbea583bc-mysql').stdout)[0]
    assert not old['State']['Running'] and old['Config']['Labels'].get('dimabug.wa.auto-base')=='6cbea583bc'
    volumes=[m['Name'] for m in old['Mounts'] if m['Destination']=='/var/lib/mysql'];assert volumes==['wa-auto-base-6cbea583bc-data']
    for o in json.loads(docker('ps','-q').stdout and docker('inspect',*docker('ps','-q').stdout.split()).stdout or '[]'):
        assert not any(m.get('Name')==volumes[0] for m in o['Mounts'])
    docker('network','create','--internal','--label','dimabug.wa.c3='+ID,NAME+'-net')
    shutil.copy2(base/'private/root',SEC/'root')
    docker('run','-d','--pull','never','--name',NAME+'-mysql','--label','dimabug.wa.c3='+ID,'--network',NAME+'-net','--memory','512m','--memory-swap','512m','--cpus','0.5','--pids-limit','96','--mount',f'type=volume,source={volumes[0]},target=/var/lib/mysql','--mount',f'type=bind,source={SEC},target=/run/qa,readonly','-e','MYSQL_ROOT_PASSWORD_FILE=/run/qa/root',MYSQL,'--performance-schema=OFF','--innodb-buffer-pool-size=134217728','--innodb-redo-log-capacity=134217728','--max-connections=8','--mysqlx=OFF','--default-time-zone=+00:00','--skip-name-resolve')
    created.append(NAME+'-mysql')
    docker('exec','-i',NAME+'-mysql','sh','-c','umask 077; cat > /tmp/root.cnf',stdin='[client]\nuser=root\npassword='+(SEC/'root').read_text()+'\n')
    for _ in range(120):
        r=docker('exec',NAME+'-mysql','mysql','--defaults-extra-file=/tmp/root.cnf','--protocol=TCP','--host=127.0.0.1','-Nse','SELECT 1',check=False)
        if r.returncode==0:break
        time.sleep(1)
    else:raise RuntimeError('MySQL QA no disponible')
    count=int(sql('SELECT COUNT(*) FROM wa_durable_b8fedc24e152.wa_evento'));report['initial_events']=count
    if args.stage=='B':
        # No reproducir salidas previas o inciertas al habilitar envío real.
        assert sql("SELECT COUNT(*) FROM wa_durable_b8fedc24e152.wa_respuesta WHERE estado IN ('PENDIENTE','REINTENTO','PREPARANDO','PREPARADA','ENVIANDO','INCIERTA')")=='0','Existen salidas pendientes; no se activa etapa B'
    (SEC/'application.properties').write_text('\n'.join(k+'='+v for k,v in props.items())+'\n',encoding='utf-8')
    javac=str(Path(shutil.which('java')).with_name('javac.exe'))
    subprocess.run([javac,'-d',str(CLASSES),str(ROOT/'qa/whatsapp/local-isolation/LoadProbe.java')],check=True,capture_output=True)
    h=Harness(docker=docker,owned=owned,created=created,name=NAME,run=RUN,sec=SEC,classes=CLASSES,java=JAVA,label=ID)
    with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
    proxy_options=[]
    if args.stage=='B':
        subprocess.run([javac,'-d',str(CLASSES),str(ROOT/'qa/whatsapp/GraphHttpsProxy.java')],check=True,capture_output=True)
        proxy_network=NAME+'-graph-net';docker('network','create','--label','dimabug.wa.c3='+ID,proxy_network)
        proxy_name=NAME+'-graph';created.append(proxy_name)
        docker('run','-d','--pull','never','--name',proxy_name,'--label','dimabug.wa.c3='+ID,'--network',proxy_network,'--memory','96m','--memory-swap','96m','--cpus','0.25','--pids-limit','48','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--user','10001:10001','--tmpfs','/tmp:rw,noexec,nosuid,size=16m','--mount',f'type=bind,source={CLASSES},target=/probe,readonly','--entrypoint','java',JAVA,'-Xmx32m','-cp','/probe','GraphHttpsProxy')
        docker('network','connect',NAME+'-net',proxy_name)
        assert owned(proxy_name)['State']['Running']
        assert not owned(proxy_name)['HostConfig'].get('PortBindings')
        proxy_options=['-Dhttps.proxyHost='+proxy_name,'-Dhttps.proxyPort=18888']
    tunnel=Session(harness=h,props=props,exe=ROOT/'qa/runtime/tools/cloudflared-2026.11.1.exe',port=port,minutes=60,real_meta=True,public_authorized=True,meta_outbound=args.stage=='B')
    docker('run','-d','--pull','never','--name',NAME+'-spring','--label','dimabug.wa.c3='+ID,'--network','container:'+NAME+'-mysql','--memory','512m','--memory-swap','512m','--cpus','0.5','--pids-limit','96','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--user','10001:10001','--tmpfs','/tmp:rw,noexec,nosuid,size=64m','--mount',f'type=bind,source={jar},target=/qa/app.jar,readonly','--mount',f'type=bind,source={SEC}/application.properties,target=/qa/application.properties,readonly',*token_mount,'--entrypoint','java',JAVA,*proxy_options,'-Xms64m','-Xmx192m','-XX:MaxDirectMemorySize=48m','-XX:MaxMetaspaceSize=128m','-XX:ReservedCodeCacheSize=48m','-jar','/qa/app.jar','--spring.config.location=file:/qa/application.properties')
    created.append(NAME+'-spring')
    # El JAR completo inicializa todos los mÃ³dulos; el artefacto de recepciÃ³n no lo hacÃ­a.
    # Esperar el contexto antes del cliente GET de 90 s, sin publicar el tÃºnel todavÃ­a.
    for _ in range(180):
        startup=docker('logs',NAME+'-spring',check=False)
        if 'Started BackendApplication in ' in startup.stdout+startup.stderr:break
        if not owned(NAME+'-spring')['State']['Running']:raise RuntimeError('Spring Boot termino durante el arranque')
        time.sleep(1)
    else:raise RuntimeError('Spring Boot completo excedio el plazo de arranque')
    probe=NAME+'-probe-ready';created.append(probe)
    r=docker('run','--pull','never','--name',probe,'--label','dimabug.wa.c3='+ID,'--network','container:'+NAME+'-mysql','--memory','256m','--memory-swap','256m','--cpus','0.5','--pids-limit','48','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--user','10001:10001','--mount',f'type=bind,source={CLASSES},target=/probe,readonly','--mount',f'type=bind,source={SEC}/application.properties,target=/qa/application.properties,readonly','--entrypoint','java',JAVA,'-Xmx64m','-cp','/probe','LoadProbe','ready','0',check=False,timeout=150)
    if r.returncode:
        (SEC/'probe.log').write_text(r.stdout+r.stderr);raise RuntimeError('Spring Boot QA no disponible')
    h.start();report['tunnel']=tunnel.start_and_wait(lambda:int(sql('SELECT COUNT(*) FROM wa_durable_b8fedc24e152.wa_evento'))==count)
    report['status']='PASS'
except Exception as e:report.update(status='FAIL',error=type(e).__name__+': '+str(e))
finally:
    if tunnel:tunnel.close()
    report['final_states']={}
    for name in reversed(created):
        try:
            o=owned(name)
            if name.endswith('-spring'):
                r=docker('logs',name,check=False);(SEC/'spring.log').write_text(r.stdout+r.stderr,encoding='utf-8')
            if o['State']['Running']:docker('stop','-t','30',name)
            report['final_states'][name]=owned(name)['State']['Status']
        except Exception:report['final_states'][name]='REVISAR'
    stopped=bool(created) and all(v=='exited' for v in report['final_states'].values())
    connector_stopped=not tunnel or not tunnel.process or tunnel.process.poll() is not None
    report['services_stopped']=stopped
    (RUN/'window-closure.json').write_text(json.dumps({'tunnel_state':'CLOSED','connector_stopped':connector_stopped,'qa_containers_stopped':stopped,'verified':stopped and connector_stopped},indent=2))
    (RUN/'result.json').write_text(json.dumps(report,indent=2))
    print(json.dumps({'runtime':str(RUN),'status':report.get('status'),'services_stopped':stopped,'error':report.get('error')}),flush=True)
raise SystemExit(0 if report.get('status')=='PASS' else 1)
