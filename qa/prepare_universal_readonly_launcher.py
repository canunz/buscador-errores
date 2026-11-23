"""Prepare a LOCAL terminal launcher; the remote auditor executes in memory only."""
from pathlib import Path
import base64,hashlib,json,zipfile,zlib
R=Path(__file__).resolve().parents[1];V=R/'qa/artifacts/registro-universal-preflight-20261115'
V.mkdir(exist_ok=True)
base=R/'qa/artifacts/staging-prep-20261114'
requirements=json.loads((base/'orm-requirements.json').read_text(encoding='utf-8'))
review=json.loads((base/'schema-review-policy.json').read_text(encoding='utf-8'))['reviewed_varchar_contracts']
with zipfile.ZipFile(R/'qa/artifacts/staging-minimo-20261114T025917Z/artifacts/frontend-production.zip') as z:
 front={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/')}
payload={'requirements':requirements,'reviewed':review,'frontend':front}
code=(base/'schema_compat.py').read_text(encoding='utf-8')+'\nPAYLOAD='+repr(payload)+'\n'+(R/'qa/verification/staging_universal_readonly.py').read_text(encoding='utf-8')
encoded=base64.b64encode(zlib.compress(code.encode(),9)).decode()
remote="sudo python3 -I -B -c \"import base64,zlib;exec(zlib.decompress(base64.b64decode('"+encoded+"')))\""
assert len(remote)<30000,'COMMAND_TOO_LONG'
launcher=r'''$ErrorActionPreference='Stop'
# All remote actions are reads. The sudo password is entered only in the interactive SSH TTY.
& ssh -tt -o StrictHostKeyChecking=yes -o UpdateHostKeys=no -o HostKeyAlgorithms=ssh-ed25519 -o IdentitiesOnly=yes -o ForwardAgent=no -i "$env:USERPROFILE\.ssh\casol_admin" carolina@157.230.187.214 '''+"'"+remote.replace("'","''")+"'\n"
(V/'Read-Staging-Universal.ps1').write_text(launcher,encoding='utf-8-sig')
(V/'remote-auditor-source.py').write_text(code,encoding='utf-8')
print(json.dumps({'launcher':str(V/'Read-Staging-Universal.ps1'),'remote_files_written':False,'sql_select_only':True,'auditor_sha256':hashlib.sha256(code.encode()).hexdigest()}))
