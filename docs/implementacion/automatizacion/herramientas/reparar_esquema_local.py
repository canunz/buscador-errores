"""Usa solo las credenciales en memoria de la instancia backend indicada; no las persiste ni imprime."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlsplit

import psutil

parser = argparse.ArgumentParser()
parser.add_argument('--pid', type=int, required=True)
parser.add_argument('--mode', choices=['inspect', 'apply', 'verify', 'hibernate', 'flow', 'flow-rollback', 'guided-inspect', 'mail-inspect', 'gmail-inspect', 'gmail-test-inspect'], required=True)
args = parser.parse_args()
process = psutil.Process(args.pid)
command = process.cmdline()
if not any('BackendApplication' in value for value in command):
    raise SystemExit('El PID no corresponde a BackendApplication; no se realizará ninguna operación.')
environment = process.environ()
url = environment.get('DB_URL')
user = environment.get('DB_USERNAME')
password = environment.get('DB_PASSWORD')
if not all((url, user, password)):
    raise SystemExit('No se encontró la configuración esperada en el proceso; no se imprimirán sus variables.')
if any('spring.datasource.' in value and value.startswith(('-D', '--')) for value in command):
    raise SystemExit('Hay overrides de datasource en los argumentos; revisar antes de elegir la conexión.')
location = urlsplit(url.removeprefix('jdbc:'))
if location.hostname not in ('127.0.0.1', 'localhost', '::1') or location.port != 3307 or location.path != '/dima_bug_staging':
    raise SystemExit('La conexión no coincide con el destino local autorizado; operación cancelada.')
runtime = Path(process.exe())
driver = Path.home() / '.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar'
source = Path(__file__).with_name('RepairExecutionSchema.java')
if args.mode == 'guided-inspect':
    source = Path(__file__).with_name('InspectGuidedContext.java')
if args.mode == 'mail-inspect':
    source = Path(__file__).with_name('VerifyMailSchema.java')
if args.mode in ('gmail-inspect','gmail-test-inspect'):
    source = Path(__file__).with_name('VerifyGmailSchema.java')
if not driver.is_file():
    raise SystemExit('No está disponible el driver JDBC previamente instalado.')
child_env = os.environ.copy()
if args.mode == 'flow-rollback':
    # Configuración de la instancia autorizada únicamente en memoria del proceso de verificación.
    child_env.update(environment)
child_env.update(REPAIR_DB_URL=url, REPAIR_DB_USER=user, REPAIR_DB_PASSWORD=password,
                 REPAIR_EXPECTED_SCHEMA='dima_bug_staging')
print(json.dumps({'mode': args.mode, 'host': location.hostname, 'port': location.port,
                  'schema': location.path.lstrip('/'), 'backend_pid': args.pid}), flush=True)
classpath = str(driver)
launch_args = [args.mode]
if args.mode == 'guided-inspect':
    launch_args = []
if args.mode in ('hibernate', 'flow', 'flow-rollback'):
    classpath = None
    for index, value in enumerate(command[:-1]):
        if value in ('-cp', '-classpath', '--class-path'):
            classpath = command[index + 1]
            break
    if not classpath:
        raise SystemExit('No se encontró el classpath de la instancia; no se realizará la prueba Hibernate.')
    if classpath.startswith('@'):
        backend = Path(__file__).resolve().parents[4] / 'dima-bug-backend'
        cached = backend / 'target/runtime-classpath.txt'
        if not cached.is_file():
            raise SystemExit('Regenera target/runtime-classpath.txt con Maven antes de verificar Hibernate.')
        classpath = str(backend / 'target/classes') + os.pathsep + cached.read_text(encoding='utf-8').strip()
    source = Path(__file__).with_name('VerifyAutomationRollback.java' if args.mode == 'flow-rollback' else 'VerifyExecutionJpa.java')
    launch_args = ['automation'] if args.mode == 'flow' else []
result = subprocess.run([str(runtime), '--class-path', classpath, str(source), *launch_args],
                        env=child_env, text=True, capture_output=True, timeout=90)
# La utilidad Java imprime únicamente metadatos, conteos y hashes, sin contenido de filas.
if args.mode == 'flow-rollback':
    for line in (result.stdout + '\n' + result.stderr).splitlines():
        if line.startswith('verification_'):
            print(line)
else:
    sys.stdout.write(result.stdout)
    sys.stderr.write(result.stderr)
raise SystemExit(result.returncode)
