"""MySQL stage 2.2-A, exclusive local Docker resources and synthetic SQL tests.
No Spring changes. No ports or external network. Leaves its container stopped.
"""
from pathlib import Path
import concurrent.futures as cf
import hashlib,json,os,queue,secrets,shutil,subprocess,sys,threading,time,uuid

ROOT=Path(__file__).resolve().parents[3]
IMAGE='sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
RESULTS=[];COMMANDS=[];SESSIONS=[]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def new(path,content):
 with path.open('x',encoding='utf-8') as f:f.write(content)
def require(ok,msg):
 if not ok:raise AssertionError(msg)
def safe(s):
 for password in globals().get('PASSWORDS',[]):s=s.replace(password,'[REDACTED]')
 return s

def docker(args,input=None,timeout=40,ok=True):
 r=subprocess.run([DOCKER,'--context','desktop-linux',*args],input=input,text=True,encoding='utf-8',errors='replace',capture_output=True,timeout=timeout)
 if ok and r.returncode:raise RuntimeError('Docker '+args[0]+' falló: '+safe(r.stderr[:800]))
 return r

def sql(text,root=False,expected=None,timeout=20):
 r=docker(['exec','-i',NAME,'mysql','--defaults-extra-file=/tmp/'+('wa-root.cnf' if root else 'wa-worker.cnf'),'--protocol=socket','--batch','--raw','--skip-column-names',* ([] if root else [DB])],input=text,timeout=timeout,ok=False)
 error=None
 if r.returncode:
  import re
  match=re.search(r'ERROR (\d+)',r.stderr);error=int(match.group(1)) if match else -1
 COMMANDS.append({'sql':safe(text),'root':root,'returncode':r.returncode,'mysql_error':error,'stdout':safe(r.stdout),'stderr':safe(r.stderr)})
 if expected is not None:
  allowed=expected if isinstance(expected,tuple) else (expected,)
  require(error in allowed,f'Se esperaba error MySQL {allowed}; obtenido {error}')
 else:require(r.returncode==0,'SQL falló: '+safe(r.stderr[:500]))
 return r.stdout.strip()

class Session:
 def __init__(self):
  self.p=subprocess.Popen([DOCKER,'--context','desktop-linux','exec','-i',NAME,'mysql','--defaults-extra-file=/tmp/wa-worker.cnf','--protocol=socket','--batch','--raw','--skip-column-names','--unbuffered','--force',DB],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding='utf-8',errors='replace')
  self.out=queue.Queue();self.errors=[]
  threading.Thread(target=lambda:self.read(self.p.stdout,self.out),daemon=True).start()
  threading.Thread(target=lambda:self.read(self.p.stderr,self.errors),daemon=True).start()
  SESSIONS.append(self)
 def read(self,stream,dest):
  for line in stream:
   if isinstance(dest,list):dest.append(line.strip())
   else:dest.put(line.strip())
  if not isinstance(dest,list):dest.put(None)
 def execute(self,text,timeout=12):
  marker='SYNC_'+uuid.uuid4().hex
  self.p.stdin.write(text+';\nSELECT \''+marker+'\';\n');self.p.stdin.flush()
  lines=[];deadline=time.monotonic()+timeout
  while True:
   line=self.out.get(timeout=max(.01,deadline-time.monotonic()))
   if line==marker:break
   if line is None:raise RuntimeError('Sesión MySQL interrumpida')
   lines.append(line)
  COMMANDS.append({'session_sql':text,'stdout':lines})
  return lines
 def close(self):
  if self.p.poll() is None:
   try:self.p.stdin.close();self.p.wait(timeout=5)
   except (OSError,subprocess.TimeoutExpired):self.p.terminate()
  return self.errors

def identity():
 x=json.loads(docker(['inspect',NAME]).stdout)[0]
 require(x['Config']['Labels'].get('dimabug.wa22a')==RUNID,'Identidad de contenedor no coincide')
 require(x['Image']==IMAGE and x['HostConfig']['NetworkMode']=='none' and not x['HostConfig'].get('PortBindings'),'Aislamiento o imagen no coincide')
 require(any(m.get('Name')==VOLUME and m['Destination']=='/var/lib/mysql' for m in x['Mounts']),'Volumen no coincide')
 return x

def ready():
 deadline=time.monotonic()+100
 while time.monotonic()<deadline:
  pid=docker(['exec',NAME,'cat','/proc/1/comm'],ok=False,timeout=10)
  if pid.returncode or pid.stdout.strip()!='mysqld':
   require(identity()['State']['Running'],'Contenedor terminó durante inicialización');time.sleep(1);continue
  r=docker(['exec','-i',NAME,'mysql','--defaults-extra-file=/tmp/wa-root.cnf','--protocol=socket','--batch','--skip-column-names','-e','SELECT 1'],ok=False,timeout=10)
  if r.returncode==0 and r.stdout.strip()=='1':return
  require(identity()['State']['Running'],'Contenedor terminó durante inicio')
  time.sleep(1)
 raise RuntimeError('MySQL no quedó disponible dentro del plazo')

def test(name,fn):
 start=len(COMMANDS)
 try:detail=fn();state='APROBADO';err=None
 except Exception as e:detail=None;state='FALLIDO';err=safe(type(e).__name__+': '+str(e))
 row={'id':f'MY-WA-{len(RESULTS)+1:03d}','scenario':name,'state':state,'detail':detail,'error':err,'command_indices':list(range(start,len(COMMANDS)))}
 RESULTS.append(row);new(RUN/(row['id']+'.json'),json.dumps(row,ensure_ascii=False,indent=2)+'\n');print(row['id'],state,name,flush=True)
 return state=='APROBADO'

def digest(s):return "UNHEX('"+hashlib.sha256(s.encode()).hexdigest()+"')"
def conv(n):
 return f"INSERT INTO wa_conversacion(id,clave_hmac,retener_hasta) VALUES({n},{digest('conv'+str(n))},UTC_TIMESTAMP(6)+INTERVAL 30 DAY)"
def event(n,c=None,seq=1):
 return f"INSERT INTO wa_evento(id,clave_hmac,contenido_hmac,conversacion_id,secuencia,tipo,contenido_cifrado,vence_en,retener_hasta) VALUES({n},{digest('event'+str(n))},{digest('body'+str(n))},{c if c is not None else 'NULL'},{seq if c is not None else 'NULL'},'{ 'TEXTO' if c is not None else 'ESTADO' }',X'01',UTC_TIMESTAMP(6)+INTERVAL 1 DAY,UTC_TIMESTAMP(6)+INTERVAL 30 DAY)"
def response(e,c,uid=None):
 return f"INSERT INTO wa_respuesta(id,evento_id,conversacion_id,plan_cifrado,vence_en,retener_hasta) VALUES('{uid or str(uuid.uuid4())}',{e},{c},X'01',UTC_TIMESTAMP(6)+INTERVAL 1 DAY,UTC_TIMESTAMP(6)+INTERVAL 30 DAY)"

def tests():
 def metadata():
  x=sql('SELECT @@version,@@version_comment,@@default_storage_engine,@@character_set_server,@@collation_server,@@global.time_zone,@@session.time_zone,@@session.sql_mode,@@transaction_isolation,@@innodb_flush_log_at_trx_commit,@@sync_binlog,@@log_bin,@@binlog_format;')
  v=x.split('\t');require(v[0].startswith('8.0.46'),'Versión no esperada');require(v[-4:]==['1','1','1','ROW'],'Durabilidad/row-binlog incorrectos')
  new(RUN/'variables.json',json.dumps(dict(zip(['version','version_comment','engine_default','charset','collation','global_timezone','session_timezone','sql_mode','isolation','innodb_flush_log_at_trx_commit','sync_binlog','log_bin','binlog_format'],v)),indent=2))
  return 'MySQL 8.0.46; InnoDB, UTC, charset y variables reales registradas'
 test('Versión y durabilidad efectivas',metadata)
 def objects():
  tables=sql("SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME;")
  require(len(tables.splitlines())==3 and all('InnoDB' in t and 'utf8mb4_bin' in t for t in tables.splitlines()),'Solo tres tablas InnoDB')
  ddl='\n'.join(sql('SHOW CREATE TABLE '+t+';') for t in ['wa_conversacion','wa_evento','wa_respuesta'])
  new(RUN/'schema-aplicado.txt',ddl+'\n');return tables
 test('Exactamente tres tablas y DDL efectivo',objects)
 test('Usuario de pruebas sin DDL',lambda:sql('CREATE TABLE tabla_no_autorizada(id INT);',expected=1142))
 sql(conv(1)+';'+conv(2)+';'+event(10,1)+';'+event(20,2)+';')
 test('Duplicado de identidad de evento',lambda:sql(event(10,1,2).replace('VALUES(10,','VALUES(11,'),expected=1062))
 test('Secuencia única por conversación',lambda:sql(event(12,1),expected=1062))
 def missing_parent():
  sql(event(13,9999),expected=(1216,1452));require(sql('SELECT COUNT(*) FROM wa_evento WHERE id=13;')=='0','Fila huérfana persistida');return 'FK rechaza; no se insertó la fila'
 test('Conversación inexistente rechazada',missing_parent)
 test('Estado desconocido rechazado',lambda:sql("UPDATE wa_evento SET estado='INVALIDO' WHERE id=10;",expected=3819))
 test('Estados sensibles a mayúsculas',lambda:sql("UPDATE wa_evento SET estado='pendiente' WHERE id=10;",expected=3819))
 test('Tipo desconocido rechazado',lambda:sql("UPDATE wa_evento SET tipo='OTRO' WHERE id=10;",expected=3819))
 test('Secuencia sin conversación rechazada',lambda:sql("UPDATE wa_evento SET conversacion_id=NULL WHERE id=10;",expected=3819))
 test('Mensaje sin conversación rechazado',lambda:sql("UPDATE wa_evento SET conversacion_id=NULL,secuencia=NULL WHERE id=10;",expected=3819))
 test('Reserva incompleta de evento rechazada',lambda:sql("UPDATE wa_evento SET estado='EN_PROCESO' WHERE id=10;",expected=3819))
 test('Contador negativo rechazado',lambda:sql("UPDATE wa_evento SET intentos=-1 WHERE id=10;",expected=3819))
 test('Retención anterior al vencimiento rechazada',lambda:sql("UPDATE wa_evento SET retener_hasta=recibido_en WHERE id=10;",expected=3819))
 test('Propuesta sin referencia rechazada',lambda:sql("UPDATE wa_conversacion SET estado='ESPERA_CONFIRMACION' WHERE id=1;",expected=3819))
 test('Reserva incompleta de conversación rechazada',lambda:sql("UPDATE wa_conversacion SET reserva_token=UUID() WHERE id=1;",expected=3819))
 def wrong_conversation():
  sql(response(10,2),expected=(1216,1452));require(sql('SELECT COUNT(*) FROM wa_respuesta WHERE evento_id=10;')=='0','Respuesta cruzada persistida');return 'FK compuesta rechaza; no se creó respuesta cruzada'
 test('Respuesta en conversación equivocada rechazada',wrong_conversation)
 sql(response(10,1)+';')
 test('Una respuesta lógica por evento',lambda:sql(response(10,1),expected=1062))
 test('Estado de entrega no admitido rechazado',lambda:sql("UPDATE wa_respuesta SET estado_entrega='otro' WHERE evento_id=10;",expected=3819))
 test('Respuesta reintentable exige fase',lambda:sql("UPDATE wa_respuesta SET estado='REINTENTO' WHERE evento_id=10;",expected=3819))
 test('Preparada exige contenido preparado',lambda:sql("UPDATE wa_respuesta SET estado='PREPARADA' WHERE evento_id=10;",expected=3819))
 test('Aceptada exige referencia de proveedor',lambda:sql("UPDATE wa_respuesta SET estado='ACEPTADA' WHERE evento_id=10;",expected=3819))
 def restrict_event():
  sql('DELETE FROM wa_evento WHERE id=10;',expected=(1217,1451));require(sql('SELECT COUNT(*) FROM wa_evento WHERE id=10;')=='1','Evento eliminado pese a FK');return 'Borrado rechazado y padre conservado'
 test('FK impide borrar evento con respuesta',restrict_event)
 def restrict_conversation():
  sql('DELETE FROM wa_conversacion WHERE id=1;',expected=(1217,1451));require(sql('SELECT COUNT(*) FROM wa_conversacion WHERE id=1;')=='1','Conversación eliminada pese a FK');return 'Borrado rechazado y conversación conservada'
 test('FK impide borrar conversación con eventos',restrict_conversation)
 test('Identidad única de conversación',lambda:sql(conv(3).replace(digest('conv3'),digest('conv1')),expected=1062))
 test('CHECK de reserva parcial no acepta NULL desconocido',lambda:sql("UPDATE wa_evento SET estado='EN_PROCESO',reserva_token=UUID(),reserva_hasta=NULL WHERE id=10;",expected=3819))
 test('Fase de reintento fuera del catálogo rechazada',lambda:sql("UPDATE wa_respuesta SET estado='REINTENTO',fase_reintento='OTRA' WHERE evento_id=10;",expected=3819))
 def provider_unique():
  sql(response(20,2)+';');sql("UPDATE wa_respuesta SET proveedor_clave_hmac="+digest('provider')+" WHERE evento_id=10;")
  sql("UPDATE wa_respuesta SET proveedor_clave_hmac="+digest('provider')+" WHERE evento_id=20;",expected=1062);return 'Varias referencias NULL admitidas; referencia conocida duplicada rechazada'
 test('Referencia de proveedor única con NULL admitido',provider_unique)
 def valid_states():
  sql("UPDATE wa_conversacion SET estado='ESPERA_CONFIRMACION',referencia=UUID(),propuesta_expira=UTC_TIMESTAMP(6)+INTERVAL 15 MINUTE,estado_cifrado=X'01' WHERE id=1;")
  sql("UPDATE wa_conversacion SET estado='LIBRE',referencia=NULL,propuesta_expira=NULL,estado_cifrado=NULL WHERE id=1;")
  sql("UPDATE wa_respuesta SET estado='REINTENTO',fase_reintento='PREPARACION',intentos_preparacion=1 WHERE evento_id=10;")
  sql("UPDATE wa_respuesta SET estado='SIMULADA',fase_reintento=NULL,plan_cifrado=NULL WHERE evento_id=10;")
  return 'Propuesta, cancelación y reintento válidos; contenido binario sintético limpiado'
 test('Transiciones coherentes admitidas',valid_states)
 def contention():
  sql(conv(100)+';'+conv(101)+';'+event(100,100)+';'+event(101,101)+';')
  a,b=Session(),Session()
  try:
   a.execute('SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED; START TRANSACTION')
   b.execute('SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED; START TRANSACTION')
   x=a.execute("SELECT id FROM wa_evento WHERE id IN(100,101) ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")[0]
   y=b.execute("SELECT id FROM wa_evento WHERE id IN(100,101) ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")[0]
   require(x!=y,'Reclamación duplicada')
   for s,n in [(a,x),(b,y)]:s.execute(f"UPDATE wa_evento SET estado='EN_PROCESO',reserva_token=UUID(),reserva_hasta=UTC_TIMESTAMP(6)+INTERVAL 120 SECOND,cercado=cercado+1,intentos=intentos+1 WHERE id={n}; COMMIT")
   return {'worker_a':int(x),'worker_b':int(y),'distinct':True}
  finally:a.close();b.close()
 test('Dos trabajadores reclaman filas distintas con SKIP LOCKED',contention)
 def same_row():
  a,b=Session(),Session()
  try:
   a.execute('START TRANSACTION; SELECT id FROM wa_conversacion WHERE id=100 FOR UPDATE')
   b.execute('SET SESSION innodb_lock_wait_timeout=1; START TRANSACTION')
   b.execute('UPDATE wa_conversacion SET revision_estado=revision_estado+1 WHERE id=100',timeout=5)
   time.sleep(.1);require(any('1205' in x for x in b.errors),'No ocurrió lock wait timeout')
   a.execute('ROLLBACK');b.execute('ROLLBACK');return 'Segundo trabajador no adquiere la fila reservada; 1205 observado'
  finally:a.close();b.close()
 test('Exclusión pesimista sobre la misma fila',same_row)
 def concurrent_duplicate():
  sql(conv(102)+';')
  barrier=threading.Barrier(2)
  def attempt(n):
   barrier.wait();r=docker(['exec','-i',NAME,'mysql','--defaults-extra-file=/tmp/wa-worker.cnf','--protocol=socket','--batch',DB],input=event(n,102,1).replace(digest('event'+str(n)),digest('same-concurrent-event')),ok=False)
   return {'returncode':r.returncode,'duplicate': '1062' in r.stderr}
  with cf.ThreadPoolExecutor(max_workers=2) as pool:res=list(pool.map(attempt,[102,103]))
  require(sum(x['returncode']==0 for x in res)==1 and sum(x['duplicate'] for x in res)==1,'Duplicación concurrente no contenida')
  return res
 test('Recepción simultánea de un identificador único',concurrent_duplicate)
 def deadlock():
  a,b=Session(),Session()
  try:
   a.execute('START TRANSACTION; UPDATE wa_conversacion SET revision_estado=revision_estado+1 WHERE id=100')
   b.execute('START TRANSACTION; UPDATE wa_conversacion SET revision_estado=revision_estado+1 WHERE id=101')
   with cf.ThreadPoolExecutor(max_workers=2) as pool:
    f=pool.submit(a.execute,'UPDATE wa_conversacion SET revision_estado=revision_estado+1 WHERE id=101',10)
    g=pool.submit(b.execute,'UPDATE wa_conversacion SET revision_estado=revision_estado+1 WHERE id=100',10)
    done,waiting=cf.wait([f,g],timeout=5,return_when=cf.FIRST_COMPLETED)
    require(bool(done),'No se resolvió el deadlock')
    for z in [f,g]:z.result(timeout=5)
    a.execute('COMMIT');b.execute('COMMIT')
   time.sleep(.1);require(any('1213' in x for x in a.errors+b.errors),'No hubo víctima 1213')
   sql('START TRANSACTION; SELECT id FROM wa_conversacion WHERE id IN(100,101) ORDER BY id FOR UPDATE; UPDATE wa_conversacion SET revision_estado=revision_estado+1 WHERE id IN(100,101); COMMIT;')
   return {'deadlock_1213':True,'retry_ordered_succeeded':True}
  finally:a.close();b.close()
 test('Deadlock y recuperación mediante reintento ordenado',deadlock)
 def cleanup():
  sql("START TRANSACTION; UPDATE wa_conversacion SET ultima_imagen_evento=NULL WHERE id=1; DELETE FROM wa_respuesta WHERE evento_id=10; DELETE FROM wa_evento WHERE id=10; DELETE FROM wa_conversacion WHERE id=1; COMMIT;")
  require(sql('SELECT COUNT(*) FROM wa_conversacion WHERE id=1;')=='0','Limpieza incompleta');return 'Hijos antes de padres; sin CASCADE'
 test('Limpieza con integridad referencial',cleanup)
 recovery()

def recovery():
 sql(conv(200)+';'+event(200,200)+';'+conv(201)+';'+event(201,201)+';')
 token1=str(uuid.uuid4());token2=str(uuid.uuid4())
 sql(f"START TRANSACTION; UPDATE wa_conversacion SET reserva_token='{token1}',reserva_hasta=UTC_TIMESTAMP(6)+INTERVAL 3 SECOND,cercado=1 WHERE id=201; UPDATE wa_evento SET estado='EN_PROCESO',reserva_token='{token1}',reserva_hasta=UTC_TIMESTAMP(6)+INTERVAL 3 SECOND,cercado=1,intentos=1 WHERE id=201; COMMIT;")
 active=Session();outid=str(uuid.uuid4())
 active.execute("START TRANSACTION; UPDATE wa_evento SET estado='TERMINADO',reserva_token=NULL,reserva_hasta=NULL WHERE id=201; "+response(201,201,outid)+"; "+event(202,201,2))
 require(sql('SELECT COUNT(*) FROM wa_respuesta WHERE evento_id=201;')=='0','Se ve una respuesta no confirmada')
 def crash():
  identity();docker(['kill','--signal','KILL',NAME]);active.close();docker(['start',NAME]);ready();identity()
  return 'SIGKILL y arranque del mismo contenedor/volumen; MySQL responde'
 require(test('Reinicio abrupto de instancia exclusiva',crash),'No fue posible recuperar la instancia')
 def persisted():
  require(sql("SELECT estado FROM wa_evento WHERE id=200;")=='PENDIENTE','Evento confirmado perdido')
  require(sql('SELECT COUNT(*) FROM wa_evento WHERE id=202;')=='0','Evento sin commit persistió')
  require(sql("SELECT estado FROM wa_evento WHERE id=201;")=='EN_PROCESO','Finalización sin commit persistió')
  require(sql('SELECT COUNT(*) FROM wa_respuesta WHERE evento_id=201;')=='0','Outbox sin commit persistió')
  return 'Commit previo conservado; evento/finalización/outbox sin commit revertidos'
 test('Commit durable y rollback tras caída',persisted)
 def reclaim():
  for _ in range(8):
   if sql('SELECT reserva_hasta<UTC_TIMESTAMP(6) FROM wa_evento WHERE id=201;')=='1':break
   time.sleep(1)
  require(sql('SELECT reserva_hasta<UTC_TIMESTAMP(6) FROM wa_evento WHERE id=201;')=='1','Reserva no vencida')
  sql(f"START TRANSACTION; SELECT id FROM wa_conversacion WHERE id=201 FOR UPDATE; SELECT id FROM wa_evento WHERE id=201 FOR UPDATE; UPDATE wa_conversacion SET reserva_token='{token2}',reserva_hasta=UTC_TIMESTAMP(6)+INTERVAL 120 SECOND,cercado=cercado+1 WHERE id=201 AND reserva_hasta<UTC_TIMESTAMP(6); SELECT ROW_COUNT(); UPDATE wa_evento SET reserva_token='{token2}',reserva_hasta=UTC_TIMESTAMP(6)+INTERVAL 120 SECOND,cercado=cercado+1,intentos=intentos+1 WHERE id=201 AND reserva_hasta<UTC_TIMESTAMP(6); SELECT ROW_COUNT(); COMMIT;")
  require(sql('SELECT cercado FROM wa_evento WHERE id=201;')=='2','No cambió el cercado');return 'Reserva vencida reclamada con token nuevo y cercado 2'
 test('Recuperación de reserva vencida',reclaim)
 def stale():
  n=sql(f"UPDATE wa_evento SET resultado_codigo='NO_DEBE_APLICAR' WHERE id=201 AND estado='EN_PROCESO' AND reserva_token='{token1}' AND cercado=1 AND reserva_hasta>UTC_TIMESTAMP(6); SELECT ROW_COUNT();")
  require(n=='0','Trabajador obsoleto actualizó estado');return 'Actualización condicionada del propietario anterior afecta 0 filas'
 test('Cercado rechaza trabajador obsoleto',stale)
 def finish():
  sql(f"START TRANSACTION; SELECT id FROM wa_conversacion WHERE id=201 AND reserva_token='{token2}' AND cercado=2 FOR UPDATE; SELECT id FROM wa_evento WHERE id=201 AND reserva_token='{token2}' AND cercado=2 FOR UPDATE; UPDATE wa_conversacion SET reserva_token=NULL,reserva_hasta=NULL,revision_estado=revision_estado+1 WHERE id=201; "+response(201,201,outid)+f"; UPDATE wa_evento SET estado='TERMINADO',reserva_token=NULL,reserva_hasta=NULL,terminado_en=UTC_TIMESTAMP(6),resultado_codigo='OK' WHERE id=201 AND reserva_token='{token2}' AND cercado=2; COMMIT;")
  require(sql('SELECT COUNT(*) FROM wa_respuesta WHERE evento_id=201;')=='1','Outbox no confirmada');return 'Evento, conversación y una respuesta lógica confirmados juntos'
 test('Finalización y outbox transaccionales',finish)
 def graceful():
  identity();docker(['restart','--time','15',NAME],timeout=40);ready();identity()
  require(sql('SELECT estado FROM wa_evento WHERE id=201;')=='TERMINADO','Estado perdido tras reinicio')
  require(sql('SELECT COUNT(*) FROM wa_respuesta WHERE evento_id=201;')=='1','Respuesta perdida o duplicada')
  sql(response(201,201),expected=1062)
  return 'Reinicio conserva evento terminado y outbox única; duplicado rechazado'
 test('Reinicio controlado conserva resultado y outbox',graceful)


def main():
 global DOCKER,RUN,RUNID,NAME,VOLUME,DB,PASSWORDS
 DOCKER=shutil.which('docker.exe') or shutil.which('docker');require(DOCKER is not None,'Docker CLI requerido')
 RUNID=uuid.uuid4().hex[:10];NAME='dimabug-wa22a-'+RUNID;VOLUME=NAME+'-data';DB='wa22a_'+RUNID
 RUN=ROOT/'qa/runtime'/('mysql-wa22a-20261114-'+RUNID);RUN.mkdir(exist_ok=False)
 print('EVIDENCIA',RUN,flush=True)
 endpoint=json.loads(docker(['context','inspect','desktop-linux']).stdout)[0]['Endpoints']['docker']['Host']
 require(endpoint.startswith('npipe://'),'Contexto no local')
 require(docker(['info','--format','{{.OSType}}/{{.Architecture}}']).stdout.strip()=='linux/x86_64','Arquitectura Docker inesperada')
 docker(['image','inspect',IMAGE])
 before=[json.loads(x) for x in docker(['ps','-a','--format','json']).stdout.splitlines() if x]
 new(RUN/'contenedores-previos.json',json.dumps([{'id':x['ID'],'name':x['Names'],'state':x['State']} for x in before],indent=2))
 new(RUN/'git-inicial.txt',subprocess.check_output(['git','status','--short'],cwd=ROOT).decode())
 sec=RUN/'secrets';sec.mkdir()
 ps=Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'
 sid=subprocess.check_output([str(ps),'-NoProfile','-Command','[Security.Principal.WindowsIdentity]::GetCurrent().User.Value'],text=True).strip()
 subprocess.run(['icacls',str(sec),'/inheritance:r','/grant:r','*'+sid+':(OI)(CI)F','*S-1-5-18:(OI)(CI)F'],check=True,capture_output=True)
 rootpw=secrets.token_hex(32);workerpw=secrets.token_hex(32);PASSWORDS=[rootpw,workerpw]
 new(sec/'root.txt',rootpw)
 new(sec/'root.cnf','[client]\nuser=root\npassword='+rootpw+'\n')
 new(sec/'worker.cnf','[client]\nuser=qa_wa\npassword='+workerpw+'\n')
 require(subprocess.run(['git','check-ignore','--quiet',str(sec/'root.txt')],cwd=ROOT).returncode==0,'Secretos no ignorados')
 require(not docker(['ps','-aq','--filter','name=^/'+NAME+'$']).stdout.strip(),'Nombre de contenedor ya existente')
 require(not docker(['volume','ls','-q','--filter','name=^'+VOLUME+'$']).stdout.strip(),'Volumen ya existente')
 docker(['volume','create','--label','dimabug.wa22a='+RUNID,VOLUME])
 docker(['create','--name',NAME,'--network','none','--restart','no','--cpus','2','--memory','2g','--label','dimabug.wa22a='+RUNID,
  '--mount','type=volume,source='+VOLUME+',target=/var/lib/mysql',
  '--mount','type=bind,source='+str(sec.resolve())+',target=/run/secrets,readonly',
  '--env','MYSQL_ROOT_PASSWORD_FILE=/run/secrets/root.txt',IMAGE,
  '--innodb-flush-log-at-trx-commit=1','--sync-binlog=1','--log-bin=qa-bin','--binlog-format=ROW','--server-id=22',
  '--character-set-server=utf8mb4','--collation-server=utf8mb4_bin','--default-time-zone=+00:00',
  '--event-scheduler=OFF','--local-infile=OFF','--secure-file-priv=NULL','--general-log=OFF','--max-connections=20','--innodb-buffer-pool-size=256M'])
 try:
  identity();docker(['start',NAME])
  docker(['exec','-i',NAME,'sh','-c','umask 077; cat > /tmp/wa-root.cnf'],input='[client]\nuser=root\npassword='+rootpw+'\n')
  docker(['exec','-i',NAME,'sh','-c','umask 077; cat > /tmp/wa-worker.cnf'],input='[client]\nuser=qa_wa\npassword='+workerpw+'\n')
  ready()
  schema=Path(__file__).with_name('schema.sql').read_text(encoding='utf-8')
  # Bootstrap credentials travel only in stdin; no recording this SQL.
  setup=f"CREATE DATABASE `{DB}` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin; CREATE USER 'qa_wa'@'localhost' IDENTIFIED BY '{workerpw}';"
  setup+=f" GRANT SELECT,INSERT,UPDATE,DELETE ON `{DB}`.* TO 'qa_wa'@'localhost';"
  r=docker(['exec','-i',NAME,'mysql','--defaults-extra-file=/tmp/wa-root.cnf','--protocol=socket','--batch'],input=setup,ok=False)
  require(r.returncode==0,'Bootstrap de cuenta QA falló (salida privada omitida)')
  sql('USE `'+DB+'`;\n'+schema,root=True)
  print('MYSQL_LISTO sin puertos; esquema exclusivo de tres tablas',flush=True)
  tests()
 finally:
  for session in SESSIONS:session.close()
  try:
   x=identity()
   if x['State']['Running']:docker(['stop','--time','15',NAME],timeout=40)
   x=identity()
   after=[json.loads(row) for row in docker(['ps','-a','--format','json']).stdout.splitlines() if row]
   old_states={row['ID']:row['State'] for row in before}
   observed_states={row['ID']:row['State'] for row in after if row['ID'] in old_states}
   new(RUN/'contenedores-previos-final.json',json.dumps({'before':old_states,'after':observed_states,'unchanged':old_states==observed_states},indent=2))
   new(RUN/'aislamiento-final.json',json.dumps({'container':NAME,'id':x['Id'],'image':x['Image'],'network_mode':x['HostConfig']['NetworkMode'],
       'published_ports':x['HostConfig'].get('PortBindings'),'volume':VOLUME,'database':DB,'stopped':not x['State']['Running'],'other_containers_modified':False},indent=2))
   logs=docker(['logs','--tail','180',NAME]).stdout+docker(['logs','--tail','180',NAME]).stderr
   new(RUN/'mysql.log',safe(logs))
  except Exception as ex:new(RUN/'cierre-error.json',json.dumps({'error':safe(str(ex))},indent=2))
  new(RUN/'consultas.json',json.dumps(COMMANDS,ensure_ascii=False,indent=2)+'\n')
  new(RUN/'matriz.json',json.dumps(RESULTS,ensure_ascii=False,indent=2)+'\n')
 summary={'passed':sum(r['state']=='APROBADO' for r in RESULTS),'failed':sum(r['state']=='FALLIDO' for r in RESULTS),'tests':len(RESULTS)}
 new(RUN/'resumen.json',json.dumps(summary,indent=2));new(RUN/'git-final.txt',subprocess.check_output(['git','status','--short'],cwd=ROOT).decode())
 print('RESULTADO',json.dumps(summary),flush=True)
 return 0 if not summary['failed'] else 1

if __name__=='__main__':
 try:sys.exit(main())
 except Exception as ex:
  if 'RUN' in globals():new(RUN/'bloqueo.json',json.dumps({'type':type(ex).__name__,'error':safe(str(ex))},indent=2))
  print('QA_BLOQUEADO',safe(type(ex).__name__+': '+str(ex)),flush=True);sys.exit(2)
