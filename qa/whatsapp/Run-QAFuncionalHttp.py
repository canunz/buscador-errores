"""QA TCP exclusivo: copia/build nuevos, H2 en memoria, loopback y secretos sintéticos.
No modifica fuentes/configuración anteriores ni usa MySQL/Meta. Ejecución explícita.
"""
from pathlib import Path
import collections, hashlib, hmac, http.client, json, os, secrets, shutil, socket
import subprocess, sys, time, uuid, zipfile

ROOT = Path(__file__).resolve().parents[2]

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def clean_env(java):
    allowed = ('SystemRoot','WINDIR','SystemDrive','USERPROFILE','LOCALAPPDATA','APPDATA','ProgramFiles','ProgramFiles(x86)','ComSpec','PATHEXT')
    env = {k:os.environ[k] for k in allowed if k in os.environ}
    env['PATH'] = str(Path(java).parent)+os.pathsep+str(Path(env.get('SystemRoot','C:/Windows'))/'System32')
    env['TEMP'] = env['TMP'] = str(RUN/'tmp')
    env['NO_PROXY'] = '127.0.0.1,localhost'
    return env

def write_new(path, content):
    with path.open('x',encoding='utf-8') as f:f.write(content)

def event(kind='text', text='Error 0x0000011b', user='100000000000001', mid=None):
    detail={'body':text} if kind=='text' else {'id':text,'mime_type':'image/png'}
    return envelope({'messages':[{'id':mid or 'wamid.TCP_'+uuid.uuid4().hex,'from':user,'timestamp':'1794646800','type':kind,kind:detail}]})

def envelope(value, field='messages'):
    merged={'messaging_product':'whatsapp','metadata':{'phone_number_id':'100000000000002'},**value}
    return json.dumps({'object':'whatsapp_business_account','entry':[{'id':'100000000000003','changes':[{'field':field,'value':merged}]}]},ensure_ascii=False,separators=(',',':')).encode()

def query(params):
    from urllib.parse import urlencode
    return '/webhooks/whatsapp?'+urlencode(params)

def request(method,path,body=None,signed=False,signature=None,source=None,record=True):
    headers={}
    if body is not None:
        headers['Content-Type']='application/json'
        if signed: headers['X-Hub-Signature-256']='sha256='+hmac.new(SECRET.encode(),body,hashlib.sha256).hexdigest()
        if signature is not None: headers['X-Hub-Signature-256']=signature
        if 'X-Hub-Signature-256' in headers: SIGNATURES.add(headers['X-Hub-Signature-256'])
    conn=http.client.HTTPConnection('127.0.0.1',PORT,timeout=45,source_address=source)
    try:
        conn.request(method,path,body=body,headers=headers)
        response=conn.getresponse();data=response.read();status=response.status
        value={'status':status,'body':data.decode('utf-8',errors='replace'),'headers':dict(response.getheaders())}
        if record:
            REQUESTS.append({'method':method,'path':path.split('?')[0],'query_present':'?' in path,
                'body_bytes':len(body) if body is not None else 0,
                'body_sha256':hashlib.sha256(body).hexdigest() if body is not None else None,
                'signature_recorded':False,'response':value})
        return status,data
    finally:conn.close()

def check(condition,description):
    if not condition:raise AssertionError(description)

def case(number,name,planned,expected,fn):
    begin=len(REQUESTS)
    try:observed=fn();state='APROBADO';error=None
    except Exception as exc:observed='No cumplió la comprobación';state='FALLIDO';error=type(exc).__name__+': '+str(exc)
    row={'id':f'QA-WA-{number:03d}','scenario':name,'synthetic_input':planned,'expected':expected,
         'observed':observed,'state':state,'error':error,'request_indices':list(range(begin,len(REQUESTS)))}
    RESULTS.append(row);print(row['id'],state,flush=True)
    write_new(RUN/f"{row['id']}.json",json.dumps(row,ensure_ascii=False,indent=2)+'\n')


def tests():
    def get_ok():
        challenge=' 000123 + á '
        s,b=request('GET',query({'hub.mode':'subscribe','hub.verify_token':TOKEN,'hub.challenge':challenge}))
        check(s==200 and b==challenge.encode(),'challenge exacto UTF-8');return '200 y challenge exacto, incluidos ceros/espacios/UTF-8'
    case(1,'GET válido','Token sintético y challenge con ceros, espacios y UTF-8','200; bytes exactos',get_ok)
    def wrong_token():
        s,b=request('GET',query({'hub.mode':'subscribe','hub.verify_token':'incorrecto-sintetico','hub.challenge':'x'}))
        check(s==403,'rechazo de token');check(TOKEN.encode() not in b and SECRET.encode() not in b,'sin secretos');return '403 genérico'
    case(2,'Token incorrecto','Token ficticio no coincidente','Rechazo sin secretos',wrong_token)
    def missing():
        for omitted in ('hub.mode','hub.verify_token','hub.challenge'):
            args={'hub.mode':'subscribe','hub.verify_token':TOKEN,'hub.challenge':'x'};del args[omitted]
            check(request('GET',query(args))[0]==403,'parámetro obligatorio')
        return 'Tres solicitudes incompletas rechazadas con 403'
    case(3,'Parámetros incompletos','Omisión individual de hub.*','Rechazo controlado',missing)
    def valid_signature():
        payload=event();s,b=request('POST','/webhooks/whatsapp',payload,signed=True)
        r=json.loads(b)['respuestas'][0];check(s==200 and r['estado']=='RESULTADOS','firma y texto aceptados')
        check([x['id'] for x in r['conocimientos']]==[10],'solo solución pública');return '200, RESULTADOS y solución pública 10'
    case(4,'Firma válida','Evento sintético y HMAC en memoria','Aceptación y respuesta simulada',valid_signature)
    def invalid_signature():
        payload=event(mid='wamid.TCP_firma_invalida')
        check(request('POST','/webhooks/whatsapp',payload,signature='sha256'+'='+'0'*64)[0]==403,'firma inválida')
        s,b=request('POST','/webhooks/whatsapp',payload,signed=True)
        check(s==200 and json.loads(b)['respuestas'][0]['estado']=='RESULTADOS','rechazo no marcó el evento como procesado')
        return '403; mismo ID aceptado después con firma válida (no hubo procesamiento previo)'
    case(5,'Firma incorrecta','Firma inválida; luego mismo ID firmado correctamente','Rechazo antes de procesar',invalid_signature)
    def altered():
        original=event(mid='wamid.TCP_cuerpo_alterado');sig='sha256='+hmac.new(SECRET.encode(),original,hashlib.sha256).hexdigest()
        check(request('POST','/webhooks/whatsapp',original+b' ',signature=sig)[0]==403,'bytes alterados')
        s,b=request('POST','/webhooks/whatsapp',original,signed=True)
        check(s==200 and json.loads(b)['respuestas'][0]['estado']=='RESULTADOS','rechazo sin efectos de deduplicación')
        return '403 al alterar bytes; mismo evento original posteriormente aceptado'
    case(6,'Cuerpo alterado','Espacio añadido tras firmar','Firma rechazada sin efectos',altered)
    def search():
        s,b=request('POST','/webhooks/whatsapp',event(),signed=True);r=json.loads(b)['respuestas'][0]
        check(s==200 and [x['id'] for x in r['conocimientos']]==[10],'solución sintética pública conocida')
        for word in ('borradorunicoxz','reservadoprivadowx','inactivounicoxz','internoreservadoxz'):
            s,b=request('POST','/webhooks/whatsapp',event(text=word),signed=True)
            check(s==200 and json.loads(b)['respuestas'][0]['estado']=='SIN_RESULTADOS','filtros públicos '+word)
        return 'Recupera ID 10; borrador, privado, inactivo y campo interno no generan resultados'
    case(7,'Texto y visibilidad','Error conocido y cuatro marcadores reservados ficticios','Solo contenido autorizado',search)
    def image():
        s,b=request('POST','/webhooks/whatsapp',event('image','qa-media-legible',user='100000000000008'),signed=True)
        r=json.loads(b)['respuestas'][0]
        check(s==200 and r['estado']=='CONFIRMACION','propuesta OCR')
        check(r['candidatos'][0]['texto']=='Error 0x0000011b' and not r['conocimientos'],'OCR real sin búsqueda automática')
        s,b=request('POST','/webhooks/whatsapp',event(text='CONFIRMAR '+r['referencia'],user='100000000000008'),signed=True)
        check(s==200 and json.loads(b)['respuestas'][0]['conocimientos'][0]['id']==10,'confirmación busca contenido público')
        return 'OCR Node real sobre fixture: propuesta exacta; confirmación recupera solución 10'
    case(8,'Imagen y confirmación','Fixture sintético local, sin descargas','Propuesta OCR y búsqueda tras confirmar',image)
    def delivery():
        payload=envelope({'statuses':[{'id':'wamid.TCP_estado','recipient_id':'100000000000009','timestamp':'1794646800','status':'delivered'}]})
        s,b=request('POST','/webhooks/whatsapp',payload,signed=True);r=json.loads(b)
        check(s==200 and r['estados']==1 and not r['respuestas'],'estado sin mensaje ni búsqueda');return '200, estados=1, respuestas vacías'
    case(9,'Estado de entrega','Estado delivered ficticio','Sin búsqueda',delivery)
    def unknown():
        s,b=request('POST','/webhooks/whatsapp',envelope({'dato':'sintetico'},field='campo_futuro'),signed=True)
        check(s==200 and json.loads(b)['ignorados']==1,'evento ignorado')
        s,b=request('POST','/webhooks/whatsapp',event('audio','qa-audio-no-descargado'),signed=True)
        check(s==200 and json.loads(b)['respuestas'][0]['estado']=='NO_COMPATIBLE','tipo no compatible')
        return 'Campo futuro ignorado y audio NO_COMPATIBLE, ambos 200'
    case(10,'Eventos desconocidos','Campo futuro y audio sintético','Manejo controlado',unknown)
    def oversize():
        payload=event(mid='wamid.TCP_limite');large=payload+b' '*(16385-len(payload))
        check(request('POST','/webhooks/whatsapp',large,signed=True)[0]==413,'límite 16 KiB')
        s,b=request('POST','/webhooks/whatsapp',payload,signed=True)
        check(s==200 and json.loads(b)['respuestas'][0]['estado']=='RESULTADOS','exceso no procesó el ID')
        return '413 para 16385 bytes; mismo ID aceptado posteriormente sin duplicado'
    case(11,'Límite de tamaño','16385 bytes firmados','413 sin procesamiento',oversize)
    def security():
        for method in ('PUT','DELETE','PATCH','HEAD','OPTIONS'):
            check(request(method,'/webhooks/whatsapp')[0]==403,'método '+method)
        for path in ('/webhooks/whatsapp/','/webhooks/whatsapp/otra','/webhooks/otro','/api/conocimientos','/api/tickets','/api/admin/prueba'):
            check(request('GET',path)[0]==401,'ruta protegida '+path)
        check(request('POST','/webhooks/whatsapp',event())[0]==403,'sin firma')
        network=json.loads(subprocess.check_output([str(Path(ENV['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'),'-NoProfile','-Command',
            f"@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object Port -eq {PORT} | ForEach-Object {{ $_.Address.ToString() }}) | ConvertTo-Json -Compress"],env=ENV).decode('utf-8-sig'))
        if isinstance(network,str):network=[network]
        check(network==['127.0.0.1'],'listener exclusivamente loopback')
        write_new(RUN/'listener.json',json.dumps({'port':PORT,'addresses':network},indent=2))
        try:
            status,_=request('GET',query({'hub.mode':'subscribe','hub.verify_token':TOKEN,'hub.challenge':'peer'}),source=('127.0.0.2',0))
            check(status==403,'peer no permitido');peer='origen 127.0.0.2 rechazado con 403'
        except OSError:peer='origen alternativo no disponible; no se expuso interfaz externa'
        return 'Binding 127.0.0.1 verificado, métodos/rutas protegidos; '+peer
    case(12,'Seguridad HTTP','Sin JWT; métodos, subrutas y origen alternativo local','Solo ruta/métodos autorizados',security)
    def privacy():
        logs=(RUN/'servidor.log').read_text(encoding='utf-8',errors='replace')
        sensitive=[TOKEN,SECRET,JWT,DB_PASSWORD,*SIGNATURES,'100000000000001','100000000000008','Error 0x0000011b']
        found=[i for i,v in enumerate(sensitive) if v and v in logs]
        check(not found,'logs contienen marcadores sensibles; detalles omitidos')
        write_new(RUN/'privacidad.json',json.dumps({'checks':len(sensitive),'matches':0,'raw_requests_logged':False,'log_sha256_at_check':sha(RUN/'servidor.log')},indent=2))
        return 'Sin secretos, firmas, remitentes ficticios ni texto OCR en logs de servidor'
    case(13,'Privacidad','Inspección del log real sin imprimir secretos','Sin contenido sensible',privacy)
    def availability():
        check(SERVER.poll() is None,'servidor sigue vivo')
        for _ in range(3):
            check(request('GET',query({'hub.mode':'subscribe','hub.verify_token':TOKEN,'hub.challenge':'disponible'}))[0]==200,'verificación después de inválidas')
        return 'Proceso vivo y tres GET válidos después de solicitudes inválidas'
    case(14,'Disponibilidad','Tres verificaciones tras pruebas inválidas','Servidor operativo',availability)


def main():
    global RUN,ENV,TOKEN,SECRET,JWT,DB_PASSWORD,PORT,SERVER,REQUESTS,RESULTS,SIGNATURES
    REQUESTS=[];RESULTS=[];SIGNATURES=set();SERVER=None
    RUN=ROOT/'qa/runtime'/('whatsapp-tcp-20261114-'+uuid.uuid4().hex[:10]);RUN.mkdir(exist_ok=False);(RUN/'tmp').mkdir()
    print('EVIDENCIA',str(RUN),flush=True)
    write_new(RUN/'git-inicial.txt',subprocess.check_output(['git','status','--short'],cwd=ROOT).decode())
    JAVA=shutil.which('java.exe') or shutil.which('java');check(JAVA is not None,'Java local requerido')
    ENV=clean_env(JAVA)
    TOKEN=secrets.token_hex(20);SECRET=secrets.token_hex(20);JWT=secrets.token_hex(48);DB_PASSWORD=secrets.token_hex(20)
    sources=ROOT/'dima-bug-backend'
    copy=RUN/'build';(copy/'src/main').mkdir(parents=True)
    shutil.copy2(sources/'pom.xml',copy/'pom.xml');shutil.copytree(sources/'src/main/java',copy/'src/main/java')
    # No copiar perfiles, credenciales, SQL de migración ni recursos de producción.
    (copy/'src/main/resources').mkdir()
    banner=sources/'src/main/resources/correo'
    if banner.exists():shutil.copytree(banner,copy/'src/main/resources/correo')
    source_hashes={p.relative_to(sources).as_posix():sha(p) for p in (sources/'src/main/java').rglob('*.java')}
    check(all(sha(copy/name)==digest for name,digest in source_hashes.items()),'copia exacta de Java')
    write_new(RUN/'fuentes.json',json.dumps(source_hashes,indent=2))
    m2=Path(ENV['USERPROFILE'])/'.m2'
    boot=next((m2/'wrapper/dists').glob('apache-maven-3.9.16*/**/plexus-classworlds*.jar'))
    mvn=boot.parent.parent
    cmd=[JAVA,'-Dmaven.home='+str(mvn),'-Dmaven.multiModuleProjectDirectory='+str(copy),'-Dclassworlds.conf='+str(mvn/'bin/m2.conf'),
         '-classpath',str(boot),'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-Dstyle.color=never','-Dmaven.test.skip=true','package']
    with (RUN/'build.log').open('xb') as log:
        result=subprocess.run(cmd,cwd=copy,env=ENV,stdout=log,stderr=subprocess.STDOUT,timeout=240)
    check(result.returncode==0,'falló build QA; consultar build.log')
    jar=copy/'target/backend-0.0.1-SNAPSHOT.jar'
    bundle=RUN/'bundle';(bundle/'classes').mkdir(parents=True);(bundle/'lib').mkdir()
    with zipfile.ZipFile(jar) as archive:
        check('BOOT-INF/classes/cl/casol/backend/whatsapp/infrastructure/http/WebhookWhatsAppController.class' in archive.namelist(),'JAR actualizado')
        for name in archive.namelist():
            if name.startswith('BOOT-INF/classes/') and not name.endswith('/'):
                relative=Path(name).relative_to('BOOT-INF/classes');target=bundle/'classes'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.read(name))
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar') and 'mysql-connector' not in name:
                (bundle/'lib'/Path(name).name).write_bytes(archive.read(name))
    h2=next((m2/'repository/com/h2database/h2/2.4.240').glob('h2-2.4.240.jar'));shutil.copy2(h2,bundle/'lib'/h2.name)
    check(not any((bundle/'classes').glob('application*')),'no perfiles anteriores en bundle')
    check(not any((bundle/'lib').glob('*mysql*')),'sin driver MySQL en runtime QA')
    write_new(RUN/'artefacto.json',json.dumps({'jar':jar.relative_to(ROOT).as_posix(),'jar_sha256':sha(jar),'h2_version':'2.4.240','h2_sha256':sha(h2),
        'source_files':len(source_hashes),'http_controller_present':True,'production_configs_copied':False,'mysql_driver_in_runtime':False},indent=2))
    sock=socket.socket();sock.bind(('127.0.0.1',0));PORT=sock.getsockname()[1];sock.close()
    data=RUN/'fixtures.sql'
    sql="""CREATE TABLE IF NOT EXISTS co_conocimiento_busqueda (conocimiento_id INT PRIMARY KEY, contenido_busqueda TEXT NOT NULL);
INSERT INTO u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) VALUES(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) VALUES(1,1,'QA SINTETICO','qa@example.invalid','SIN_LOGIN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
"""
    for i,title,state in [(1,'Error 0x0000011b','PUBLICADO'),(2,'borradorunicoxz','BORRADOR'),(3,'reservadoprivadowx','PUBLICADO'),(4,'inactivounicoxz','PUBLICADO')]:
        sql+=f"INSERT INTO co_conocimiento(conocimiento_id,conocimiento_titulo,conocimiento_descripcion,conocimiento_estado,creado_por,fecha_creacion) VALUES({i},'{title}','internoreservadoxz','{state}',1,CURRENT_TIMESTAMP);\n"
        sql+=f"INSERT INTO co_solucion(solucion_id,conocimiento_id,solucion_titulo,solucion_descripcion,solucion_tipo,solucion_orden,solucion_estado,solucion_autoservicio) VALUES({i*10},{i},'Solución sintética {i}','Pasos sintéticos públicos QA','PASOS',1,{'false' if i==4 else 'true'},{'false' if i==3 else 'true'});\n"
    write_new(data,sql)
    props={
        'spring.application.name':'dimabug-whatsapp-tcp-qa','server.address':'127.0.0.1','server.port':str(PORT),
        'spring.datasource.url':'jdbc:h2:mem:wa_func_'+uuid.uuid4().hex+';MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
        'spring.datasource.driver-class-name':'org.h2.Driver','spring.datasource.username':'sa','spring.datasource.password':'${WA_QA_H2_PASSWORD}',
        'spring.jpa.hibernate.ddl-auto':'create-drop','spring.jpa.defer-datasource-initialization':'true',
        'spring.sql.init.mode':'always','spring.sql.init.data-locations':data.as_uri(),'spring.jpa.show-sql':'false',
        'jwt.secret':'${WA_QA_JWT_SECRET}','jwt.expiration':'1800000',
        'dimabug.whatsapp.http-local.verify-token':'${WA_QA_VERIFY_TOKEN}','dimabug.whatsapp.http-local.app-secret':'${WA_QA_APP_SECRET}',
        'dimabug.whatsapp.qa.raiz':ROOT.as_posix(),'dimabug.gmail.oauth-habilitado':'false','dimabug.gmail.envio-real-habilitado':'false',
        'dimabug.auth.correo.habilitado':'false','dimabug.automatizacion.seguimiento-habilitado':'false','dimabug.automatizacion.modo':'DESHABILITADO',
        'spring.autoconfigure.exclude':'org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration',
        'spring.flyway.enabled':'false','spring.liquibase.enabled':'false','spring.h2.console.enabled':'false',
        'logging.level.root':'INFO','spring.mvc.log-request-details':'false','server.tomcat.accesslog.enabled':'false',
        'dimabug.storage.materiales-dir':(RUN/'data/materiales').as_posix(),'dimabug.storage.tickets-dir':(RUN/'data/tickets').as_posix()}
    config=RUN/'application-http-functional.properties';write_new(config,'\n'.join(k+'='+v for k,v in props.items())+'\n')
    check(props['spring.datasource.url'].startswith('jdbc:h2:mem:') and props['server.address']=='127.0.0.1','aislamiento obligatorio')
    runtime_env=ENV|{'SPRING_CONFIG_LOCATION':config.as_uri(),'SPRING_PROFILES_ACTIVE':'qa-whatsapp-http-local',
        'WA_QA_VERIFY_TOKEN':TOKEN,'WA_QA_APP_SECRET':SECRET,'WA_QA_JWT_SECRET':JWT,'WA_QA_H2_PASSWORD':DB_PASSWORD}
    log=(RUN/'servidor.log').open('xb')
    try:
        SERVER=subprocess.Popen([JAVA,'-cp',str(bundle/'classes')+os.pathsep+str(bundle/'lib/*'),'cl.casol.backend.BackendApplication'],
            cwd=RUN,env=runtime_env,stdout=log,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        deadline=time.monotonic()+100;ready=False
        while time.monotonic()<deadline:
            check(SERVER.poll() is None,'servidor terminó durante inicio; consultar log')
            try:
                s,b=request('GET',query({'hub.mode':'subscribe','hub.verify_token':TOKEN,'hub.challenge':'ready'}),record=False)
                if s==200 and b==b'ready':ready=True;break
            except (OSError,http.client.HTTPException):pass
            time.sleep(.5)
        check(ready,'servidor no disponible dentro del plazo')
        logs=(RUN/'servidor.log').read_text(encoding='utf-8',errors='replace')
        check('jdbc:h2:mem:wa_func_' in logs and 'jdbc:mysql:' not in logs,'base H2 exclusiva confirmada en log')
        write_new(RUN/'inicio.json',json.dumps({'started':True,'pid':SERVER.pid,'port':PORT,'binding':'127.0.0.1','database':'H2 private in-memory',
            'profile':'qa-whatsapp-http-local','synthetic_data_only':True,'secret_values_recorded':False},indent=2))
        print('SERVIDOR_LISTO loopback; H2 efímera verificada',flush=True)
        tests()
    finally:
        if SERVER and SERVER.poll() is None:
            SERVER.terminate()
            try:SERVER.wait(timeout=15)
            except subprocess.TimeoutExpired:SERVER.kill();SERVER.wait(timeout=10)
        log.close()
        write_new(RUN/'http.json',json.dumps(REQUESTS,ensure_ascii=False,indent=2)+'\n')
        write_new(RUN/'matriz.json',json.dumps(RESULTS,ensure_ascii=False,indent=2)+'\n')
        write_new(RUN/'cierre.json',json.dumps({'server_started_by_this_run_only':SERVER is not None,'server_stopped':SERVER is None or SERVER.poll() is not None,
            'h2_persistence':'none; in-memory JVM ended','other_processes_stopped':False},indent=2))
    summary={'passed':sum(r['state']=='APROBADO' for r in RESULTS),'failed':sum(r['state']=='FALLIDO' for r in RESULTS),'blocked':14-len(RESULTS)}
    write_new(RUN/'resumen.json',json.dumps(summary,indent=2)+'\n')
    write_new(RUN/'git-final.txt',subprocess.check_output(['git','status','--short'],cwd=ROOT).decode())
    print('RESULTADO',json.dumps(summary),flush=True)
    return 0 if summary['passed']==14 else 1

if __name__=='__main__':
    try:sys.exit(main())
    except Exception as exc:
        if 'RUN' in globals():
            write_new(RUN/'bloqueo.json',json.dumps({'error_type':type(exc).__name__,'description':str(exc),'runtime_started':globals().get('SERVER') is not None},indent=2))
        print('QA_BLOQUEADO',type(exc).__name__,str(exc),flush=True)
        sys.exit(2)
