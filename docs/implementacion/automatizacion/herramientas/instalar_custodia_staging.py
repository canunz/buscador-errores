"""Instalación explícita solo de staging.env. Sin arranque, envío ni acceso MySQL.
Recibe la custodia únicamente en memoria por un socket Unix local restringido.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import shutil
import socket
import stat
import struct
import subprocess
import tempfile
import time
import sys
import traceback
import zipfile

TARGET = Path('/etc/dimabug/staging.env')
SOCKET_DIR = Path('/run/dimabug-staging-custodia')
SOCKET_PATH = SOCKET_DIR / 'entrada.sock'
JAR = Path('/opt/dimabug/staging/backend.jar')
JAR_SHA = '21df61ccb07090d36417fe3032583a38fe21a3c5721f2dac797387b2d82bd91f'
PRIVATE_KEYS = ('GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET', 'GMAIL_TOKEN_ENCRYPTION_KEY', 'GMAIL_SENDER_EMAIL')
FIXED = {'GMAIL_OAUTH_HABILITADO': 'true', 'GMAIL_ENVIO_REAL_HABILITADO': 'false',
         'GMAIL_OAUTH_REDIRECT_URI': 'https://staging.dimabug.casol.cl/login/oauth2/code/google',
         'GMAIL_FRONTEND_URL': 'https://staging.dimabug.casol.cl/admin/gmail'}


def emit(**values):
    print(json.dumps(values), flush=True)


def require(condition):
    if not condition:
        raise RuntimeError('Control no satisfecho; sin detalles sensibles')


def assignments(raw):
    parsed = {}
    for line in raw.decode('utf-8').splitlines():
        value = line.strip()
        if not value or value.startswith('#'):
            continue
        require(bool(re.match(r'^[A-Za-z_][A-Za-z0-9_]*=', value)))
        key, content = value.split('=', 1)
        require(key not in parsed)
        if len(content) >= 2 and content[0] == content[-1] and content[0] in "\"'":
            content = content[1:-1]
        parsed[key] = content
    return parsed


def build_config(original, payload):
    require(isinstance(payload, dict) and set(payload) == set(PRIVATE_KEYS))
    for value in payload.values():
        # Valores simples: nunca interpretar shell, escapes ni expansión.
        require(isinstance(value, str) and bool(re.fullmatch(r'[A-Za-z0-9_@.+=:/\-]{1,4096}', value)))
    require(payload['GMAIL_SENDER_EMAIL'] == 'dimabug507@gmail.com')
    require(len(base64.b64decode(payload['GMAIL_TOKEN_ENCRYPTION_KEY'], validate=True)) == 32)
    require(payload['GOOGLE_CLIENT_ID'].endswith('.apps.googleusercontent.com'))
    env = assignments(original)
    json_properties = {}
    def flatten(value, prefix=''):
        require(isinstance(value, dict))
        for key, item in value.items():
            require(isinstance(key, str))
            path = (prefix + '.' + key).strip('.')
            normalized = path.lower().replace('-', '').replace('_', '')
            if isinstance(item, dict):
                flatten(item, path)
            else:
                require(normalized not in json_properties)
                json_properties[normalized] = item
    if env.get('SPRING_APPLICATION_JSON'):
        flatten(json.loads(env['SPRING_APPLICATION_JSON']))
    # Conservar JSON byte a byte: no permitir que prevalezca sobre las variables Gmail.
    require(not any(k.startswith('dimabug.gmail.') or k.startswith('spring.config.')
                    for k in json_properties))
    db = json_properties.get('spring.datasource.url', env.get('DB_URL', ''))
    require(isinstance(db, str) and bool(re.search(r'/dima_bug_staging(?:\?|$)', db)))
    profile_value = json_properties.get('spring.profiles.active', env.get('SPRING_PROFILES_ACTIVE', ''))
    profiles = profile_value if isinstance(profile_value, list) else profile_value.split(',')
    require('staging' in profiles and not set(profiles).intersection({'prod', 'production'}))
    overrides = {'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS'}
    require(not any(v and (k in overrides or k.startswith(('DIMABUG_GMAIL_', 'SPRING_CONFIG_'))) for k, v in env.items()))
    updates = dict(payload, **FIXED)
    preserved = [line for line in original.decode('utf-8').splitlines()
                 if line.strip().split('=', 1)[0] not in updates]
    result = ('\n'.join(preserved).rstrip('\n') + '\n' +
              '\n'.join(k + '=' + v for k, v in updates.items()) + '\n').encode('utf-8')
    after = assignments(result)
    require(all(after[k] == v for k, v in updates.items()))
    require({k: v for k, v in env.items() if k not in updates} ==
            {k: v for k, v in after.items() if k not in updates})
    return result


def stopped():
    for unit in ('dimabug@staging.service', 'dimabug@staging7.service'):
        state = subprocess.check_output(['systemctl', 'show', unit, '-pActiveState', '-pMainPID'], text=True)
        require('ActiveState=inactive\n' in state and 'MainPID=0\n' in state)


def preflight():
    require(os.geteuid() == 0)
    stopped()
    for parent in (Path('/etc/dimabug'), TARGET):
        require(not parent.is_symlink())
        st = parent.stat()
        require(st.st_uid == 0 and not st.st_mode & 0o022)
    st = TARGET.stat()
    require(stat.S_ISREG(st.st_mode) and stat.S_IMODE(st.st_mode) == 0o600 and st.st_gid == 0)
    source = subprocess.check_output(['systemctl', 'cat', 'dimabug@staging.service'], text=True)
    require(not re.search(r'^Environment=|(?:-D|--)(?:dimabug\.|spring\.)', source, re.M))
    actual_env = subprocess.check_output(['systemctl', 'show', 'dimabug@staging.service', '-pEnvironment', '--value'], text=True)
    require(not actual_env.strip())
    require(not JAR.is_symlink())
    with JAR.open('rb') as f:
        require(hashlib.file_digest(f, 'sha256').hexdigest() == JAR_SHA)
    with zipfile.ZipFile(JAR) as z:
        properties = z.read('BOOT-INF/classes/application-staging.properties').decode()
        names = set(re.findall(r'\$\{((?:GMAIL_|GOOGLE_)[A-Z0-9_]+)', properties))
        require(set(PRIVATE_KEYS).union(FIXED).issubset(names))
    return TARGET.read_bytes(), st


def install(original, metadata, updated):
    stopped()
    require(TARGET.read_bytes() == original)
    backup_dir = TARGET.parent / 'custodia-backups'
    if not backup_dir.exists():
        backup_dir.mkdir(mode=0o700)
    require(not backup_dir.is_symlink())
    bd = backup_dir.stat()
    require(bd.st_uid == 0 and stat.S_IMODE(bd.st_mode) == 0o700)
    backup = backup_dir / ('staging.env.' + time.strftime('%Y%m%dT%H%M%SZ', time.gmtime()) + '.' + str(os.getpid()) + '.bak')
    with backup.open('xb') as f:
        os.fchmod(f.fileno(), stat.S_IMODE(metadata.st_mode))
        os.fchown(f.fileno(), metadata.st_uid, metadata.st_gid)
        f.write(original)
        f.flush()
        os.fsync(f.fileno())
    shutil.copystat(TARGET, backup, follow_symlinks=False)
    b = backup.stat()
    require(backup.read_bytes() == original and (b.st_uid, b.st_gid, stat.S_IMODE(b.st_mode)) ==
            (metadata.st_uid, metadata.st_gid, stat.S_IMODE(metadata.st_mode)))
    fd, name = tempfile.mkstemp(prefix='.staging.env.custodia.', dir=TARGET.parent)
    try:
        with os.fdopen(fd, 'wb') as f:
            os.fchown(f.fileno(), metadata.st_uid, metadata.st_gid)
            os.fchmod(f.fileno(), stat.S_IMODE(metadata.st_mode))
            f.write(updated)
            f.flush()
            os.fsync(f.fileno())
        require(TARGET.read_bytes() == original)
        stopped()
        os.replace(name, TARGET)
        directory_fd = os.open(TARGET.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
        a = TARGET.stat()
        require(TARGET.read_bytes() == updated and (a.st_uid, a.st_gid, stat.S_IMODE(a.st_mode)) ==
                (metadata.st_uid, metadata.st_gid, stat.S_IMODE(metadata.st_mode)))
        stopped()
        return str(backup)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def main():
    original, metadata = preflight()
    user = pwd.getpwnam('carolina')
    # Un directorio nuevo impide reutilizar sockets de una ejecución anterior.
    SOCKET_DIR.mkdir(mode=0o711)
    try:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as server:
            server.bind(str(SOCKET_PATH))
            os.chown(SOCKET_PATH, 0, user.pw_gid)
            os.chmod(SOCKET_PATH, 0o620)
            server.listen(1)
            server.settimeout(600)
            emit(estado='ESPERANDO_CUSTODIA', minutos=10, secretos_mostrados=False)
            connection, _ = server.accept()
            with connection:
                _, uid, _ = struct.unpack('3i', connection.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
                require(uid == user.pw_uid)
                connection.settimeout(30)
                received = bytearray()
                while True:
                    chunk = connection.recv(4096)
                    if not chunk:
                        break
                    received.extend(chunk)
                    require(len(received) <= 32768)
                payload = json.loads(received)
                updated = build_config(original, payload)
                backup = install(original, metadata, updated)
                emit(estado='INSTALACION_VERIFICADA', respaldo=backup, respaldo_identico=True,
                     propietario_conservado=True, permisos='0600', remitente_oficial=True,
                     envio_real_deshabilitado=True, variables_reconocidas_por_jar=True,
                     otras_variables_conservadas=True, ambos_servicios_detenidos=True,
                     mysql_sin_acceso=True, secretos_mostrados=False)
                connection.sendall(b'INSTALACION_VERIFICADA\n')
                received[:] = b'\0' * len(received)
    finally:
        if SOCKET_PATH.exists():
            SOCKET_PATH.unlink()
        SOCKET_DIR.rmdir()


def status():
    require(os.geteuid() == 0)
    stopped()
    raw = TARGET.read_bytes()
    env = assignments(raw)
    backups = sorted((TARGET.parent / 'custodia-backups').glob('staging.env.*.bak'))
    emit(estado='LECTURA_DIAGNOSTICA', variables_gmail_presentes={k: bool(env.get(k)) for k in PRIVATE_KEYS + tuple(FIXED)},
         envio_real_false=env.get('GMAIL_ENVIO_REAL_HABILITADO') == 'false',
         sobrescrituras_presentes=[k for k,v in env.items() if v and (k in {'SPRING_APPLICATION_JSON','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','JAVA_OPTS'} or k.startswith(('DIMABUG_GMAIL_','SPRING_CONFIG_')))],
         respaldos_encontrados=len(backups),
         ultimo_respaldo_coincide=(backups[-1].read_bytes() == raw) if backups else None,
         propietario_root=TARGET.stat().st_uid == 0, grupo_root=TARGET.stat().st_gid == 0,
         permisos_0600=stat.S_IMODE(TARGET.stat().st_mode) == 0o600)


if __name__ == '__main__':
    try:
        if len(sys.argv) == 2 and sys.argv[1] == '--status':
            status()
        else:
            main()
    except Exception as exc:
        emit(estado='INSTALACION_NO_COMPLETADA', categoria=type(exc).__name__,
             linea_control=traceback.extract_tb(exc.__traceback__)[-2].lineno if len(traceback.extract_tb(exc.__traceback__)) > 1 else None,
             secretos_mostrados=False)
        raise SystemExit(1)
