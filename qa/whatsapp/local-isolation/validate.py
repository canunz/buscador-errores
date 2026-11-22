"""QA local acotado. Solo imagen MySQL ya instalada, volumen y secretos propios."""
from pathlib import Path
import base64,concurrent.futures,ctypes,hashlib,hmac,http.client,json,os,secrets,shutil,socket,subprocess,sys,time,uuid

ROOT=Path(__file__).resolve().parents[3]
build=Path(sys.argv[1]).resolve()
if not build.is_relative_to(ROOT/'qa/runtime') or not build.name.startswith('wa-local-build-'):
    raise ValueError('Build fuera de QA')
compiled=json.loads((build/'informe.json').read_text())
jar=Path(compiled['jar']);assert hashlib.sha256(jar.read_bytes()).hexdigest()==compiled['jar_sha256']
ID=uuid.uuid4().hex[:10];NAME='wa-local-'+ID;DB='wa_durable_'+ID
RUN=ROOT/'qa/runtime'/NAME;RUN.mkdir()
SEC=RUN/'private';SEC.mkdir()
subprocess.run(['icacls',str(RUN),'/inheritance:r','/grant:r',os.environ['USERDOMAIN']+'\\'+os.environ['USERNAME']+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],capture_output=True,check=True)
IMAGE='sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
PASSWORD=secrets.token_hex(24);APP_PASSWORD=secrets.token_hex(24);TOKEN=secrets.token_hex(24);SECRET=secrets.token_hex(24)
report={'ubuntu_modificado':False,'meta':False,'nginx_real':False,'prueba_acotada_no_baseline_24h':True,'capacidad_aprobada':False,'certificados_reales_validados':False,'jar_sha256':compiled['jar_sha256']}
created=False;process=None
def docker(*args,stdin=None,check=True):
    p=subprocess.run(['docker','--context','desktop-linux',*args],input=stdin,text=True,capture_output=True,timeout=90)
    if check and p.returncode:raise RuntimeError('Docker fallo en '+args[0])
    return p
def sql(query):return docker('exec','-i',NAME,'mysql','--defaults-extra-file=/tmp/root.cnf','--batch','--skip-column-names',stdin=query).stdout.strip()
def port():
    with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def request(method,path,body=None,signature=None):
    c=http.client.HTTPConnection('127.0.0.1',HTTP,timeout=8)
    headers={'Content-Type':'application/json'}
    if signature:headers['X-Hub-Signature-256']=signature
    started=time.monotonic()
    try:
        c.request(method,path,body,headers);r=c.getresponse();return r.status,r.read(),time.monotonic()-started
    finally:c.close()
def identity():
    obj=json.loads(docker('inspect',NAME).stdout)[0]
    assert obj['Config']['Labels']['dimabug.qa.local']==ID and obj['Image']==IMAGE
    assert obj['HostConfig']['Memory']==512*1024**2
    assert obj['HostConfig']['PortBindings']['3306/tcp'][0]['HostIp']=='127.0.0.1'
    return obj
try:
    MYSQL=port();HTTP=port()
    (SEC/'root').write_text(PASSWORD);(SEC/'app').write_text(APP_PASSWORD)
    docker('volume','create','--label','dimabug.qa.local='+ID,NAME+'-data')
    docker('run','-d','--pull','never','--name',NAME,'--label','dimabug.qa.local='+ID,'--network','bridge','-p',f'127.0.0.1:{MYSQL}:3306','--memory','512m','--memory-swap','512m','--cpus','0.5','--pids-limit','96','--mount',f'type=volume,source={NAME}-data,target=/var/lib/mysql','--mount',f'type=bind,source={SEC},target=/run/qa,readonly','-e','MYSQL_ROOT_PASSWORD_FILE=/run/qa/root','-e','MYSQL_PASSWORD_FILE=/run/qa/app','-e','MYSQL_USER=wa_worker','-e','MYSQL_DATABASE='+DB,IMAGE,'--innodb-buffer-pool-size=134217728','--innodb-redo-log-capacity=134217728','--max-connections=8','--mysqlx=OFF','--default-time-zone=+00:00','--skip-name-resolve')
    created=True;identity()
    docker('exec','-i',NAME,'sh','-c','umask 077; cat > /tmp/root.cnf',stdin=f'[client]\nuser=root\npassword={PASSWORD}\nprotocol=socket\n')
    for _ in range(90):
        p=docker('exec',NAME,'mysql','--defaults-extra-file=/tmp/root.cnf','-Nse','SELECT 1',check=False)
        if p.returncode==0:break
        time.sleep(1)
    else:raise RuntimeError('MySQL no arranco bajo 512 MiB')
    sql('USE '+DB+';\n'+(ROOT/'qa/whatsapp/mysql-stage22a/schema.sql').read_text())
    sql(f"REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'wa_worker'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON `{DB}`.* TO 'wa_worker'@'%';")
    assert sql(f"SELECT count(*) FROM information_schema.tables WHERE table_schema='{DB}'")=='3'
    props={
        'spring.profiles.active':'qa-whatsapp-meta','server.address':'127.0.0.1','server.port':str(HTTP),'server.forward-headers-strategy':'none',
        'spring.datasource.url':f'jdbc:mysql://127.0.0.1:{MYSQL}/{DB}?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=5000',
        'spring.datasource.username':'wa_worker','spring.datasource.password':APP_PASSWORD,'spring.datasource.hikari.maximum-pool-size':'2','spring.datasource.hikari.minimum-idle':'0',
        'server.tomcat.threads.max':'8','spring.security.user.password':secrets.token_hex(24),
        'dimabug.whatsapp.meta-qa.waba':'qa-waba-local','dimabug.whatsapp.meta-qa.numero':'qa-numero-local','dimabug.whatsapp.meta-qa.participantes':'tel:100000000000001=qa-a',
        'dimabug.whatsapp.meta-qa.verify-token':TOKEN,'dimabug.whatsapp.meta-qa.app-secret':SECRET,
        'dimabug.whatsapp.durable.clave-cifrado':base64.b64encode(secrets.token_bytes(32)).decode(),'dimabug.whatsapp.durable.clave-hmac':base64.b64encode(secrets.token_bytes(32)).decode(),
        'logging.level.org.springframework.security.config.annotation.authentication.configuration.InitializeUserDetailsBeanManagerConfigurer':'ERROR'}
    cfg=SEC/'application.properties';cfg.write_text('\n'.join(k+'='+v for k,v in props.items()))
    env={k:v for k,v in os.environ.items() if k.upper() in {'SYSTEMROOT','WINDIR','SYSTEMDRIVE','COMSPEC','PATH','TEMP','TMP'}}
    log=(SEC/'spring.log').open('wb')
    process=subprocess.Popen([shutil.which('java'),'-Xms64m','-Xmx192m','-XX:MaxDirectMemorySize=48m','-XX:MaxMetaspaceSize=96m','-XX:ReservedCodeCacheSize=48m','-XX:+ExitOnOutOfMemoryError','-jar',str(jar),'--spring.config.additional-location='+cfg.as_uri()],env=env,stdout=log,stderr=subprocess.STDOUT)
    for _ in range(100):
        if process.poll() is not None:raise RuntimeError('JVM fallo al arrancar; log privado')
        try:
            if request('GET','/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token='+TOKEN+'&hub.challenge=123')[0]==200:break
        except OSError:pass
        time.sleep(.5)
    else:raise RuntimeError('Spring no disponible')
    assert request('GET','/api/publico/chatbot/consultas')[0]==403
    event={'object':'whatsapp_business_account','entry':[{'id':'qa-waba-local','changes':[{'field':'messages','value':{'messaging_product':'whatsapp','metadata':{'phone_number_id':'qa-numero-local'},'messages':[{'id':'qa-local-event','from':'100000000000001','timestamp':str(int(time.time())),'type':'text','text':{'body':'SINTETICO_QA'}}]}}]}]}
    body=json.dumps(event).encode();signature='sha256='+hmac.new(SECRET.encode(),body,hashlib.sha256).hexdigest()
    assert request('POST','/webhooks/whatsapp',body,signature)[0]==503
    assert request('POST','/webhooks/whatsapp',body,'sha256='+'0'*64)[0]==403
    report['recepcion_apagada_y_firma_invalida']=True
    # No habilitar recepción mediante API auxiliar. Reinicio explícito del mismo JAR.
    process.terminate();process.wait(timeout=30);log.close()
    with cfg.open('a') as f:f.write('\ndimabug.whatsapp.meta-qa.recepcion=true\n')
    log=(SEC/'spring.log').open('ab')
    args=[shutil.which('java'),'-Xms64m','-Xmx192m','-XX:MaxDirectMemorySize=48m','-XX:MaxMetaspaceSize=96m','-XX:ReservedCodeCacheSize=48m','-XX:+ExitOnOutOfMemoryError','-jar',str(jar),'--spring.config.additional-location='+cfg.as_uri()]
    process=subprocess.Popen(args,env=env,stdout=log,stderr=subprocess.STDOUT)
    for _ in range(100):
        if process.poll() is not None:raise RuntimeError('Reinicio JVM fallo')
        try:
            if request('GET','/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token='+TOKEN+'&hub.challenge=123')[0]==200:break
        except OSError:pass
        time.sleep(.5)
    else:raise RuntimeError('Reinicio no disponible')
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        responses=list(pool.map(lambda _:request('POST','/webhooks/whatsapp',body,signature),range(4)))
    assert all(r[0]==200 for r in responses)
    assert sql('SELECT count(*) FROM '+DB+'.wa_evento')=='1'
    report['duplicados_concurrentes']={'peticiones':4,'filas_evento':1,'latencias_ms':[round(r[2]*1000) for r in responses]}
    # Muestra acotada de 30 solicitudes a 2/s; no equivale a una carga sostenida.
    latencias=[]
    for _ in range(30):
        inicio=time.monotonic();r=request('POST','/webhooks/whatsapp',body,signature)
        assert r[0]==200;latencias.append(r[2]*1000)
        time.sleep(max(0,.5-(time.monotonic()-inicio)))
    ordenadas=sorted(latencias)
    report['carga_acotada']={'peticiones':30,'ritmo_s':2,'p95_ms':round(ordenadas[28]),'p99_ms':round(ordenadas[29])}
    assert ordenadas[28]<=1000 and ordenadas[29]<=3000
    report['mysql_pico_pre_reinicio_bytes']=docker('exec',NAME,'cat','/sys/fs/cgroup/memory.peak',check=False).stdout.strip()
    # Reiniciar exclusivamente el contenedor recién creado y comprobar el dato.
    identity();docker('restart','-t','20',NAME)
    for _ in range(60):
        try:
            if sql('SELECT count(*) FROM '+DB+'.wa_evento')=='1':break
        except RuntimeError:pass
        time.sleep(1)
    else:raise RuntimeError('Evento no recuperado tras reinicio MySQL exclusivo')
    for _ in range(20):
        r=request('POST','/webhooks/whatsapp',body,signature)
        if r[0]==200:break
        time.sleep(1)
    assert r[0]==200 and sql('SELECT count(*) FROM '+DB+'.wa_evento')=='1'
    report['reinicio_mysql_sin_perdida_ni_duplicado']=True
    # TLS ficticio, solo validación criptográfica offline: no Nginx ni ACME.
    openssl=Path('C:/Program Files/Git/usr/bin/openssl.exe')
    if not openssl.is_file():openssl=Path('C:/Program Files/Git/mingw64/bin/openssl.exe')
    def ssl(*args):return subprocess.run([str(openssl),*args],capture_output=True,timeout=30)
    assert ssl('req','-x509','-newkey','rsa:2048','-nodes','-days','2','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1','-keyout',str(SEC/'key.pem'),'-out',str(SEC/'cert.pem')).returncode==0
    assert ssl('verify','-CAfile',str(SEC/'cert.pem'),'-verify_hostname','localhost',str(SEC/'cert.pem')).returncode==0
    assert ssl('verify','-CAfile',str(SEC/'cert.pem'),'-verify_hostname','incorrecto.invalid',str(SEC/'cert.pem')).returncode!=0
    report['tls_sintetico']={'hostname_correcto':True,'hostname_incorrecto_rechazado':True,'real_certbot_validado':False}
    report['mysql_cgroup']=docker('stats','--no-stream','--format','{{json .}}',NAME).stdout.strip()
    cgroup={}
    for nombre in ['memory.current','memory.peak','memory.events']:
        p=docker('exec',NAME,'cat','/sys/fs/cgroup/'+nombre,check=False)
        cgroup[nombre]=p.stdout.strip() if p.returncode==0 else 'NO_DISPONIBLE'
    report['mysql_cgroup_mediciones_post_reinicio']=cgroup
    p=subprocess.run(['powershell','-NoProfile','-Command',f'Get-Process -Id {process.pid} | Select-Object WorkingSet64,PeakWorkingSet64,PrivateMemorySize64 | ConvertTo-Json'],capture_output=True,text=True,timeout=20)
    if p.returncode==0:report['spring_memoria_windows']=json.loads(p.stdout)
    log.flush()
    texto=(SEC/'spring.log').read_text(errors='replace')
    assert not any(s in texto for s in [PASSWORD,APP_PASSWORD,TOKEN,SECRET,'SINTETICO_QA','100000000000001'])
    report['log_spring_sin_marcadores_sensibles']=True
    report['mysql_limites']={'memory_mib':512,'cpu':0.5,'swap_extra':0,'pids':96}
    report['spring_limite_total_memoria']='NO_APLICADO: proceso Windows; flags JVM probados, cgroup pendiente'
    report['mysql_oom']=identity()['State']['OOMKilled']
    report['resultado']='APROBADO_ACOTADO'
except Exception as e:
    report.update(resultado='FALLIDO',error=type(e).__name__+': '+str(e))
finally:
    if process is not None and process.poll() is None:process.terminate();process.wait(timeout=30)
    if created:
        identity();docker('stop','-t','20',NAME)
        report['contenedor_final']=identity()['State']['Status']
    report['volumen_conservado']=NAME+'-data'
    (RUN/'informe.json').write_text(json.dumps(report,indent=2))
    print('EVIDENCIAS',RUN,flush=True);print(report.get('resultado'),report.get('error',''),flush=True)
sys.exit(0 if report.get('resultado')=='APROBADO_ACOTADO' else 1)
