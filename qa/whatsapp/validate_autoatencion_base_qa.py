"""Validación explícita local: base NUEVA, JAR completo sin perfiles externos, sin red ni Meta."""
from pathlib import Path
import argparse,base64,hashlib,json,os,re,secrets,shutil,subprocess,time,uuid

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--sql',type=Path,required=True)
parser.add_argument('--reuse-result',type=Path)
args=parser.parse_args()
SQL=args.sql.resolve()
assert SQL.is_relative_to(ROOT/'qa/runtime/autoatencion')
DB=SQL.stem
assert re.fullmatch(r'wa_durable_[a-f0-9]{8,32}',DB)
ID=uuid.uuid4().hex[:10];NAME='wa-auto-base-'+ID
RUN=ROOT/'qa/runtime/autoatencion'/NAME;RUN.mkdir()
subprocess.run(['icacls',str(RUN),'/inheritance:r','/grant:r',os.environ['USERDOMAIN']+'\\'+os.environ['USERNAME']+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],capture_output=True,check=True)
SEC=RUN/'private';SEC.mkdir()
MYSQL='sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
JAVA_IMAGE='eclipse-temurin@sha256:dff6eb46527bd293a3c06bbbe9e64e8c9f91f01905b5abbdad31761c03059d2c'
created=[];result={'database':DB,'schema_applied':False,'validate_started':False,'no_external_network':True}

def docker(*args,stdin=None,check=True,timeout=120):
    r=subprocess.run(['docker','--context','desktop-linux',*args],input=stdin,capture_output=True,text=True,encoding='utf-8',timeout=timeout)
    if check and r.returncode:
        (SEC/'docker-error.log').write_text(r.stdout+r.stderr)
        raise RuntimeError('Docker '+args[0]+' fallo; detalle privado')
    return r

def sql(query):
    return docker('exec','-i',NAME+'-mysql','mysql','--defaults-extra-file=/tmp/root.cnf','--default-character-set=utf8mb4','-N','-B',stdin=query).stdout.strip()

try:
    # Copia exacta de Java; ningún perfil, credencial o migración se empaqueta automáticamente.
    source=ROOT/'dima-bug-backend';build=RUN/'build';(build/'src/main').mkdir(parents=True)
    shutil.copy2(source/'pom.xml',build/'pom.xml');shutil.copytree(source/'src/main/java',build/'src/main/java')
    resources=build/'src/main/resources';resources.mkdir()
    for folder in ['correo','META-INF']:
        if (source/'src/main/resources'/folder).exists():shutil.copytree(source/'src/main/resources'/folder,resources/folder)
    shutil.copy2(source/'src/main/resources/application-qa-whatsapp-meta.properties',resources/'application-qa-whatsapp-meta.properties')
    java=shutil.which('java');boot=next((Path.home()/'.m2/wrapper/dists').glob('apache-maven-3.9.16*/**/plexus-classworlds*.jar'));maven=boot.parent.parent
    command=[java,'-Dmaven.home='+str(maven),'-Dmaven.multiModuleProjectDirectory='+str(build),'-Dclassworlds.conf='+str(maven/'bin/m2.conf'),'-classpath',str(boot),'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-Dstyle.color=never','-Dmaven.test.skip=true','package']
    if args.reuse_result:
        previous=args.reuse_result.resolve();assert previous.is_relative_to(ROOT/'qa/runtime/autoatencion')
        prior=json.loads(previous.read_text());jar=Path(prior['jar'])
        assert jar.is_relative_to(ROOT/'qa/runtime/autoatencion')
        assert hashlib.sha256(jar.read_bytes()).hexdigest()==prior['jar_sha256']
        assert all(p.read_bytes()==(jar.parents[1]/p.relative_to(build)).read_bytes() for p in (build/'src/main').rglob('*') if p.is_file())
    else:
        with (RUN/'build.log').open('wb') as log:r=subprocess.run(command,cwd=build,stdout=log,stderr=subprocess.STDOUT,timeout=240)
        if r.returncode:raise RuntimeError('Compilacion del JAR completo fallo; detalle privado')
        jar=build/'target/backend-0.0.1-SNAPSHOT.jar'
    result['jar']=str(jar);result['jar_sha256']=hashlib.sha256(jar.read_bytes()).hexdigest()
    print('JAR_QA_COMPLETO_COMPILADO',flush=True)
    password=secrets.token_hex(24);app_password=secrets.token_hex(24)
    (SEC/'root').write_text(password)
    docker('volume','create','--label','dimabug.wa.auto-base='+ID,NAME+'-data')
    docker('run','-d','--pull','never','--name',NAME+'-mysql','--label','dimabug.wa.auto-base='+ID,'--network','none','--memory','512m','--memory-swap','512m','--cpus','0.5','--pids-limit','96','--mount',f'type=volume,source={NAME}-data,target=/var/lib/mysql','--mount',f'type=bind,source={SEC},target=/run/qa,readonly','-e','MYSQL_ROOT_PASSWORD_FILE=/run/qa/root',MYSQL,'--performance-schema=OFF','--innodb-buffer-pool-size=134217728','--innodb-redo-log-capacity=134217728','--max-connections=8','--mysqlx=OFF','--default-time-zone=+00:00','--skip-name-resolve')
    created.append(NAME+'-mysql')
    docker('exec','-i',NAME+'-mysql','sh','-c','umask 077; cat > /tmp/root.cnf',stdin='[client]\nuser=root\npassword='+password+'\n')
    for _ in range(180):
        r=docker('exec',NAME+'-mysql','mysql','--defaults-extra-file=/tmp/root.cnf','--protocol=TCP','--host=127.0.0.1','-Nse','SELECT 1',check=False)
        if r.returncode==0:break
        time.sleep(1)
    else:raise RuntimeError('MySQL QA no disponible')
    assert sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"+DB+"'")=='0'
    applied=sql(SQL.read_text(encoding='utf-8'))
    (SEC/'sql-application.log').write_text(applied)
    result['schema_applied']=True
    assert sql(f"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='{DB}' AND TABLE_NAME IN ('wa_conversacion','wa_evento','wa_respuesta')")=='3'
    ready=sql(f"USE `{DB}`; SELECT COUNT(*) FROM co_conocimiento c JOIN co_solucion s ON s.conocimiento_id=c.conocimiento_id WHERE c.conocimiento_estado='PUBLICADO' AND s.solucion_estado=1 AND s.solucion_autoservicio=1")
    assert ready=='1';result['public_solution_count']=1;result['whatsapp_tables']=3
    # Repetición SOLO del seed idempotente sobre esta base nueva.
    sql(f'USE `{DB}`;\n'+(ROOT/'qa/whatsapp/autoatencion-datos-qa.sql').read_text(encoding='utf-8'))
    assert sql(f'USE `{DB}`; SELECT COUNT(*) FROM co_conocimiento')=='1'
    assert sql(f'USE `{DB}`; SELECT COUNT(*) FROM co_solucion')=='1'
    result['seed_idempotent']=True
    sql(f"CREATE USER 'wa_validate'@'%' IDENTIFIED BY '{app_password}'; GRANT SELECT,INSERT,UPDATE,DELETE ON `{DB}`.* TO 'wa_validate'@'%';")
    print('SQL_APLICADO_Y_SEED_VALIDADO',flush=True)
    props={
        'spring.profiles.active':'qa-whatsapp-meta','server.address':'127.0.0.1','server.port':'8082','server.forward-headers-strategy':'none',
        'spring.datasource.url':f'jdbc:mysql://127.0.0.1:3306/{DB}?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC',
        'spring.datasource.username':'wa_validate','spring.datasource.password':app_password,'spring.datasource.hikari.maximum-pool-size':'2',
        'spring.jpa.hibernate.ddl-auto':'validate','spring.sql.init.mode':'never','spring.flyway.enabled':'false','spring.liquibase.enabled':'false',
        'spring.jpa.mapping-resources':'META-INF/whatsapp.orm.xml','spring.jpa.show-sql':'false','spring.mvc.log-request-details':'false',
        'jwt.secret':secrets.token_hex(48),'jwt.expiration':'1800000','spring.security.user.password':secrets.token_hex(24),
        'dimabug.whatsapp.qa.raiz':'/qa','dimabug.whatsapp.meta-qa.waba':'qa-waba-local','dimabug.whatsapp.meta-qa.numero':'qa-numero-local',
        'dimabug.whatsapp.meta-qa.participantes':'tel:100000000000001=qa-sintetico','dimabug.whatsapp.meta-qa.verify-token':secrets.token_hex(24),'dimabug.whatsapp.meta-qa.app-secret':secrets.token_hex(24),
        'dimabug.whatsapp.autoatencion.habilitada':'false','dimabug.whatsapp.autoatencion.ticket-actor-email':'soporte@qa.dimabug.test',
        'dimabug.whatsapp.durable.clave-cifrado':base64.b64encode(secrets.token_bytes(32)).decode(),'dimabug.whatsapp.durable.clave-hmac':base64.b64encode(secrets.token_bytes(32)).decode(),
        'dimabug.gmail.oauth-habilitado':'false','dimabug.gmail.envio-real-habilitado':'false','dimabug.auth.correo.habilitado':'false',
        'dimabug.automatizacion.modo':'DESHABILITADO','dimabug.automatizacion.seguimiento-habilitado':'false',
        'spring.autoconfigure.exclude':'org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration',
        'dimabug.storage.materiales-dir':'/tmp/materiales','dimabug.storage.tickets-dir':'/tmp/tickets',
        'logging.level.org.hibernate.orm.jdbc.bind':'OFF','logging.level.org.hibernate.orm.jdbc.extract':'OFF'}
    for flag in ['recepcion','procesamiento','medios','envio','envio-real','conexion-meta']:props['dimabug.whatsapp.meta-qa.'+flag]='false'
    (SEC/'application.properties').write_text('\n'.join(k+'='+v for k,v in props.items())+'\n')
    docker('run','-d','--pull','never','--name',NAME+'-spring','--label','dimabug.wa.auto-base='+ID,'--network','container:'+NAME+'-mysql','--memory','512m','--memory-swap','512m','--cpus','0.5','--pids-limit','96','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--user','10001:10001','--tmpfs','/tmp:rw,noexec,nosuid,size=64m','--mount',f'type=bind,source={jar},target=/qa/app.jar,readonly','--mount',f'type=bind,source={SEC}/application.properties,target=/qa/application.properties,readonly','--entrypoint','java',JAVA_IMAGE,'-Xms64m','-Xmx192m','-XX:MaxDirectMemorySize=48m','-XX:MaxMetaspaceSize=128m','-XX:ReservedCodeCacheSize=48m','-jar','/qa/app.jar','--spring.config.location=file:/qa/application.properties')
    created.append(NAME+'-spring')
    for _ in range(180):
        logs=docker('logs',NAME+'-spring',check=False);text=logs.stdout+logs.stderr
        if re.search(r'Started \S+ in ',text):result['validate_started']=True;break
        state=json.loads(docker('inspect',NAME+'-spring').stdout)[0]['State']
        if not state['Running']:break
        time.sleep(1)
    logs=docker('logs',NAME+'-spring',check=False);text=logs.stdout+logs.stderr
    (SEC/'spring.log').write_text(text)
    result['secrets_in_spring_log']=any(v in text for k,v in props.items() if any(s in k for s in ['password','secret','token','clave']))
    if not result['validate_started']:raise RuntimeError('Spring Boot no completo el arranque; revisar detalle privado')
    if result['secrets_in_spring_log']:raise RuntimeError('Registro privado requiere revision de secretos')
    result['status']='PASS'
except Exception as e:
    result.update(status='FAIL',error=type(e).__name__+': '+str(e))
finally:
    result['final_states']={}
    for name in reversed(created):
        try:
            o=json.loads(docker('inspect',name).stdout)[0]
            assert o['Config']['Labels'].get('dimabug.wa.auto-base')==ID
            if o['State']['Running']:docker('stop','-t','30',name)
            result['final_states'][name]=json.loads(docker('inspect',name).stdout)[0]['State']['Status']
        except Exception:result['final_states'][name]='REVISAR'
    result['services_stopped']=bool(created) and all(s=='exited' for s in result['final_states'].values())
    if not result['services_stopped']:result['status']='FAIL'
    (RUN/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'runtime':str(RUN),'status':result.get('status'),'schema_applied':result['schema_applied'],'validate_started':result['validate_started'],'services_stopped':result['services_stopped'],'error':result.get('error')}),flush=True)
raise SystemExit(0 if result.get('status')=='PASS' else 1)
