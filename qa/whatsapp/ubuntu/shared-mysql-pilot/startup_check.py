"""Un intento QA autorizado, sin POST; rollback, cuenta bloqueada y sin modificar otros servicios."""
import argparse,http.client,json,os,re,signal,subprocess,time
from pathlib import Path
HERE=Path(__file__).resolve().parent;CONF=Path('/etc/dimabug-wa-pilot')
QA='dimabug-wa-pilot.service';GRAPH='dimabug-wa-pilot-graph.service'
DROP=Path('/run/systemd/system/dimabug-wa-pilot.service.d/90-startup-check.conf')
STATUS=HERE/'startup-status.json'
def command(args,check=True):
    r=subprocess.run(args,capture_output=True,text=True,timeout=60)
    if check and r.returncode:raise RuntimeError('COMMAND_FAILED_'+args[0].replace('/','_'))
    return r.stdout.strip()
def unit(name,key):return command(['systemctl','show',name,'-p',key,'--value'])
def mysql(q):
    r=subprocess.run(['mysql','--protocol=socket','--batch','--skip-column-names'],input=q,capture_output=True,text=True,timeout=15)
    if r.returncode:raise RuntimeError('QA_ACCOUNT_CONTROL_FAILED')
    return r.stdout.strip()
def timeout_config(text):
    pattern=r'(?m)^spring\.datasource\.hikari\.connection-timeout=(3000|30000)$'
    if len(re.findall(pattern,text))!=1:raise RuntimeError('UNEXPECTED_QA_TIMEOUT_CONFIG')
    return re.sub(pattern,'spring.datasource.hikari.connection-timeout=30000',text)
def runtime_override(text):
    entries=[l for l in text.splitlines() if l.startswith('ExecStart=')]
    if len(entries)!=1 or '/opt/dimabug-wa-pilot/whatsapp-qa.jar' not in entries[0]:raise RuntimeError('UNEXPECTED_QA_EXECSTART')
    args=' '.join('--dimabug.whatsapp.meta-qa.'+k+'=false' for k in ['recepcion','procesamiento','envio','envio-real','medios','conexion-meta'])
    return '[Service]\nRestart=no\nExecStart=\n'+entries[0]+' '+args+'\n'
def progress(data):
    STATUS.write_text(json.dumps(data,indent=2)+'\n');STATUS.chmod(0o644)
def ready():
    token=(CONF/'secrets/verify-token').read_text();c=http.client.HTTPConnection('127.0.0.1',8082,timeout=2)
    try:
        c.request('GET','/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token='+token+'&hub.challenge=000123')
        r=c.getresponse();return r.status==200 and r.read(1024)==b'000123'
    except (OSError,http.client.HTTPException):return False
    finally:c.close()
def available():
    line=next(l for l in Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:'))
    return int(line.split()[1])/1024
def identity():return {n:(unit(n,'MainPID'),unit(n,'NRestarts')) for n in ['mysql.service','nginx.service','dimabug@staging.service']}
def main():
    p=argparse.ArgumentParser();p.add_argument('--execute',action='store_true');p.add_argument('--approval');a=p.parse_args()
    if not a.execute:print('PLAN_ONLY: Hikari 30000ms, un arranque <=180s, sin eventos, parada y bloqueo final');return
    if a.approval!='AJUSTAR_TIMEOUT_Y_PROBAR_SOLO_ARRANQUE_QA' or os.name!='posix' or os.geteuid()!=0:p.error('Root y autorizacion expresa requeridos')
    for name in [QA,GRAPH]:
        if unit(name,'ActiveState') in ['active','activating','deactivating']:raise RuntimeError('QA_MUST_BE_STOPPED')
    if DROP.exists():raise RuntimeError('STARTUP_OVERRIDE_ALREADY_EXISTS')
    if mysql("SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1'")!='Y':raise RuntimeError('QA_ACCOUNT_MUST_START_LOCKED')
    before=identity();result={'status':'RUNNING','phase':'PREPARING','events_sent':0,'replies_enabled':False}
    marker=CONF/'PILOT_APPROVED';progress(result)
    signal.signal(signal.SIGTERM,lambda *_: (_ for _ in ()).throw(RuntimeError('STARTUP_CHECK_TERMINATED')))
    try:
        command(['systemd-run','--unit=dimabug-wa-startcheck-cutoff','--on-active=5m','/bin/sh',str(HERE/'rollback.sh'),'DETENER_Y_BLOQUEAR_SOLO_QA'])
        target=CONF/'application.properties';old=target.read_text();changed=timeout_config(old)
        backup=CONF/'application.before-startup-timeout.properties'
        if not backup.exists():backup.write_text(old);backup.chmod(0o600)
        target.write_text(changed);target.chmod(0o600)
        DROP.parent.mkdir(parents=True,exist_ok=True)
        DROP.write_text(runtime_override(Path('/etc/systemd/system/dimabug-wa-pilot.service').read_text()));DROP.chmod(0o644)
        command(['systemctl','daemon-reload']);command(['systemctl','reset-failed',QA],check=False)
        mysql("ALTER USER 'wa_qa_pilot'@'127.0.0.1' ACCOUNT UNLOCK;")
        marker.touch(mode=0o600,exist_ok=False)
        start=time.monotonic();command(['systemctl','start',QA])
        result.update(phase='STARTING',available_min_mib=available());progress(result)
        while time.monotonic()-start<=180:
            result['available_min_mib']=min(result['available_min_mib'],available());result['elapsed_seconds']=round(time.monotonic()-start,1);progress(result)
            if result['available_min_mib']<1024:raise RuntimeError('MEMAVAILABLE_BELOW_1_GIB')
            if unit(QA,'ActiveState')=='failed':raise RuntimeError('QA_STARTUP_PROCESS_FAILED')
            if ready():break
            time.sleep(2)
        else:raise RuntimeError('QA_READY_EXCEEDED_180_SECONDS')
        result['ready_seconds']=round(time.monotonic()-start,2)
        invocation=unit(QA,'InvocationID')
        logs=command(['journalctl','--no-pager','-o','cat','_SYSTEMD_INVOCATION_ID='+invocation])
        result['jpa_validate_passed']='Initialized JPA EntityManagerFactory' in logs
        result['boot_started']='Started BackendApplication in ' in logs
        result['oom_observed']='OutOfMemoryError' in logs or unit(QA,'Result')=='oom-kill'
        result['automatic_restarts']=int(unit(QA,'NRestarts'))
        if not result['jpa_validate_passed'] or not result['boot_started']:raise RuntimeError('QA_CONTEXT_NOT_CONFIRMED')
        if result['oom_observed'] or result['automatic_restarts']!=0:raise RuntimeError('OOM_OR_UNEXPECTED_RESTART')
        for _ in range(3):
            if not ready():raise RuntimeError('QA_GET_UNSTABLE')
        if identity()!=before:raise RuntimeError('EXISTING_SERVICE_CHANGED')
        result.update(status='PASS',phase='CHECK_COMPLETE',get_http=200)
    except Exception as e:
        text=str(e);result.update(status='FAIL',error_code=text if re.fullmatch('[A-Z0-9_]+',text) else type(e).__name__)
    finally:
        command(['systemctl','stop',QA,GRAPH],check=False);marker.unlink(missing_ok=True)
        lock=mysql("ALTER USER 'wa_qa_pilot'@'127.0.0.1' ACCOUNT LOCK; SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1';")
        result['qa_account_locked']=lock=='Y'
        DROP.unlink(missing_ok=True)
        if DROP.parent.exists() and not any(DROP.parent.iterdir()):DROP.parent.rmdir()
        command(['systemctl','daemon-reload'],check=False)
        command(['systemctl','stop','dimabug-wa-startcheck-cutoff.timer'],check=False)
        result['qa_stopped']=all(unit(n,'ActiveState') not in ['active','activating','deactivating'] for n in [QA,GRAPH])
        result['existing_services_unchanged']=identity()==before
        result.update(phase='FINISHED',available_min_mib=round(result.get('available_min_mib',available())))
        if not all(result[k] for k in ['qa_account_locked','qa_stopped','existing_services_unchanged']):result['status']='FAIL'
        progress(result);print(json.dumps(result))
    if result['status']!='PASS':raise SystemExit(1)
if __name__=='__main__':main()
