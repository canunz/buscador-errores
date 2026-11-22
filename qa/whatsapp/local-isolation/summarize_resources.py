"""Resume evidencias C.3; no inicia contenedores ni cambia configuración."""
import json,sys
from pathlib import Path

def pairs(text):
    return {k:int(v) for k,v in (line.split() for line in text.splitlines())}

def summarize(path):
    r=json.loads((path/'informe.json').read_text())
    rows=[]
    for s in r['samples']:
        c=s['cgroup'];stat=pairs(c['memory.stat']);events=pairs(c['memory.events'])
        rows.append({'phase':s['phase'],'service':'spring' if s['container'].endswith('-spring') else 'mysql',
            'current_mib':round(int(c['memory.current'])/1048576,1),
            'peak_mib':round(int(c['memory.peak'])/1048576,1),
            'anon_mib':round(stat.get('anon',0)/1048576,1),
            'file_mib':round(stat.get('file',0)/1048576,1),
            'kernel_mib':round(stat.get('kernel',0)/1048576,1),
            'max_events':events.get('max',0),'oom':events.get('oom',0),'oom_kill':events.get('oom_kill',0),
            'cpu':pairs(c['cpu.stat'])})
    peak_ok=bool(rows) and all(row['peak_mib']<512*.9 for row in rows)
    samples_ok=bool(rows) and all(row['current_mib']<384 for row in rows if row['phase'] in ['warmup','load'])
    no_oom=bool(rows) and all(row['oom']==0 and row['oom_kill']==0 for row in rows)
    load=r.get('load',{})
    warmup=r.get('warmup',{})
    return {'evidencia':str(path),'variant':r['variant'],'mysql_version':r.get('mysql_version'),
        'functional':r.get('functional'),'error':r.get('error'),'metrics':rows,'warmup':r.get('warmup'),'load':load,
        'gates':{'peak_under_90_percent_all_phases':peak_ok,'endpoint_samples_under_high':samples_ok,
            'no_oom':no_oom,'load_latency':load.get('p95_ms',float('inf'))<=1000 and load.get('p99_ms',float('inf'))<=3000,
            'warmup_latency':warmup.get('p95_ms',float('inf'))<=1000 and warmup.get('p99_ms',float('inf'))<=3000},
        'baseline_24h':False,'ubuntu_capacity_approved':False}

if __name__=='__main__':
    for arg in sys.argv[1:]:print(json.dumps(summarize(Path(arg)),indent=2))
