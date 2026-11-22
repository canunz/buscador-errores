"""Preparación: por defecto SOLO plan. No instala, programa ni conecta por SSH."""
import argparse
import datetime as dt
import http.client
import json
import math
import os
from pathlib import Path
import platform
import signal
import ssl
import time
from urllib.parse import urlsplit

DURATION=86400
INTERVAL=60
PROBE_INTERVAL=300
ALLOWED={'staging':'staging.dimabug.casol.cl','avanza':'avanza.casol.cl','buscador':'buscadorerrores.casol.cl'}
UNITS={'staging':'system-dimabug.slice/dimabug@staging.service','mysql':'mysql.service','buscador':'buscadorerrores.service','pm2_manager':'pm2-root.service'}

def parse_targets(data):
    targets=data.get('targets',[])
    if not isinstance(targets,list) or len(targets)>3:raise ValueError('Lista de destinos no valida')
    result=[];seen=set()
    for item in targets:
        name=item['name'];url=item['url'];u=urlsplit(url)
        if (name not in ALLOWED or name in seen or u.scheme!='https' or
            u.hostname!=ALLOWED[name] or u.port not in (None,443) or u.username or
            u.password or u.query or u.fragment or not u.path.startswith('/') or
            '__' in u.path or 'PENDIENTE' in u.path.upper()):
            raise ValueError('Destino no aprobado: revisar host, ruta y ausencia de credenciales/query')
        seen.add(name);result.append({'name':name,'url':url})
    return result

def meminfo(text):
    values={k:int(v.split()[0]) for k,v in (line.split(':',1) for line in text.splitlines() if ':' in line)}
    return {'total_kib':values['MemTotal'],'available_kib':values.get('MemAvailable'),
        'swap_used_kib':values.get('SwapTotal',0)-values.get('SwapFree',0)}

def cpu_counters(text):
    values=list(map(int,text.splitlines()[0].split()[1:]))
    # user/nice incluyen guest: sumar solo los primeros ocho evita duplicarlo.
    return {'total':sum(values[:8]),'idle':values[3],'iowait':values[4],'steal':values[7]}

def cpu_delta(before,after):
    total=after['total']-before['total']
    if total<=0 or any(after[k]<before[k] for k in before):return None
    return {k:round(100*(after[k]-before[k])/total,3) for k in ['idle','iowait','steal']}

def disk_counters(text):
    result={}
    for line in text.splitlines():
        p=line.split()
        if len(p)>=14:
            result[f'{p[0]}:{p[1]}']={'reads':int(p[3]),'read_ms':int(p[6]),'writes':int(p[7]),'write_ms':int(p[10]),'busy_ms':int(p[12])}
    return result

def io_delta(before,after,seconds):
    if seconds<=0 or any(after[k]<before[k] for k in before):return None
    ops=(after['reads']-before['reads'])+(after['writes']-before['writes'])
    duration=(after['read_ms']-before['read_ms'])+(after['write_ms']-before['write_ms'])
    return {'operations_s':round(ops/seconds,3),'mean_operation_ms':round(duration/ops,3) if ops else None,
        'busy_percent':round((after['busy_ms']-before['busy_ms'])/(seconds*10),3)}

def service_metrics():
    result={}
    for name,unit in UNITS.items():
        root=Path('/sys/fs/cgroup/system.slice')/unit
        try:
            cpu=dict(line.split() for line in (root/'cpu.stat').read_text().splitlines())
            result[name]={'memory_bytes':int((root/'memory.current').read_text()),'cpu_usage_usec':int(cpu['usage_usec'])}
        except (OSError,ValueError,KeyError) as e:result[name]={'unavailable':type(e).__name__}
    return result

def probe(target,connection_factory=http.client.HTTPSConnection):
    """Sin redirects, cookies, credenciales o lectura/registro del contenido."""
    u=urlsplit(target['url']);start=time.monotonic();connection=None
    try:
        connection=connection_factory(u.hostname,443,timeout=5,context=ssl.create_default_context())
        connection.request('GET',u.path,headers={'User-Agent':'DimaBug-QA-baseline','Connection':'close'})
        response=connection.getresponse()
        result={'name':target['name'],'status':response.status,'elapsed_ms':round((time.monotonic()-start)*1000,2)}
        response.close();return result
    except Exception as e:
        # No str(e): puede contener URL o datos del servidor.
        return {'name':target['name'],'error_type':type(e).__name__,'elapsed_ms':round((time.monotonic()-start)*1000,2)}
    finally:
        if connection is not None:connection.close()

def percentile(values,p):
    return sorted(values)[max(0,math.ceil(len(values)*p)-1)] if values else None

class Summary:
    def __init__(self):
        self.count=0;self.available=[];self.swap=[];self.cpu=[];self.disk_min={};self.io_max={};self.swap_rates=[];self.probes={};self.gaps=[];self.errors=0;self.service_max={};self.service_unavailable=set()
    def add(self,row):
        self.count+=1
        if row.get('read_error'):self.errors+=1
        mem=row.get('memory',{})
        if mem.get('available_kib') is not None:self.available.append(mem['available_kib'])
        if mem.get('swap_used_kib') is not None:self.swap.append(mem['swap_used_kib'])
        if row.get('cpu_percent'):self.cpu.append(row['cpu_percent'])
        if row.get('swap_pages_s'):self.swap_rates.append(row['swap_pages_s'])
        if row.get('interval_seconds',0)>INTERVAL*1.5:self.gaps.append(row['interval_seconds'])
        for name,service in row.get('services',{}).items():
            if 'memory_bytes' in service:self.service_max[name]=max(self.service_max.get(name,0),service['memory_bytes'])
            else:self.service_unavailable.add(name)
        for d in row.get('disks',[]):
            key=d['device'];previous=self.disk_min.get(key)
            if previous is None:self.disk_min[key]=dict(d)
            else:
                previous['available_bytes']=min(previous['available_bytes'],d['available_bytes'])
                if d.get('inodes_available') is not None:previous['inodes_available']=min(previous.get('inodes_available',d['inodes_available']),d['inodes_available'])
            for metric,value in (d.get('io') or {}).items():
                if value is not None:
                    previous_max=self.io_max.setdefault(key,{})
                    previous_max[metric]=max(previous_max.get(metric,value),value)
        for p in row.get('probes',[]):self.probes.setdefault(p['name'],[]).append(p)
    def result(self,elapsed,reason):
        http={}
        for name,rows in self.probes.items():
            latencies=[r['elapsed_ms'] for r in rows if 200<=r.get('status',0)<300]
            statuses={}
            for r in rows:
                key=str(r.get('status',r.get('error_type','unknown')));statuses[key]=statuses.get(key,0)+1
            http[name]={'samples':len(rows),'successful_samples':len(latencies),'latency_scope':'2xx','outcomes':statuses,'p50_ms':percentile(latencies,.5),'p95_ms':percentile(latencies,.95),'p99_ms':percentile(latencies,.99)}
        return {'duration_seconds':round(elapsed,2),'reason':reason,'samples':self.count,'read_errors':self.errors,
            'coverage_24h':reason=='completed' and elapsed>=DURATION and not self.gaps and not self.errors and len(self.available)==self.count and len(self.cpu)>=self.count-1 and bool(self.disk_min),
            'gaps_seconds':self.gaps,'memavailable_min_kib':min(self.available) if self.available else None,
            'swap_used_max_kib':max(self.swap) if self.swap else None,
            'cpu_idle_min_percent':min((r['idle'] for r in self.cpu),default=None),
            'cpu_non_idle_p95_percent':percentile([100-r['idle'] for r in self.cpu],.95),
            'iowait_max_percent':max((r['iowait'] for r in self.cpu),default=None),
            'steal_max_percent':max((r['steal'] for r in self.cpu),default=None),
            'swap_in_max_pages_s':max((r['pswpin'] for r in self.swap_rates),default=None),
            'swap_out_max_pages_s':max((r['pswpout'] for r in self.swap_rates),default=None),
            'disk_minima':self.disk_min,'disk_io_maxima':self.io_max,'http':http,
            'service_memory_max_bytes':self.service_max,'service_unavailable':sorted(self.service_unavailable),
            'latencies_pending':set(http)!=set(ALLOWED) or any(not h['successful_samples'] for h in http.values()),'deployment_approved':False}

def read_sample(previous,elapsed):
    raw_cpu=cpu_counters(Path('/proc/stat').read_text())
    raw_vm=dict(line.split() for line in Path('/proc/vmstat').read_text().splitlines())
    raw_io=disk_counters(Path('/proc/diskstats').read_text())
    now=time.monotonic();boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip()
    row={'utc':dt.datetime.now(dt.timezone.utc).isoformat(),'elapsed_seconds':round(elapsed,3),
        'memory':meminfo(Path('/proc/meminfo').read_text()),'cpu_percent':None,'swap_pages_s':None,'disks':[],'services':service_metrics()}
    current={'cpu':raw_cpu,'swap':{k:int(raw_vm[k]) for k in ['pswpin','pswpout']},'io':raw_io,'time':now,'boot':boot}
    same=previous is not None and boot==previous['boot']
    if same:
        seconds=now-previous['time'];row['interval_seconds']=round(seconds,3)
        row['cpu_percent']=cpu_delta(previous['cpu'],raw_cpu)
        if seconds>0 and all(current['swap'][k]>=previous['swap'][k] for k in current['swap']):
            row['swap_pages_s']={k:round((current['swap'][k]-previous['swap'][k])/seconds,4) for k in current['swap']}
    elif previous is not None:row['read_error']='boot_changed'
    devices={}
    for path in ['/var/lib','/var/log']:
        info=os.stat(path);device=f'{os.major(info.st_dev)}:{os.minor(info.st_dev)}'
        if device in devices:devices[device]['paths'].append(path);continue
        v=os.statvfs(path)
        d={'device':device,'paths':[path],'available_bytes':v.f_bavail*v.f_frsize,'size_bytes':v.f_blocks*v.f_frsize,'inodes_available':v.f_favail}
        if same and device in previous['io'] and device in raw_io:d['io']=io_delta(previous['io'][device],raw_io[device],seconds)
        devices[device]=d
    row['disks']=list(devices.values())
    return row,current

def private_json(path,data):
    with os.fdopen(os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as f:json.dump(data,f,indent=2)

def interrupt(signum,frame):
    raise KeyboardInterrupt

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute',action='store_true')
    parser.add_argument('--approval')
    parser.add_argument('--output',type=Path)
    parser.add_argument('--allow-http',action='store_true')
    parser.add_argument('--targets-file',type=Path)
    args=parser.parse_args(argv)
    targets=[]
    if args.allow_http:
        if args.targets_file is None:parser.error('HTTP requiere archivo de rutas acordadas')
        targets=parse_targets(json.loads(args.targets_file.read_text()))
        if not targets:parser.error('HTTP requiere destinos acordados no vacios')
    elif args.targets_file:parser.error('El archivo de rutas requiere --allow-http explicito')
    plan={'mode':'PLAN_ONLY','duration_seconds':DURATION,'interval_seconds':INTERVAL,'http_enabled':bool(targets),
        'probe_interval_seconds':PROBE_INTERVAL,'target_names':[t['name'] for t in targets],
        'installs_or_schedules':False,'ssh':False,'collects_request_content':False}
    if not args.execute:
        print(json.dumps(plan,indent=2));return 0
    if args.approval!='MEDICION_24H_AUTORIZADA' or args.output is None:parser.error('Falta autorizacion explicita y directorio nuevo de salida')
    if platform.system()!='Linux' or 'ID=ubuntu' not in Path('/etc/os-release').read_text():parser.error('Solo Ubuntu; no se ejecuta en esta plataforma')
    if os.geteuid()==0:parser.error('Ejecutar como operador sin sudo')
    os.umask(0o077)
    args.output.mkdir(mode=0o700,parents=False,exist_ok=False)
    private_json(args.output/'plan.json',{**plan,'mode':'EXPLICIT_EXECUTION','start_utc':dt.datetime.now(dt.timezone.utc).isoformat()})
    signal.signal(signal.SIGTERM,interrupt)
    if hasattr(signal,'SIGHUP'):signal.signal(signal.SIGHUP,interrupt)
    start=time.monotonic();deadline=start+DURATION;next_sample=start;next_probe=start;previous=None;summary=Summary();reason='interrupted'
    try:
        with os.fdopen(os.open(args.output/'samples.jsonl',os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as stream:
            while True:
                now=time.monotonic()
                if now<next_sample:time.sleep(min(next_sample-now,60));continue
                try:row,previous=read_sample(previous,now-start)
                except (OSError,ValueError,KeyError) as e:
                    row={'elapsed_seconds':now-start,'read_error':type(e).__name__}
                if targets and now>=next_probe:
                    row['probes']=[probe(t) for t in targets];next_probe=now+PROBE_INTERVAL
                stream.write(json.dumps(row)+'\n');stream.flush();summary.add(row)
                if now>=deadline:reason='completed';break
                next_sample=min(deadline,now+INTERVAL)
    except KeyboardInterrupt:pass
    finally:private_json(args.output/'summary.json',summary.result(time.monotonic()-start,reason))
    # Solo conteo; no URLs ni secretos. Un informe completo no aprueba despliegue.
    print(json.dumps({'reason':reason,'samples':summary.count,'deployment_approved':False}))
    return 0 if reason=='completed' else 2

if __name__=='__main__':raise SystemExit(main())
