"""Preparado para una inspección Ubuntu AUTORIZADA. No ejecuta Certbot/ACME.
No imprime contenidos de secretos, argumentos de servicios ni configuración completa.
"""
from pathlib import Path
import argparse,json,os,re,shutil,stat,subprocess,sys

def command(args):
    try:
        p=subprocess.run(args,capture_output=True,text=True,timeout=10)
        return p.returncode,p.stdout.strip()
    except (OSError,subprocess.TimeoutExpired):return -1,''

def inspect():
    out={'modo':'solo lectura; no renovación ni ACME','instalaciones':[],'programadores':[],'lineages':[]}
    for raw in ['/usr/bin/certbot','/usr/local/bin/certbot','/snap/bin/certbot','/opt/certbot/bin/certbot']:
        p=Path(raw)
        if p.exists():out['instalaciones'].append({'ruta':raw,'destino':str(p.resolve())})
    if shutil.which('dpkg-query'):
        _,v=command(['dpkg-query','-W','-f=${Package} ${Version}\n','certbot','python3-certbot-nginx'])
        out['paquetes_apt']=v.splitlines()
    if shutil.which('snap'):
        code,v=command(['snap','list','certbot']);out['paquete_snap']=v.splitlines() if code==0 else []
    for timer,service in [('certbot.timer','certbot.service'),('snap.certbot.renew.timer','snap.certbot.renew.service')]:
        _,active=command(['systemctl','show',timer,'--property=ActiveState','--value'])
        _,enabled=command(['systemctl','is-enabled',timer])
        _,exe=command(['systemctl','show',service,'--property=ExecStart','--value'])
        # ExecStart completo puede contener datos privados: solo extraer la ruta del binario.
        match=re.search(r'path=([^ ;}]+)',exe)
        out['programadores'].append({'timer':timer,'activo':active,'habilitado':enabled,'binario':match.group(1) if match else None})
    for base in [Path('/etc/letsencrypt'),Path('/var/snap/certbot/common')]:
        renewal=base/'renewal'
        if not renewal.is_dir():continue
        for f in sorted(renewal.glob('*.conf')):
            try:
                s=f.stat();allowed={}
                # Solo plugins; no volcar renewalparams ni archivos referenciados.
                for line in f.read_text().splitlines():
                    m=re.fullmatch(r'\s*(authenticator|installer)\s*=\s*([A-Za-z0-9_-]+)\s*',line)
                    if m:allowed[m.group(1)]=m.group(2)
                out['lineages'].append({'config':str(f),'uid':s.st_uid,'modo':oct(stat.S_IMODE(s.st_mode)),'plugins':allowed})
            except (PermissionError,OSError):out['lineages'].append({'config':str(f),'lectura':'no autorizada'})
    crons=[]
    for p in [Path('/etc/cron.d/certbot'),Path('/etc/crontab')]:
        try:
            if p.is_file() and 'certbot' in p.read_text():crons.append(str(p))
        except (OSError,PermissionError):pass
    out['cron_con_referencia_certbot']=crons
    activos=[x for x in out['programadores'] if x['activo']=='active']
    out['candidato_administrador']=activos[0]['timer'] if len(activos)==1 else None
    out['conclusion']='PENDIENTE: contrastar timer, binario, config-dir, lineage y cron; no elegir por which certbot'
    return out

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--lectura-autorizada',action='store_true');a=p.parse_args()
    if not a.lectura_autorizada or not sys.platform.startswith('linux'):raise SystemExit('Requiere Ubuntu e inspección de lectura expresamente autorizada')
    print(json.dumps(inspect(),indent=2))
