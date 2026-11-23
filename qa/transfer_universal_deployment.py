from pathlib import Path
import base64,hashlib,json,os,shlex,subprocess
R=Path(__file__).resolve().parents[1];V=R/'qa/artifacts/registro-universal-deploy-20261115'
plan=json.loads((V/'transfer-plan.json').read_text());home=Path(os.environ['USERPROFILE'])
p=Path(plan['package']);assert hashlib.sha256(p.read_bytes()).hexdigest()==plan['package_sha256']
known=subprocess.run(['ssh-keygen','-F','157.230.187.214','-f',str(home/'.ssh/known_hosts')],capture_output=True,text=True)
ok=False
for line in known.stdout.splitlines():
 a=line.split()
 if len(a)>=3 and a[1]=='ssh-ed25519':ok |= 'SHA256:'+base64.b64encode(hashlib.sha256(base64.b64decode(a[2])).digest()).decode().rstrip('=')=='SHA256:rkQjRy53ifQdKPouFp+p/mZVcM9KnQcwZFX/nKrqnN0'
assert ok,'PINNED_SERVER_IDENTITY_NOT_VERIFIED'
options=['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','HostKeyAlgorithms=ssh-ed25519','-o','IdentitiesOnly=yes','-o','ForwardAgent=no','-i',str(home/'.ssh/casol_admin')]
target='carolina@157.230.187.214'
def remote(code):
 q=subprocess.run(['ssh',*options,target,'python3 -I -B -c '+shlex.quote(code)],capture_output=True,text=True,timeout=40)
 assert q.returncode==0,'REMOTE_PREPARATION_OR_TRANSFER_CHECK_FAILED';return q.stdout
remote('from pathlib import Path;p=Path('+repr(plan['incoming'])+');p.mkdir(mode=0o700)')
q=subprocess.run(['scp','-q',*options,str(p),target+':'+plan['remote_zip']],capture_output=True,text=True,timeout=240)
assert q.returncode==0,'TRANSFER_NOT_COMPLETED'
code='from pathlib import Path;import hashlib,json;p=Path('+repr(plan['remote_zip'])+');p.chmod(0o600);assert hashlib.file_digest(p.open("rb"),"sha256").hexdigest()=='+repr(plan['package_sha256'])+';print(json.dumps({"package_verified":True,"active_staging_modified":False}))'
print(remote(code).strip());print(json.dumps({'deployment_id':plan['deployment_id'],'launcher_ready':plan['launcher'],'package_sha256':plan['package_sha256'],'sudo_execution_pending':True}))
