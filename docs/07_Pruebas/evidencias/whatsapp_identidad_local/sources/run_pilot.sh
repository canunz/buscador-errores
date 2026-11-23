#!/bin/bash
# Supervisor del piloto: bloqueo final y corte independiente, incluso ante fallo del ejecutor.
set -euo pipefail
[[ ${1:-} == INSTALAR_Y_EJECUTAR_PILOTO_QA_AUTORIZADO ]] || exit 2
[[ $(id -u) == 0 ]] || exit 2
cd "$(dirname "$0")"
python3 - <<'PY'
from pathlib import Path
import json,hashlib
r=Path.cwd();m=json.loads((r/'manifest.json').read_text())
assert all((r/k).resolve().is_relative_to(r) and hashlib.sha256((r/k).read_bytes()).hexdigest()==v for k,v in m['sha256'].items()),'BUNDLE_CHANGED'
PY
cleanup() {
  status=$?
  trap - EXIT HUP INT TERM
  /bin/sh ./rollback.sh DETENER_Y_BLOQUEAR_SOLO_QA || status=1
  systemctl stop dimabug-wa-pilot-account-cutoff.timer >/dev/null 2>&1 || true
  STATUS_CODE=$status python3 - <<'PY'
import json,os,subprocess
from pathlib import Path
p=Path('pilot-status.json')
try:r=json.loads(p.read_text())
except Exception:r={'phase':'PREPARATION_FAILED','status':'FAIL'}
units=['dimabug-wa-pilot.service','dimabug-wa-pilot-graph.service']
r['qa_stopped']=all(subprocess.run(['systemctl','is-active',u],capture_output=True,text=True).stdout.strip() not in ('active','activating','deactivating') for u in units)
q="SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1'"
x=subprocess.run(['mysql','--protocol=socket','--batch','--skip-column-names','-e',q],capture_output=True,text=True)
r['qa_account_locked']=x.returncode==0 and x.stdout.strip()=='Y'
r['supervisor_exit']=int(os.environ['STATUS_CODE'])
if not r['qa_stopped'] or not r['qa_account_locked'] or r['supervisor_exit']:r['status']='FAIL'
r['phase']='FINISHED'
p.write_text(json.dumps(r,indent=2)+'\n');p.chmod(0o644)
PY
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM
# Instalar solo si aún no existe: el instalador se niega a sobrescribir recursos.
if [[ ! -e /etc/systemd/system/dimabug-wa-pilot.service ]]; then
  /bin/sh ./install.sh INSTALAR_SOLO_PILOTO_QA
fi
systemd-run --unit=dimabug-wa-pilot-account-cutoff --on-active=45m /bin/sh "$PWD/rollback.sh" DETENER_Y_BLOQUEAR_SOLO_QA
mysql --protocol=socket --batch --skip-column-names <<'SQL'
ALTER USER 'wa_qa_pilot'@'127.0.0.1' ACCOUNT UNLOCK;
SQL
python3 -u ./pilot.py --execute --approval PILOTO_LOCAL_SIMULADO_Y_RECUPERACION_QA
