"""Inspección de solo lectura. No imprime variables de entorno ni argumentos Java."""
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import stat
import subprocess
import zipfile


def report(data):
    print(json.dumps(data, ensure_ascii=True))


def inspect(name):
    unit = 'dimabug@' + name + '.service'
    info = subprocess.check_output(['systemctl', 'show', unit, '-p', 'ActiveState',
        '-p', 'MainPID', '-p', 'EnvironmentFiles', '-p', 'FragmentPath',
        '-p', 'DropInPaths'], text=True)
    expected = '/etc/dimabug/' + name + '.env'
    if 'ActiveState=inactive\n' not in info or 'MainPID=0\n' not in info:
        raise RuntimeError('Servicio no detenido')
    if 'EnvironmentFiles=' + expected + ' (ignore_errors=no)\n' not in info:
        raise RuntimeError('Fuente inesperada')
    path = Path(expected)
    if not path.exists():
        report({'unidad': unit, 'archivo_ausente': expected,
            'detenida': True, 'configuracion_no_creada': True})
        return
    meta = path.lstat()
    if path.is_symlink() or not stat.S_ISREG(meta.st_mode):
        raise RuntimeError('Archivo inesperado')
    env = {}
    duplicates = []
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if not re.match(r'^[A-Za-z_][A-Za-z0-9_]*=', line):
            raise RuntimeError('Formato requiere revision privada')
        key, value = line.split('=', 1)
        if key in env:
            duplicates.append(key)
        # Solo formatos simples; no ejecutar ni hacer source del archivo.
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        env[key] = value
    keys = ['GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET', 'GMAIL_TOKEN_ENCRYPTION_KEY',
        'GMAIL_SENDER_EMAIL', 'GMAIL_OAUTH_HABILITADO', 'GMAIL_ENVIO_REAL_HABILITADO',
        'GMAIL_OAUTH_REDIRECT_URI', 'GMAIL_FRONTEND_URL']
    report({'unidad': unit, 'detenida': True, 'archivo': expected,
        'modo': oct(stat.S_IMODE(meta.st_mode)), 'uid': meta.st_uid, 'gid': meta.st_gid,
        'variables_presentes': {k: bool(env.get(k)) for k in keys},
        'duplicadas': duplicates,
        'db_es_staging': bool(re.search(r'/dima_bug_staging(?:\?|$)', env.get('DB_URL', ''))),
        'perfil_incluye_staging': 'staging' in env.get('SPRING_PROFILES_ACTIVE', '').split(','),
        'perfil_incluye_produccion': any(p in env.get('SPRING_PROFILES_ACTIVE', '').split(',') for p in ['prod','production']),
        'envio_real_false': env.get('GMAIL_ENVIO_REAL_HABILITADO', '').lower() == 'false',
        'remitente_oficial': env.get('GMAIL_SENDER_EMAIL') == 'dimabug507@gmail.com',
        'callback_https_staging': env.get('GMAIL_OAUTH_REDIRECT_URI') == 'https://staging.dimabug.casol.cl/login/oauth2/code/google',
        'frontend_https_staging': env.get('GMAIL_FRONTEND_URL') == 'https://staging.dimabug.casol.cl/admin/gmail'})
    # Identificar solo JARs por rutas absolutas en las fuentes, sin imprimir comandos.
    sources = subprocess.check_output(['systemctl', 'cat', unit], text=True)
    jars = set(re.findall(r'(/[A-Za-z0-9_./@%+\-]+\.jar)', sources))
    for raw in sorted(jars):
        jar = Path(raw.replace('%i', name))
        if not jar.is_file():
            report({'unidad': unit, 'jar': str(jar), 'existe': False})
            continue
        with zipfile.ZipFile(jar) as z:
            members = set(z.namelist())
            gmail = z.read('BOOT-INF/classes/cl/casol/backend/correo/infrastructure/gmail/GmailConfig.class') if 'BOOT-INF/classes/cl/casol/backend/correo/infrastructure/gmail/GmailConfig.class' in members else b''
            oauth = z.read('BOOT-INF/classes/cl/casol/backend/correo/application/service/GmailOAuthService.class') if 'BOOT-INF/classes/cl/casol/backend/correo/application/service/GmailOAuthService.class' in members else b''
            report({'unidad': unit, 'indicadores_estaticos_sin_ejecutar_jar': {
                'propiedad_envio_real': b'dimabug.gmail.envio-real-habilitado' in gmail,
                'propiedad_clave_cifrado': b'dimabug.gmail.clave-cifrado' in gmail,
                'metodo_envio_autenticacion': b'enviarAutenticacion' in oauth}})
            report({'unidad': unit, 'jar': str(jar),
                'sha256': hashlib.file_digest(jar.open('rb'), 'sha256').hexdigest(),
                'gmail_config': 'BOOT-INF/classes/cl/casol/backend/correo/infrastructure/gmail/GmailConfig.class' in members,
                'transporte_central': 'BOOT-INF/classes/cl/casol/backend/correo/infrastructure/gmail/CorreoGmailTransporte.class' in members})
    report({'unidad': unit, 'jars_detectados': len(jars),
        'sobrescrituras_requieren_revision': bool(re.search(r'(?:-D|--)(?:dimabug\.|spring\.|gmail\.)|^Environment=', sources, re.M))})


if __name__ == '__main__':
    try:
        for instance in ['staging', 'staging7']:
            inspect(instance)
    except Exception as exc:
        report({'verificacion_completa': False, 'categoria': type(exc).__name__})
        raise SystemExit(1)
