"""C.3: comparación local de recursos, sin Ubuntu; redes internas y contenedores nuevos."""
from pathlib import Path
import argparse,base64,hashlib,json,os,secrets,shutil,socket,subprocess,time,uuid

ROOT=Path(__file__).resolve().parents[3]
parser=argparse.ArgumentParser()
parser.add_argument('build',type=Path)
parser.add_argument('--variant',choices=['baseline','optimized'],required=True)
parser.add_argument('--https',action='store_true',help='Comprobar Nginx real, HMAC y fallo SQL local')
parser.add_argument('--temporary-tunnel',action='store_true',help='Quick Tunnel QA con cierre automatico')
parser.add_argument('--window-minutes',type=int,choices=[20,30,60],default=20,help='Duracion autorizada; por defecto 20 minutos')
parser.add_argument('--cloudflared-exe',type=Path)
parser.add_argument('--prepare-reception',action='store_true',help='Solo comprobar localmente con recepcion apagada; nunca tunel')
parser.add_argument('--existing-qa-tls',type=Path)
parser.add_argument('--real-meta-config',type=Path)
parser.add_argument('--public-approval',help='Nunca usar sin autorizacion expresa del usuario')
parser.add_argument('--durable-smoke',action='store_true',help='Un evento sintetico local, HMAC, autorizacion y deduplicacion; sin tunel')
args=parser.parse_args()
if args.durable_smoke and (args.temporary_tunnel or args.real_meta_config or args.prepare_reception or args.https):parser.error('Prueba durable local incompatible con tunel, Meta u otros modos')
if args.temporary_tunnel and (not args.https or args.variant!='optimized' or args.cloudflared_exe is None):parser.error('Tunel requiere HTTPS, variante optimizada y binario verificado')
if args.prepare_reception and (args.temporary_tunnel or not args.https or args.existing_qa_tls is None):parser.error('Preparacion requiere HTTPS/TLS existente y prohibe crear un tunel')
if args.real_meta_config and (not args.temporary_tunnel or args.prepare_reception or args.existing_qa_tls is None or args.public_approval!=f'AUTORIZO_RECEPCION_REAL_META_{args.window_minutes}_MINUTOS'):parser.error('Recepcion real requiere archivo privado, TLS existente y autorizacion publica expresa para la duracion seleccionada; no se inicia')
if args.public_approval and args.real_meta_config is None:parser.error('Autorizacion sin configuracion real no valida')
meta_values=None
if args.real_meta_config:
    from meta_config import property_values
    meta_values=property_values(json.loads(args.real_meta_config.read_text(encoding='utf-8-sig')))
build=args.build.resolve()
if not build.is_relative_to(ROOT/'qa/runtime') or not build.name.startswith('wa-local-build-'):
    raise ValueError('Build fuera de QA')
compiled=json.loads((build/'informe.json').read_text());jar=Path(compiled['jar'])
assert hashlib.sha256(jar.read_bytes()).hexdigest()==compiled['jar_sha256']
MYSQL='sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
JAVA='eclipse-temurin@sha256:dff6eb46527bd293a3c06bbbe9e64e8c9f91f01905b5abbdad31761c03059d2c'
ID=uuid.uuid4().hex[:10];NAME=('wa-reception-prep-' if args.prepare_reception else 'wa-tunnel-' if args.temporary_tunnel else 'wa-nginx-' if args.https else 'wa-c3-')+ID;DB='wa_durable_'+ID
RUN=ROOT/'qa/runtime'/NAME;RUN.mkdir()
subprocess.run(['icacls',str(RUN),'/inheritance:r','/grant:r',os.environ['USERDOMAIN']+'\\'+os.environ['USERNAME']+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],capture_output=True,check=True)
SEC=RUN/'private';SEC.mkdir();CLASSES=RUN/'probe';CLASSES.mkdir()
subprocess.run([str(Path(shutil.which('java')).with_name('javac.exe')),'-d',str(CLASSES),str(Path(__file__).with_name('LoadProbe.java'))],check=True,capture_output=True)
password=secrets.token_hex(24);app_password=secrets.token_hex(24)
created=[];samples=[];https=None;tunnel=None
HOST_TLS_PORT=None
if args.temporary_tunnel:
    with socket.socket() as candidate:candidate.bind(('127.0.0.1',0));HOST_TLS_PORT=candidate.getsockname()[1]
report={'etapa':'3.2-C.3','variant':args.variant,'ubuntu':False,'meta':False,'red_interna':True,'jar_sha256':compiled['jar_sha256'],'mysql_image':MYSQL,'java_image':JAVA,'capacidad_host_ubuntu_validada':False}
def docker(*command,stdin=None,check=True,timeout=120):
    p=subprocess.run(['docker','--context','desktop-linux',*command],input=stdin,text=True,capture_output=True,timeout=timeout)
    if check and p.returncode:
        (SEC/'docker-last-error.log').write_text(p.stdout+p.stderr)
        raise RuntimeError('Docker fallo: '+command[0]+' (detalle privado)')
    return p
def inspect(name):return json.loads(docker('inspect',name).stdout)[0]
def owned(name):
    o=inspect(name);assert o['Config']['Labels'].get('dimabug.wa.c3')==ID
    return o
def sql(query):return docker('exec','-i',NAME+'-mysql','mysql','--defaults-extra-file=/tmp/root.cnf','-N','-B',stdin=query).stdout.strip()
def ready_mysql():
    for _ in range(120):
        # El servidor temporal del entrypoint escucha solo socket/puerto 0.
        # Esperar TCP evita confundirlo con el mysqld definitivo.
        p=docker('exec',NAME+'-mysql','mysql','--defaults-extra-file=/tmp/root.cnf','--protocol=TCP','--host=127.0.0.1','--port=3306','-Nse','SELECT 1',check=False)
        if p.returncode==0:return
        if not owned(NAME+'-mysql')['State']['Running']:raise RuntimeError('MySQL se detuvo')
        time.sleep(.5)
    raise RuntimeError('MySQL no disponible')
def measurement(name,phase):
    fields={}
    for key in ['memory.current','memory.peak','memory.stat','memory.events','cpu.stat','pids.current']:
        r=docker('exec',name,'cat','/sys/fs/cgroup/'+key,check=False)
        fields[key]=r.stdout.strip() if r.returncode==0 else 'NO_DISPONIBLE'
    state=owned(name)
    result={'phase':phase,'container':name,'timestamp':time.time(),'cgroup':fields,'oom_killed':state['State']['OOMKilled'],'restart_count':state['RestartCount']}
    samples.append(result)
    print('MEDICION',phase,'spring' if name.endswith('-spring') else 'mysql',fields['memory.current'],flush=True)
    return result
def probe(mode,count=0):
    name=NAME+'-probe-'+uuid.uuid4().hex[:5]
    p=docker('run','--pull','never','--name',name,'--label','dimabug.wa.c3='+ID,'--network','container:'+NAME+'-mysql','--memory','256m','--memory-swap','256m','--cpus','0.5','--pids-limit','48','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--user','10001:10001','--mount',f'type=bind,source={CLASSES},target=/probe,readonly','--mount',f'type=bind,source={SEC}/application.properties,target=/qa/application.properties,readonly','--entrypoint','java',JAVA,'-Xmx64m','-cp','/probe','LoadProbe',mode,str(count),check=False,timeout=150)
    if docker('inspect',name,check=False).returncode==0:created.append(name)
    if p.returncode:
        (SEC/(name+'.log')).write_text(p.stdout+p.stderr)
        raise RuntimeError('Cliente sintetico fallo (detalle privado)')
    return json.loads(p.stdout)
try:
    docker('network','create','--internal','--label','dimabug.wa.c3='+ID,NAME+'-net')
    docker('volume','create','--label','dimabug.wa.c3='+ID,NAME+'-data')
    (SEC/'root').write_text(password);(SEC/'app').write_text(app_password)
    tuning=['--innodb-buffer-pool-size=134217728','--innodb-redo-log-capacity=134217728','--max-connections=8','--mysqlx=OFF','--default-time-zone=+00:00','--skip-name-resolve']
    if args.variant=='optimized':
        tuning+=['--performance-schema=OFF','--table-open-cache=128','--table-definition-cache=400','--thread-cache-size=4','--tmp-table-size=16777216','--max-heap-table-size=16777216','--temptable-max-ram=33554432']
    report['mysql_flags']=tuning
    docker('run','-d','--pull','never','--name',NAME+'-mysql','--label','dimabug.wa.c3='+ID,'--network',NAME+'-net','--memory','512m','--memory-swap','512m','--cpus','0.5','--pids-limit','96','--mount',f'type=volume,source={NAME}-data,target=/var/lib/mysql','--mount',f'type=bind,source={SEC},target=/run/qa,readonly','-e','MYSQL_ROOT_PASSWORD_FILE=/run/qa/root','-e','MYSQL_PASSWORD_FILE=/run/qa/app','-e','MYSQL_USER=wa_worker','-e','MYSQL_DATABASE='+DB,MYSQL,*tuning)
    created.append(NAME+'-mysql')
    docker('exec','-i',NAME+'-mysql','sh','-c','umask 077; cat > /tmp/root.cnf',stdin=f'[client]\nuser=root\npassword={password}\nprotocol=socket\n')
    ready_mysql();measurement(NAME+'-mysql','initialization')
    report['mysql_version']=sql('SELECT VERSION()')
    report['effective_variables']=sql("SHOW VARIABLES WHERE Variable_name IN ('performance_schema','innodb_buffer_pool_size','table_open_cache','table_definition_cache','max_connections','thread_cache_size','tmp_table_size','max_heap_table_size','temptable_max_ram','innodb_flush_log_at_trx_commit','innodb_doublewrite','innodb_redo_log_capacity','sync_binlog')")
    sql('USE '+DB+';\n'+(ROOT/'qa/whatsapp/mysql-stage22a/schema.sql').read_text())
    sql(f"REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'wa_worker'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON `{DB}`.* TO 'wa_worker'@'%';")
    measurement(NAME+'-mysql','schema_ready')
    # Reinicio controlado: separa inicialización del datadir de operación normal.
    owned(NAME+'-mysql');docker('restart','-t','30',NAME+'-mysql');ready_mysql()
    measurement(NAME+'-mysql','normal_start')
    props={
        'spring.profiles.active':'qa-whatsapp-meta','server.address':'127.0.0.1','server.port':'8082','server.forward-headers-strategy':'none',
        'spring.datasource.url':f'jdbc:mysql://127.0.0.1:3306/{DB}?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=5000',
        'spring.datasource.username':'wa_worker','spring.datasource.password':app_password,'spring.datasource.hikari.maximum-pool-size':'2','spring.datasource.hikari.minimum-idle':'0',
        'server.tomcat.threads.max':'8','spring.security.user.password':secrets.token_hex(24),'server.shutdown':'graceful',
        'dimabug.whatsapp.meta-qa.recepcion':'false' if args.prepare_reception else 'true','dimabug.whatsapp.meta-qa.waba':'qa-waba-local','dimabug.whatsapp.meta-qa.numero':'qa-numero-local',
        'dimabug.whatsapp.meta-qa.participantes':','.join(f'tel:10000000000000{i}=qa-{i}' for i in range(1,5)),
        'dimabug.whatsapp.meta-qa.verify-token':secrets.token_hex(24),'dimabug.whatsapp.meta-qa.app-secret':secrets.token_hex(24),
        'dimabug.whatsapp.durable.clave-cifrado':base64.b64encode(secrets.token_bytes(32)).decode(),'dimabug.whatsapp.durable.clave-hmac':base64.b64encode(secrets.token_bytes(32)).decode()}
    if meta_values:
        props.update(meta_values)
        props['dimabug.whatsapp.meta-qa.recepcion']='true' # Solo tras el gate expreso anterior.
        report['real_reception_authorized']=True
    (SEC/'application.properties').write_text('\n'.join(k+'='+v for k,v in props.items()))
    if args.https:
        with (SEC/'application.properties').open('a') as f:
            f.write('\nspring.datasource.hikari.connection-timeout=3000\nspring.datasource.hikari.validation-timeout=1000\nlogging.level.org.hibernate.orm.connections.pooling=WARN\nqa.probe.timestamp='+str(int(time.time()))+'\n')
        from nginx_https import Harness
        https=Harness(docker=docker,owned=owned,created=created,name=NAME,run=RUN,sec=SEC,classes=CLASSES,java=JAVA,label=ID,tls_existing=args.existing_qa_tls,tls_min_seconds=args.window_minutes*60+600)
        if args.temporary_tunnel:
            from temporary_tunnel import Session
            tunnel=Session(harness=https,props=props,exe=args.cloudflared_exe,port=HOST_TLS_PORT,minutes=args.window_minutes,real_meta=bool(meta_values),public_authorized=bool(meta_values))
    # Comparte SOLO namespace de red con su MySQL: JDBC loopback, sin puertos publicados.
    docker('run','-d','--pull','never','--name',NAME+'-spring','--label','dimabug.wa.c3='+ID,'--network','container:'+NAME+'-mysql','--memory','512m','--memory-swap','512m','--cpus','0.5','--pids-limit','96','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--user','10001:10001','--tmpfs','/tmp:rw,noexec,nosuid,size=64m','--mount',f'type=bind,source={jar},target=/qa/app.jar,readonly','--mount',f'type=bind,source={SEC}/application.properties,target=/qa/application.properties,readonly','--entrypoint','java',JAVA,'-Xms64m','-Xmx192m','-XX:MaxDirectMemorySize=48m','-XX:MaxMetaspaceSize=96m','-XX:ReservedCodeCacheSize=48m','-XX:+ExitOnOutOfMemoryError','-jar','/qa/app.jar','--spring.config.additional-location=file:/qa/application.properties')
    created.append(NAME+'-spring');probe('ready')
    measurement(NAME+'-spring','spring_ready');measurement(NAME+'-mysql','spring_ready')
    if https:
        https.start()
        report['nginx_image']=__import__('nginx_https').IMAGE
        if args.prepare_reception:
            report['local_reception_paused']=probe('paused')
            report['public_reception']=False
            report['meta_connected']=False
            expected=0
        elif tunnel:
            report['temporary_tunnel']=tunnel.start_and_wait(lambda:int(sql('SELECT count(*) FROM '+DB+'.wa_evento'))==(0 if meta_values else 1))
            expected=None
        else:
            report['https']=https.probe('cases')
            assert sql('SELECT count(*) FROM '+DB+'.wa_evento')=='2'
            owned(NAME+'-mysql');docker('pause',NAME+'-mysql')
            try:report['mysql_fault']=https.probe('fault')
            finally:
                owned(NAME+'-mysql');docker('unpause',NAME+'-mysql')
            ready_mysql()
            time.sleep(1)
            assert sql('SELECT count(*) FROM '+DB+'.wa_evento')=='2'
            report['mysql_fault_no_commit']=True
            report['mysql_retry']=https.probe('retry')
            expected=3
        measurement(NAME+'-spring','https_and_fault');measurement(NAME+'-mysql','https_and_fault')
    elif args.durable_smoke:
        report['durable_smoke']=probe('durable-smoke')
        expected=1
        assert sql('SELECT count(*) FROM '+DB+'.wa_evento WHERE tipo=\'TEXTO\' AND estado=\'PENDIENTE\' AND intentos=0 AND OCTET_LENGTH(contenido_cifrado)>0')=='1'
        assert sql('SELECT count(*) FROM '+DB+'.wa_respuesta')=='0'
    else:
        report['warmup']=probe('warmup',20)
        measurement(NAME+'-spring','warmup');measurement(NAME+'-mysql','warmup')
        report['load']=probe('load',120)
        measurement(NAME+'-spring','load');measurement(NAME+'-mysql','load')
        expected=report['warmup']['unique_events']+report['load']['unique_events']
    count=int(sql('SELECT count(*) FROM '+DB+'.wa_evento'))
    if expected is not None:assert count==expected
    report['eventos_esperados']=expected;report['eventos_persistidos']=count
    report['sql_status']=sql("SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Max_used_connections','Created_tmp_tables','Created_tmp_disk_tables','Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests')")
    # No hay restart automático; comprobar estados y límites efectivos por contenedor.
    report['limits']={}
    for name in [NAME+'-spring',NAME+'-mysql']:
        o=owned(name);h=o['HostConfig']
        assert h['Memory']==512*1024**2 and h['MemorySwap']==512*1024**2 and h['NanoCpus']==500000000
        assert not h.get('PortBindings')
        report['limits'][name]={k:h.get(k) for k in ['Memory','MemorySwap','NanoCpus','PidsLimit','NetworkMode']}
        assert o['State']['Running'] and not o['State']['OOMKilled'] and o['RestartCount']==0
    text=docker('logs',NAME+'-spring').stdout
    (SEC/'spring.log').write_text(text)
    assert not any(v in text for v in [password,app_password,props['dimabug.whatsapp.meta-qa.verify-token'],props['dimabug.whatsapp.meta-qa.app-secret'],'SINTETICO_C3'])
    report['functional']='PASS'
except Exception as e:
    report.update(functional='FAIL',error=type(e).__name__+': '+str(e))
finally:
    if tunnel:tunnel.close()
    for name in [NAME+'-spring',NAME+'-mysql']:
        if name in created:
            try:
                p=docker('logs',name,check=False)
                (SEC/(name+'.log')).write_text(p.stdout+p.stderr)
            except Exception:pass
    report['samples']=samples
    report['final_states']={}
    for name in reversed(created):
        try:
            o=owned(name)
            if o['State'].get('Paused'):docker('unpause',name)
            if o['State']['Running']:docker('stop','-t','30',name)
            report['final_states'][name]=owned(name)['State']['Status']
        except Exception:report['final_states'][name]='REVISAR'
    report['volumen_conservado']=NAME+'-data';report['network_conservada']=NAME+'-net'
    if https:
        try:
            asset_markers=[]
            if meta_values:
                asset_markers=[props['dimabug.whatsapp.meta-qa.waba'],props['dimabug.whatsapp.meta-qa.numero']]
                asset_markers += [p.split('=',1)[0].split(':',1)[1] for p in props['dimabug.whatsapp.meta-qa.participantes'].split(',')]
            report['log_privacy']=https.scan_logs([password,app_password,*asset_markers,*([tunnel.password,'Basic '+base64.b64encode((tunnel.username+':'+tunnel.password).encode()).decode(),'SOLO_EVENTO_SINTETICO_TUNEL'] if tunnel else []),*[v for k,v in props.items() if any(s in k for s in ['password','secret','token','clave'])]])
            if tunnel:tunnel.audit_log()
        except Exception as e:
            report['privacy_error']=type(e).__name__+': '+str(e)
            report['functional']='FAIL'
            report.setdefault('error',report['privacy_error'])
    if tunnel:
        closed=json.loads((RUN/'tunnel-status.json').read_text())
        verified=closed.get('state')=='CLOSED' and tunnel.process is not None and tunnel.process.poll() is not None and all(s=='exited' for s in report['final_states'].values())
        report['shutdown_verified']=verified
        (RUN/'window-closure.json').write_text(json.dumps({'tunnel_state':closed.get('state'),'connector_stopped':tunnel.process is not None and tunnel.process.poll() is not None,'qa_containers_stopped':all(s=='exited' for s in report['final_states'].values()),'verified':verified},indent=2))
        if not verified:report.update(functional='FAIL',error='Cierre de ventana requiere revision local')
    (RUN/'informe.json').write_text(json.dumps(report,indent=2))
    print('EVIDENCIAS',RUN,flush=True);print(report.get('functional'),report.get('error',''),flush=True)
raise SystemExit(0 if report.get('functional')=='PASS' else 1)
