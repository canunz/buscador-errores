"""Pinned-host read-only connection probe. No acceptance or known_hosts updates."""
from pathlib import Path
import base64,hashlib,json,os,subprocess
expected='SHA256:rkQjRy53ifQdKPouFp+p/mZVcM9KnQcwZFX/nKrqnN0'
home=Path(os.environ['USERPROFILE'])
query=subprocess.run(['ssh-keygen','-F','157.230.187.214','-f',str(home/'.ssh/known_hosts')],capture_output=True,text=True)
matched=False
for line in query.stdout.splitlines():
 parts=line.split()
 if len(parts)>=3 and parts[1]=='ssh-ed25519':
  fingerprint='SHA256:'+base64.b64encode(hashlib.sha256(base64.b64decode(parts[2])).digest()).decode().rstrip('=')
  matched |= fingerprint==expected
if not matched:
 print(json.dumps({'pinned_identity_verified':False,'remote_connected':False}));raise SystemExit(1)
args=['ssh','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','HostKeyAlgorithms=ssh-ed25519','-o','ConnectTimeout=10','-o','IdentitiesOnly=yes','-i',str(home/'.ssh/casol_admin'),'carolina@157.230.187.214']
p=subprocess.run(args+["printf 'SSH_READ_OK\\n'; sudo -n true >/dev/null 2>&1; printf 'SUDO_READ_EXIT=%s\\n' \"$?\""],capture_output=True,text=True,timeout=20)
print(json.dumps({'pinned_identity_verified':True,'remote_connected':p.returncode==0 and 'SSH_READ_OK' in p.stdout,'sudo_noninteractive': 'SUDO_READ_EXIT=0' in p.stdout,'ssh_exit':p.returncode,'authentication_required':p.returncode==255 and 'Permission denied' in p.stderr,'details_suppressed':True}))
