"""Valida HAProxy TLS con certificado localhost NUEVO; no arranca listeners."""
from pathlib import Path
import json,os,re,shutil,subprocess,sys,uuid

ROOT=Path(__file__).resolve().parents[3]
run=Path(sys.argv[1]).resolve()
if not run.is_relative_to(ROOT/'qa/runtime') or not re.fullmatch('dimabug-wa-https-[a-f0-9]{10}',run.name):raise ValueError('Directorio de QA no autorizado')
informe=json.loads((run/'informe.json').read_text())
if informe['exit_code']!=0 or informe['contenedor']!=run.name:raise ValueError('Se requiere un recorrido local aprobado')
dest=run/('tls-haproxy-'+uuid.uuid4().hex[:8]);dest.mkdir()
openssl=next((p for p in [Path('C:/Program Files/Git/usr/bin/openssl.exe'),Path('C:/Program Files/Git/mingw64/bin/openssl.exe')] if p.is_file()),None)
if openssl is None:raise RuntimeError('OpenSSL no disponible; no se instala automáticamente')
allow={k.upper() for k in ['SystemRoot','WINDIR','SystemDrive','ComSpec','USERPROFILE','TEMP','TMP']}
env={k:v for k,v in os.environ.items() if k.upper() in allow};env['PATH']=str(openssl.parent)+os.pathsep+str(Path(os.environ['SystemRoot'])/'System32')
(dest/'openssl.cnf').write_text('[req]\ndistinguished_name=dn\nprompt=no\nx509_extensions=ext\n[dn]\nCN=localhost\n[ext]\nsubjectAltName=DNS:localhost,IP:127.0.0.1\nbasicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\n',encoding='ascii')
p=subprocess.run([str(openssl),'req','-x509','-newkey','rsa:2048','-nodes','-sha256','-days','2','-config',str(dest/'openssl.cnf'),'-keyout',str(dest/'key.pem'),'-out',str(dest/'cert.pem')],env=env,capture_output=True,timeout=40)
if p.returncode:raise RuntimeError('No se pudo preparar el certificado ficticio')
(dest/'localhost.pem').write_bytes((dest/'key.pem').read_bytes()+(dest/'cert.pem').read_bytes())
(dest/'openssl-version.txt').write_bytes(subprocess.check_output([str(openssl),'version'],env=env))
docker=shutil.which('docker.exe') or shutil.which('docker')
image='sha256:5378234d19457d645ed6d5a0ac994ef196614f9a2cabda8f6ccd6ac8815f9098'
name=run.name+'-tls-'+uuid.uuid4().hex[:6]
p=subprocess.run([docker,'--context','desktop-linux','run','--pull','never','--name',name,'--label','dimabug.wa32b='+run.name[-10:],'--network','none','--read-only','--cap-drop','ALL','--user','65534:65534',
 '--mount',f'type=bind,source={ROOT}/qa/whatsapp/https/haproxy.tls.local.cfg,target=/qa/haproxy.cfg,readonly',
 '--mount',f'type=bind,source={dest}/localhost.pem,target=/qa/localhost.pem,readonly',
 '--entrypoint','haproxy',image,'-c','-f','/qa/haproxy.cfg'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=40)
(dest/'validacion.txt').write_text(p.stdout+p.stderr,encoding='utf-8')
(dest/'informe.json').write_text(json.dumps({'exit_code':p.returncode,'contenedor':name,'solo_validacion_sintaxis_y_pem':True,'listener_iniciado':False,'certificado':'autofirmado ficticio localhost','red':'none','meta':False},indent=2),encoding='utf-8')
print('HAProxy TLS:', 'APROBADO' if p.returncode==0 else 'FALLIDO');print('EVIDENCIAS',dest)
sys.exit(p.returncode)
