"""Reinicia exclusivamente la instancia local staging autorizada, sin persistir su entorno."""
import argparse
import json
import subprocess
import time
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit
import psutil

parser = argparse.ArgumentParser()
parser.add_argument('--pid', type=int, required=True)
parser.add_argument('--preserve-configuration', action='store_true', help='Reinicia con los argumentos y entorno originales, sin cambiar modo o Gmail.')
args = parser.parse_args()
original = psutil.Process(args.pid)
command = original.cmdline()
environment = original.environ()
url = urlsplit(environment.get('DB_URL', '').removeprefix('jdbc:'))
if 'cl.casol.backend.BackendApplication' not in command or url.hostname != '127.0.0.1' or url.port != 3307 or url.path != '/dima_bug_staging':
    raise SystemExit('La instancia no corresponde al staging local autorizado.')
perfiles_locales = {p.strip() for p in environment.get('SPRING_PROFILES_ACTIVE', '').split(',') if p.strip()}
if 'staging' not in perfiles_locales or not perfiles_locales.issubset({'staging', 'gmail'}):
    raise SystemExit('El proceso no usa el perfil staging esperado.')
if not any(c.status == psutil.CONN_LISTEN and c.laddr.port == 9090 for c in original.net_connections(kind='tcp')):
    raise SystemExit('El PID no atiende el puerto 9090 esperado.')
classpath = next((command[i+1] for i, value in enumerate(command[:-1]) if value in ('-cp', '-classpath', '--class-path')), None)
if not classpath:
    raise SystemExit('No se encontró el classpath de la aplicación.')
runtime = original.exe()
working_directory = original.cwd()
backend = Path(__file__).resolve().parents[4] / 'dima-bug-backend'
if classpath.startswith('@') or any(dependencia not in classpath for dependencia in ('spring-boot-starter-mail','spring-security-oauth2-client')):
    cached = backend / 'target/runtime-classpath.txt'
    if not cached.is_file():
        raise SystemExit('Regenera el classpath local antes de reiniciar; se conserva la instancia actual.')
    classpath = str(backend / 'target/classes') + ';' + cached.read_text(encoding='utf-8').strip()
logfile = backend / 'target/automatizacion-backend-local.log'
if args.preserve_configuration:
    launch = command.copy()
    classpath_index = next(i+1 for i,v in enumerate(command[:-1]) if v in ('-cp','-classpath','--class-path'))
    launch[classpath_index] = classpath
    launch[0] = runtime
    mode = environment.get('DIMABUG_AUTOMATIZACION_MODO', 'SIMULADO')
    for value in command:
        if value.startswith('--dimabug.automatizacion.modo='):
            mode = value.split('=',1)[1]
        elif value.startswith('-Ddimabug.automatizacion.modo='):
            mode = value.split('=',1)[1]
    # Solo valores públicos conocidos; no imprimir argumentos ni entorno.
    mode = mode if mode in ('SIMULADO','SMTP','GMAIL_API') else 'CONFIGURACION_ORIGINAL'
else:
    launch = [runtime, '-Dspring.output.ansi.enabled=never', '-cp', classpath,
        'cl.casol.backend.BackendApplication', '--server.port=9090', '--spring.jpa.hibernate.ddl-auto=none',
        '--spring.sql.init.mode=never', '--dimabug.automatizacion.modo=SIMULADO', '--dimabug.gmail.envio-real-habilitado=false']
    mode = 'SIMULADO'
original.terminate()
original.wait(timeout=15)
with logfile.open('wb') as log:
    nuevo = subprocess.Popen(launch,
        cwd=working_directory, env=environment, stdout=log, stderr=subprocess.STDOUT,
        creationflags=subprocess.CREATE_NO_WINDOW)
estado = {'previous_pid': args.pid, 'pid': nuevo.pid, 'port': 9090, 'mode': mode, 'configuration_preserved': args.preserve_configuration, 'schema': 'dima_bug_staging'}
for _ in range(40):
    if nuevo.poll() is not None:
        estado['exit_code'] = nuevo.returncode
        break
    try:
        with urllib.request.urlopen('http://127.0.0.1:9090/api/health', timeout=1) as response:
            estado['health_status'] = response.status
            break
    except Exception:
        time.sleep(1)
(backend / 'target/automatizacion-backend-local.json').write_text(json.dumps(estado, indent=2), encoding='utf-8')
print(json.dumps(estado))
if estado.get('health_status') != 200:
    raise SystemExit(2)
