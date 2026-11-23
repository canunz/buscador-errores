"""Piloto autorizado futuro: 10 min referencia + 10 min QA + recuperación solo QA. Sin Meta."""
import argparse,hashlib,hmac,http.client,json,math,os,subprocess,time,uuid
from pathlib import Path

QA='dimabug-wa-pilot.service';GRAPH='dimabug-wa-pilot-graph.service'
EXISTING=['dimabug@staging.service','mysql.service','nginx.service']
CONF=Path('/etc/dimabug-wa-pilot');BASEURL='/api/publico/soluciones?q=qa-piloto'
def progress(phase,**values):
    path=Path(__file__).resolve().parent/'pilot-status.json'
    data={'phase':phase,'status':'RUNNING',**values}
    path.write_text(json.dumps(data,indent=2)+'\n');path.chmod(0o644)
def command(args,check=True):
    r=subprocess.run(args,capture_output=True,text=True,timeout=45)
    if check and r.returncode:raise RuntimeError('COMMAND_FAILED: '+args[0])
    return r.stdout.strip()
def unit(name,key):return command(['systemctl','show',name,'-p',key,'--value'])
def identity():return {n:(unit(n,'MainPID'),unit(n,'NRestarts')) for n in EXISTING}
def mysql(query):
    return command_mysql(query)
def command_mysql(query):
    tls=json.loads((CONF/'tls-public.json').read_text());bundle=Path(tls['bundle'])
    args=['java','-Xmx32m','-Dqa.tls.storeUri=file:'+(CONF/'mysql-truststore.p12').as_posix(),'-Dqa.tls.storeSha256='+tls['store_sha256'],
          '-cp',str(bundle/'tls')+':'+str(bundle/'lib/mysql-connector-j.jar'),'dimabug.qa.SharedMysqlTlsProbe','--query']
    r=subprocess.run(args,input=query,capture_output=True,text=True,encoding='utf-8',timeout=30)
    if r.returncode:raise RuntimeError('QA_MYSQL_QUERY_FAILED')
    return r.stdout.strip()
def sample():
    mem={k:int(v.split()[0]) for k,v in (l.split(':',1) for l in Path('/proc/meminfo').read_text().splitlines() if ':' in l)}
    vm=dict(l.split() for l in Path('/proc/vmstat').read_text().splitlines())
    start=time.monotonic();c=http.client.HTTPSConnection('staging.dimabug.casol.cl',timeout=5)
    try:
        c.request('GET',BASEURL);r=c.getresponse();status=r.status;r.read(512*1024)
    finally:c.close()
    if status!=200:raise RuntimeError('STAGING_HTTP_NOT_200')
    return {'available_mib':mem['MemAvailable']/1024,'swap_in':int(vm['pswpin']),'swap_out':int(vm['pswpout']),
            'staging_ms':(time.monotonic()-start)*1000,'mysql_bytes':int(unit('mysql.service','MemoryCurrent'))}
def p95(rows):
    values=sorted(r['staging_ms'] for r in rows);return values[math.ceil(len(values)*.95)-1]
def observe(seconds):
    rows=[];deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        rows.append(sample())
        if rows[-1]['available_mib']<1024:raise RuntimeError('MEMAVAILABLE_BELOW_1_GIB')
        if len(rows)>=4 and all(rows[i]['swap_in']>rows[i-1]['swap_in'] or rows[i]['swap_out']>rows[i-1]['swap_out'] for i in range(len(rows)-3,len(rows))):raise RuntimeError('SUSTAINED_SWAP_30_SECONDS')
        status=Path(__file__).resolve().parent/'pilot-status.json'
        if status.exists():
            data=json.loads(status.read_text());data.update(samples=len(rows),available_min_mib=round(min(x['available_mib'] for x in rows)))
            status.write_text(json.dumps(data,indent=2)+'\n');status.chmod(0o644)
        time.sleep(min(10,max(0,deadline-time.monotonic())))
    return rows
def http_get_ready():
    token=(CONF/'secrets/verify-token').read_text();c=http.client.HTTPConnection('127.0.0.1',8082,timeout=3)
    try:
        c.request('GET','/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token='+token+'&hub.challenge=000123')
        r=c.getresponse();return r.status==200 and r.read(1024)==b'000123'
    except (OSError,http.client.HTTPException):return False
    finally:c.close()
def wait_ready(previous_pid=None):
    start=time.monotonic()
    while time.monotonic()-start<=180:
        if (previous_pid is None or unit(QA,'MainPID') not in ('0',previous_pid)) and http_get_ready():return time.monotonic()-start
        time.sleep(2)
    raise RuntimeError('QA_READY_EXCEEDED_180_SECONDS')
def post(body):
    secret=(CONF/'secrets/app-secret').read_bytes()
    sign='sha256='+hmac.new(secret,body,hashlib.sha256).hexdigest()
    c=http.client.HTTPConnection('127.0.0.1',8082,timeout=15)
    try:
        c.request('POST','/webhooks/whatsapp',body,{'Content-Type':'application/json','X-Hub-Signature-256':sign})
        r=c.getresponse();data=r.read(8192)
        if r.status!=200:raise RuntimeError('QA_POST_NOT_200')
        return json.loads(data)
    finally:c.close()
def event(mid):
    return json.dumps({'object':'whatsapp_business_account','entry':[{'id':'qa-pilot-waba','changes':[{'field':'messages','value':{'messaging_product':'whatsapp','metadata':{'phone_number_id':'qa-pilot-phone'},'messages':[{'id':mid,'from':'100000000000001','timestamp':str(int(time.time())),'type':'text','text':{'body':'Hola'}}]}}]}]},separators=(',',':')).encode()
def statuses(db):
    return mysql(f"USE `{db}`; SELECT COUNT(*) FROM wa_evento; SELECT COUNT(*) FROM wa_evento WHERE estado='TERMINADO' AND resultado_codigo='SALUDO' AND ultimo_error_codigo IS NULL; SELECT COUNT(*) FROM wa_respuesta WHERE estado='SIMULADA'; SELECT COUNT(*) FROM wa_respuesta WHERE estado!='SIMULADA'; SELECT COUNT(*) FROM ticket;").splitlines()
def main():
    p=argparse.ArgumentParser();p.add_argument('--execute',action='store_true');p.add_argument('--approval');a=p.parse_args()
    if not a.execute:print('PLAN_ONLY: 10 min referencia, 20 eventos/duplicados, 10 min carga, una recuperacion QA, parada final');return
    if a.approval!='PILOTO_LOCAL_SIMULADO_Y_RECUPERACION_QA' or os.name!='posix' or os.geteuid()!=0:p.error('Requiere root y autorizacion expresa')
    for n in [QA,GRAPH]:
        if command(['systemctl','is-active',n],check=False)=='active':raise RuntimeError('QA_ALREADY_ACTIVE')
    props=dict(l.split('=',1) for l in (CONF/'application.properties').read_text().splitlines() if '=' in l)
    for k in ['envio-real','medios','conexion-meta']:
        if props.get('dimabug.whatsapp.meta-qa.'+k)!='false':raise RuntimeError('REAL_OR_MEDIA_NOT_ALLOWED')
    import re
    match=re.search(r':3306/(wa_durable_[a-f0-9]+)\?',props['spring.datasource.url'])
    if not match:raise RuntimeError('QA_DB_REQUIRED')
    db=match[1];before=identity()
    if statuses(db)!=['0','0','0','0','0']:raise RuntimeError('PILOT_REQUIRES_EMPTY_EVENT_TABLES')
    progress('REFERENCE_10_MIN')
    reference=observe(600);result={'status':'FAIL'};marker=CONF/'PILOT_APPROVED'
    try:
        # Watchdog independiente; no altera timers existentes y no habilita arranque permanente.
        command(['systemd-run','--unit=dimabug-wa-pilot-cutoff','--on-active=45m','/usr/bin/systemctl','stop',QA,GRAPH])
        marker.touch(mode=0o600,exist_ok=False)
        errors_before=mysql("SHOW GLOBAL STATUS LIKE 'Connection_errors_max_connections'")
        progress('STARTING_QA',reference_p95_ms=round(p95(reference),2))
        command(['systemctl','start',GRAPH,QA]);wait_ready()
        prefix='pilot-'+uuid.uuid4().hex;first=None
        for i in range(20):
            body=event(prefix+'-'+str(i));first=body if first is None else first
            if post(body).get('aceptados')!=1 or post(body).get('duplicados')!=1:raise RuntimeError('ACCEPTANCE_OR_DEDUP_FAILED')
            time.sleep(1)
        progress('PILOT_10_MIN',events_sent=20,duplicate_requests=20,reference_p95_ms=round(p95(reference),2))
        active=observe(600)
        if statuses(db)!=['20','20','20','0','0']:raise RuntimeError('PERSISTENCE_OR_SIMULATED_REPLY_FAILED')
        progress('RECOVERING_QA_ONLY')
        previous_pid=unit(QA,'MainPID')
        command(['systemctl','kill','--kill-whom=main','--signal=SIGKILL',QA])
        recovery=wait_ready(previous_pid)
        if post(first).get('duplicados')!=1 or statuses(db)!=['20','20','20','0','0']:raise RuntimeError('RESTART_PERSISTENCE_OR_DEDUP_FAILED')
        progress('POST_RECOVERY_2_MIN',recovery_seconds=round(recovery,2))
        active+=observe(120)
        if unit(QA,'NRestarts')!='1':raise RuntimeError('UNEXPECTED_QA_RESTARTS')
        if identity()!=before:raise RuntimeError('EXISTING_SERVICE_RESTART_OR_PID_CHANGED')
        if p95(active)>p95(reference)*1.2:raise RuntimeError('STAGING_P95_DEGRADATION_OVER_20_PERCENT')
        if max(x['mysql_bytes'] for x in active)>max(x['mysql_bytes'] for x in reference)+128*1024**2:raise RuntimeError('MYSQL_GROWTH_OVER_128_MIB')
        if mysql("SHOW GLOBAL STATUS LIKE 'Connection_errors_max_connections'")!=errors_before:raise RuntimeError('MYSQL_CONNECTION_SATURATION')
        result={'status':'PASS','available_min_mib':round(min(x['available_mib'] for x in active)),
                'staging_reference_p95_ms':round(p95(reference),2),'staging_pilot_p95_ms':round(p95(active),2),
                'recovery_seconds':round(recovery,2),'events':20,'simulated_replies':20,'real_send':False}
    except Exception as e:
        result['error_code']=str(e) if str(e).replace('_','').replace(' ','').isalnum() else type(e).__name__
        raise
    finally:
        command(['systemctl','stop',QA,GRAPH],check=False)
        marker.unlink(missing_ok=True)
        command(['systemctl','stop','dimabug-wa-pilot-cutoff.timer'],check=False)
        result['qa_stopped']=all(command(['systemctl','is-active',n],check=False)!='active' for n in [QA,GRAPH])
        progress('QA_STOPPED',**result)
        print(json.dumps(result))
    if result['status']!='PASS' or not result['qa_stopped']:raise RuntimeError('PILOT_NOT_APPROVED')

if __name__=='__main__':
    try:main()
    except Exception as error:
        path=Path(__file__).resolve().parent/'pilot-status.json'
        if path.exists():
            data=json.loads(path.read_text());data['status']='FAIL'
            text=str(error);data['error_code']=text if text.replace('_','').isalnum() else type(error).__name__
            path.write_text(json.dumps(data,indent=2)+'\n');path.chmod(0o644)
        raise
