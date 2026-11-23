"""Una solicitud autorizada; mantenimiento API, Gmail temporal y cierre seguro.
No expone códigos, hashes, tokens, contraseñas, cuerpos HTTP ni mensajes del proveedor.
"""
import datetime
import fcntl
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import stat
import subprocess
import sys
import tempfile
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from instalar_custodia_staging import assignments, build_config, PRIVATE_KEYS, JAR, JAR_SHA, require
from aislar_staging import EXCLUSION, exclusions, flatten

UNIT = 'dimabug@staging.service'
WATCHDOG = 'dimabug-staging-prueba-cierre'
ENV = Path('/etc/dimabug/staging.env')
BACKUPS = Path('/etc/dimabug/custodia-backups')
DEST = 'dimabug507@gmail.com'
MARKER = BACKUPS / 'prueba-codigo-unica.intentada'


def emit(**data):
    print(json.dumps(data), flush=True)


def command(args, **kwargs):
    result = subprocess.run(args, capture_output=True, text=True, timeout=90, **kwargs)
    if result.returncode != 0:
        emit(estado='COMANDO_NO_COMPLETADO', herramienta=Path(args[0]).name, codigo_salida=result.returncode)
    require(result.returncode == 0)
    return result.stdout


def service(unit=UNIT):
    raw = command(['systemctl', 'show', unit, '-pActiveState', '-pSubState', '-pMainPID', '-pInvocationID', '-pNRestarts'])
    return dict(line.split('=', 1) for line in raw.splitlines() if '=' in line)


def check_env(env, real):
    require(env.get('GMAIL_ENVIO_REAL_HABILITADO') == str(real).lower())
    require(env.get('GMAIL_OAUTH_HABILITADO') == 'true')
    require(env.get('DIMABUG_AUTOMATIZACION_MODO') == 'DESHABILITADO')
    require(EXCLUSION in exclusions(env.get('SPRING_AUTOCONFIGURE_EXCLUDE', '')))
    require(env.get('GMAIL_SENDER_EMAIL') == DEST)


def process_env():
    info = service()
    require(info['ActiveState'] == 'active' and info['MainPID'] != '0')
    data = (Path('/proc') / info['MainPID'] / 'environ').read_bytes().split(b'\0')
    return dict(item.decode().split('=', 1) for item in data if b'=' in item)


def with_flag(raw, enabled):
    env = assignments(raw)
    require('GMAIL_ENVIO_REAL_HABILITADO' in env)
    lines = raw.decode().splitlines(keepends=True)
    changed = 0
    for index, line in enumerate(lines):
        if line.strip().split('=', 1)[0] == 'GMAIL_ENVIO_REAL_HABILITADO':
            lines[index] = 'GMAIL_ENVIO_REAL_HABILITADO=' + str(enabled).lower() + '\n'
            changed += 1
    require(changed == 1)
    result = ''.join(lines).encode()
    after = assignments(result)
    require({k: v for k, v in env.items() if k != 'GMAIL_ENVIO_REAL_HABILITADO'} ==
            {k: v for k, v in after.items() if k != 'GMAIL_ENVIO_REAL_HABILITADO'})
    return result


def replace(path, expected, updated):
    require(path.read_bytes() == expected and not path.is_symlink())
    metadata = path.stat()
    require(metadata.st_uid == 0 and not metadata.st_mode & 0o022)
    fd, name = tempfile.mkstemp(prefix='.prueba-codigo.', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as f:
            os.fchown(f.fileno(), metadata.st_uid, metadata.st_gid)
            os.fchmod(f.fileno(), stat.S_IMODE(metadata.st_mode))
            f.write(updated)
            f.flush()
            os.fsync(f.fileno())
        require(path.read_bytes() == expected)
        os.replace(name, path)
        require(path.read_bytes() == updated)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def backup(path, label):
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    dest = BACKUPS / (label + '.' + stamp + '.' + str(os.getpid()) + '.bak')
    metadata = path.stat()
    with dest.open('xb') as f:
        os.fchown(f.fileno(), metadata.st_uid, metadata.st_gid)
        os.fchmod(f.fileno(), stat.S_IMODE(metadata.st_mode))
        f.write(path.read_bytes())
        f.flush()
        os.fsync(f.fileno())
    shutil.copystat(path, dest)
    require(path.read_bytes() == dest.read_bytes())
    return dest


def mysql(env, sql):
    require(sql.startswith('SELECT ') and ';' not in sql)
    js = flatten(json.loads(env['SPRING_APPLICATION_JSON'])) if env.get('SPRING_APPLICATION_JSON') else {}
    url = js.get('spring.datasource.url', env.get('DB_URL', ''))
    require(url.startswith('jdbc:mysql:'))
    target = urllib.parse.urlsplit(url[5:])
    require(target.hostname in ['127.0.0.1', 'localhost'] and target.path == '/dima_bug_staging')
    user = js.get('spring.datasource.username', env.get('DB_USERNAME'))
    password = js.get('spring.datasource.password', env.get('DB_PASSWORD'))
    require(isinstance(user, str) and isinstance(password, str))
    def quote(value):
        require('\n' not in value and '\r' not in value and '\0' not in value)
        return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'
    # Opción MySQL en memfd anónimo heredado: nunca crear archivo de credenciales en disco.
    fd = os.memfd_create('dimabug-prueba-db', flags=0)
    try:
        os.fchmod(fd, 0o600)
        config = '[client]\nprotocol=TCP\nhost=' + quote(target.hostname) + '\nport=' + str(target.port or 3306)
        config += '\nuser=' + quote(user) + '\npassword=' + quote(password) + '\ndatabase=dima_bug_staging\nconnect-timeout=5\n'
        os.write(fd, config.encode())
        os.lseek(fd, 0, os.SEEK_SET)
        mysql_env = {k: v for k, v in os.environ.items() if not k.startswith('MYSQL_')}
        mysql_env['MYSQL_TEST_LOGIN_FILE'] = '/dev/null'
        return command(['mysql', '--defaults-file=/proc/self/fd/' + str(fd),
                        '--batch', '--raw', '--skip-column-names', '--execute', sql],
                       pass_fds=(fd,), env=mysql_env).strip()
    finally:
        os.close(fd)


def baseline(env):
    row = mysql(env, "SELECT u.usuario_id,u.usuario_estado,r.rol_estado FROM u_usuario u JOIN u_rol r ON r.rol_id=u.rol_id WHERE LOWER(TRIM(u.usuario_email))='" + DEST + "'")
    require('\n' not in row)
    fields = row.split('\t')
    require(len(fields) == 3 and fields[0].isdigit() and fields[1:] == ['1', '1'])
    uid = fields[0]
    account = mysql(env, 'SELECT estado,version,(access_token_cifrado IS NOT NULL),(refresh_token_cifrado IS NOT NULL),(revocacion_cifrada IS NOT NULL) FROM gm_cuenta_oauth WHERE id=1').split('\t')
    require(len(account) == 5 and account[0] == 'ACTIVA' and account[2:] == ['1', '1', '0'])
    result = metadata(env, uid)
    emit(estado='BASELINE_VERIFICADO', usuario_activo=True, rol_activo=True,
         oauth_activa=True, version_oauth=int(account[1]), total_codigos=int(result['count']))
    return uid, result


def metadata(env, uid):
    require(uid.isdigit())
    return {'count': mysql(env, 'SELECT COUNT(*) FROM u_codigo_acceso WHERE usuario_id=' + uid),
            'latest': mysql(env, 'SELECT codigo_id,creado_en,expira_en,intentos,usado FROM u_codigo_acceso WHERE usuario_id=' + uid + ' ORDER BY codigo_id DESC LIMIT 1'),
            'queue': mysql(env, 'SELECT estado,intentos,COUNT(*) FROM u_correo_codigo GROUP BY estado,intentos ORDER BY estado,intentos')}


def messages(invocation):
    require(bool(re.fullmatch('[a-f0-9]{32}', invocation)))
    text = command(['journalctl', '_SYSTEMD_INVOCATION_ID=' + invocation, '--no-pager', '-o', 'json', '-n', '1500'])
    return [str(json.loads(line).get('MESSAGE', '')) for line in text.splitlines()]


def wait_ready(real, limit=75):
    end = time.monotonic() + limit
    while time.monotonic() < end:
        info = service()
        require(info['ActiveState'] not in ['failed', 'inactive'] and info['NRestarts'] == '0')
        if info['MainPID'] != '0':
            check_env(process_env(), real)
            if any('Started BackendApplication in ' in line for line in messages(info['InvocationID'])):
                with socket.create_connection(('127.0.0.1', 8081), timeout=2):
                    return info['InvocationID']
        time.sleep(1)
    raise TimeoutError()


def post(path, payload):
    require(path in ['/api/auth/recuperar/email', '/api/auth/recuperar/codigo'])
    req = urllib.request.Request('http://127.0.0.1:8081' + path,
        data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'}, method='POST')
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(req, timeout=15) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def one_request():
    # El marcador persistente se crea ANTES del POST. Nunca reintentar tras timeout.
    with MARKER.open('xb') as f:
        os.fchmod(f.fileno(), 0o600)
        f.write(datetime.datetime.now(datetime.timezone.utc).isoformat().encode())
        f.flush()
        os.fsync(f.fileno())
    return post('/api/auth/recuperar/email', {'email': DEST})


def nginx_test_reload():
    command(['nginx', '-t'])
    command(['systemctl', 'reload', 'nginx'])


def api_closed():
    end = time.monotonic() + 12
    observed = []
    while time.monotonic() < end:
        result = command(['curl', '--silent', '--show-error', '--max-time', '4',
            '--resolve', 'staging.dimabug.casol.cl:443:127.0.0.1', '--output', '/dev/null',
            '--write-out', '%{http_code}', 'https://staging.dimabug.casol.cl/api/auth/me'])
        observed.append(result)
        if result == '503':
            emit(estado='MANTENIMIENTO_HTTP_VERIFICADO', comprobaciones=len(observed))
            return
        time.sleep(0.3)
    emit(estado='MANTENIMIENTO_HTTP_NO_CONFIRMADO', codigos_http=sorted(set(observed)))
    raise RuntimeError()


def cleanup_service(original, enabled):
    command(['systemctl', 'stop', UNIT])
    require(service()['MainPID'] == '0')
    current = ENV.read_bytes()
    require(current in [original, enabled])
    if current != original:
        replace(ENV, current, original)
    check_env(assignments(ENV.read_bytes()), False)
    # El timer deja de ser necesario solo después de parar el proceso y comprobar el archivo false.
    subprocess.run(['systemctl', 'stop', WATCHDOG + '.timer'], capture_output=True, timeout=15)
    command(['systemctl', 'start', UNIT])
    wait_ready(False)
    emit(estado='GMAIL_BLOQUEADO_RECONFIRMADO', procedimientos_deshabilitados=True, smtp_excluido=True)


def interrupted(signum, frame):
    raise InterruptedError()


def controls_only():
    require(os.geteuid() == 0 and not MARKER.exists())
    lock_fd = os.open('/run/dimabug-prueba-codigo.lock', os.O_CREAT | os.O_RDWR, 0o600)
    fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    env = assignments(ENV.read_bytes())
    check_env(env, False)
    check_env(process_env(), False)
    path = Path('/etc/nginx/sites-enabled/dimabug-staging').resolve(strict=True)
    original = path.read_bytes()
    require(len(re.findall(rb'location\s+/api/\s*\{', original)) == 1)
    blocked = re.sub(rb'(location\s+/api/\s*\{)', rb'\1\n        return 503; # DimaBug prueba unica', original, count=1)
    backup(path, 'nginx.staging.control-sin-envio')
    try:
        replace(path, original, blocked)
        nginx_test_reload()
        api_closed()
        baseline(env)
        emit(estado='CONTROLES_PREVIOS_APROBADOS', envio_habilitado=False, solicitud_correo=False)
    finally:
        current = path.read_bytes()
        require(current in [original, blocked])
        if current != original:
            replace(path, current, original)
            nginx_test_reload()
        check_env(process_env(), False)
        emit(estado='CONTROL_SIN_ENVIO_CERRADO', nginx_restaurado=True, gmail_false=True)
        os.close(lock_fd)


def execute():
    require(os.geteuid() == 0 and os.isatty(0) and not MARKER.exists())
    for sig in [signal.SIGTERM, signal.SIGINT, signal.SIGHUP]:
        signal.signal(sig, interrupted)
    lock_fd = os.open('/run/dimabug-prueba-codigo.lock', os.O_CREAT | os.O_RDWR, 0o600)
    fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    require(service('dimabug@staging7.service')['MainPID'] == '0')
    require(service()['ActiveState'] == 'active')
    original = ENV.read_bytes()
    metadata_env = ENV.stat()
    require(metadata_env.st_uid == metadata_env.st_gid == 0 and stat.S_IMODE(metadata_env.st_mode) == 0o600)
    env = assignments(original)
    check_env(env, False)
    check_env(process_env(), False)
    build_config(original, {k: env[k] for k in PRIVATE_KEYS})
    with JAR.open('rb') as f:
        require(hashlib.file_digest(f, 'sha256').hexdigest() == JAR_SHA)
    nginx_path = Path('/etc/nginx/sites-enabled/dimabug-staging').resolve(strict=True)
    require(nginx_path.parent in [Path('/etc/nginx/sites-enabled'), Path('/etc/nginx/sites-available')])
    nginx_original = nginx_path.read_bytes()
    require(b'staging.dimabug.casol.cl' in nginx_original)
    require(len(re.findall(rb'location\s+/api/\s*\{', nginx_original)) == 1)
    full_nginx = command(['nginx', '-T'])
    require(full_nginx.count('127.0.0.1:8081') == 1 and 'localhost:8081' not in full_nginx)
    blocked = re.sub(rb'(location\s+/api/\s*\{)', rb'\1\n        return 503; # DimaBug prueba unica', nginx_original, count=1)
    require(BACKUPS.is_dir() and not BACKUPS.is_symlink() and BACKUPS.stat().st_uid == 0
            and stat.S_IMODE(BACKUPS.stat().st_mode) == 0o700)
    env_backup = backup(ENV, 'staging.env.pre-prueba-codigo')
    nginx_backup = backup(nginx_path, 'nginx.staging.pre-prueba-codigo')
    emit(estado='RESPALDOS_VERIFICADOS', configuracion=str(env_backup), nginx=str(nginx_backup))
    enabled = with_flag(original, True)
    window_started = False
    service_closed = False
    attempted = False
    accepted = False
    http_status = None
    invocation = None
    phase_failed = False
    uid = None
    before = None
    try:
        replace(nginx_path, nginx_original, blocked)
        nginx_test_reload()
        api_closed()
        uid, before = baseline(env)
        require(command(['systemctl', 'show', WATCHDOG + '.timer', '-pLoadState', '--value']).strip() == 'not-found')
        command(['systemd-run', '--quiet', '--unit=' + WATCHDOG, '--on-active=180s',
                 '/usr/bin/systemctl', 'stop', UNIT])
        window_started = True
        command(['systemctl', 'stop', UNIT])
        require(service()['MainPID'] == '0')
        replace(ENV, original, enabled)
        command(['systemctl', 'start', UNIT])
        # systemctl simple devuelve tras exec: comprobar que recibió true antes de dejar disco en false.
        check_env(process_env(), True)
        replace(ENV, enabled, original)
        invocation = wait_ready(True)
        api_closed()
        emit(estado='VENTANA_PRIVADA_LISTA', api_publica_bloqueada=True, archivo_en_disco_false=True,
             watchdog_stop_segundos=180, destinatario_unico=True)
        attempted = True
        http_status = one_request()
        emit(estado='SOLICITUD_UNICA_REALIZADA', http=http_status)
        end = time.monotonic() + 35
        while time.monotonic() < end:
            logs = messages(invocation)
            accepted = any('Gmail API' in m and DEST in m and 'enviado' in m.lower() and 'recuperaci' in m.lower() for m in logs)
            failed = any(DEST in m and 'No se pudo enviar' in m for m in logs)
            if accepted or failed or http_status != 200:
                break
            time.sleep(1)
    except Exception as exc:
        phase_failed = True
        frames = traceback.extract_tb(exc.__traceback__)
        emit(estado='PRUEBA_NO_CONFIRMADA', categoria=type(exc).__name__, solicitud_intentada=attempted,
             linea_control=frames[-2].lineno if len(frames) > 1 else None)
    finally:
        if window_started:
            try:
                cleanup_service(original, enabled)
                service_closed = True
            except Exception as exc:
                subprocess.run(['systemctl', 'stop', UNIT], capture_output=True, timeout=90)
                emit(estado='CIERRE_REQUIERE_REVISION', categoria=type(exc).__name__, api_publica_sigue_bloqueada=True)
        else:
            service_closed = True
        # El acceso público permanece cerrado durante la recepción/validación privada.
        try:
            if service_closed:
                if invocation:
                    accepted = accepted or any('Gmail API' in m and DEST in m and 'enviado' in m.lower() and 'recuperaci' in m.lower() for m in messages(invocation))
                after = metadata(env, uid) if uid else None
                counts_ok = bool(before and after and int(after['count']) == int(before['count']) + 1)
                queue_ok = bool(before and after and after['queue'] == before['queue'])
                emit(estado='RESULTADO_TRANSPORTE', http=http_status, aceptacion_gmail_observada=accepted,
                     nuevo_codigo_unico=counts_ok, cola_historica_sin_cambios=queue_ok,
                     metadatos_codigo=after['latest'].split('\t') if after and after['latest'] else None,
                     resultado_incierto=attempted and not accepted)
                received = False
                validated = False
                if not phase_failed and http_status == 200 and accepted and counts_ok and queue_ok:
                    signal.signal(signal.SIGALRM, interrupted)
                    signal.alarm(120)
                    try:
                        print('Confirma el mensaje nuevo en Recibidos/Spam de la cuenta oficial (no solo Enviados). Escribe SI si lo recibiste: ', file=sys.stderr, end='', flush=True)
                        answer = input()
                        received = answer.strip().upper() == 'SI'
                        if received:
                            code = getpass.getpass('Codigo recibido (oculto; un solo intento de validacion): ')
                            require(bool(re.fullmatch(r'\d{6}', code)))
                            validated = post('/api/auth/recuperar/codigo', {'email': DEST, 'codigo': code}) == 200
                            code = None
                    except (InterruptedError, EOFError, Exception):
                        pass
                    finally:
                        signal.alarm(0)
                emit(estado='RESULTADO_FUNCIONAL', recepcion_confirmada_por_usuario=received,
                     codigo_validado=validated, password_sin_cambios=True, codigo_no_mostrado=True)
                # Solo restaurar la ruta pública después de verificar proceso bloqueado y archivo false.
                check_env(assignments(ENV.read_bytes()), False)
                check_env(process_env(), False)
                current_nginx = nginx_path.read_bytes()
                require(current_nginx in [nginx_original, blocked])
                if current_nginx != nginx_original:
                    replace(nginx_path, current_nginx, nginx_original)
                    nginx_test_reload()
                require(service('dimabug@staging7.service')['MainPID'] == '0')
                emit(estado='CIERRE_VERIFICADO', gmail_false=True, procedimientos_deshabilitados=True,
                     smtp_excluido=True, nginx_restaurado=True, staging7_detenido=True,
                     solicitud_unica=attempted, secretos_mostrados=False)
        except Exception as exc:
            emit(estado='REVISION_FINAL_PENDIENTE', categoria=type(exc).__name__)
        os.close(lock_fd)


if __name__ == '__main__':
    try:
        if sys.argv[1:] == ['--verificar-controles']:
            controls_only()
        elif sys.argv[1:] == ['--intento-2']:
            # Segundo intento autorizado expresamente; conservar el marcador del primero.
            MARKER = BACKUPS / 'prueba-codigo-intento-2.intentada'
            WATCHDOG = 'dimabug-staging-prueba-cierre-2'
            execute()
        else:
            require(not sys.argv[1:])
            execute()
    except Exception as exc:
        emit(estado='PRECONDICION_NO_COMPLETADA', categoria=type(exc).__name__, secretos_mostrados=False)
        raise SystemExit(1)
