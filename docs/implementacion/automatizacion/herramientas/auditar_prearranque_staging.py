"""Auditoría de solo lectura: no ejecuta JAR, no escribe configuración ni accede a MySQL."""
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
from instalar_custodia_staging import preflight, assignments, build_config, PRIVATE_KEYS, JAR


def emit(**data):
    print(json.dumps(data), flush=True)


def flatten(node, prefix=''):
    result = {}
    for key, value in node.items():
        path = (prefix + '.' + key).strip('.').lower().replace('-', '').replace('_', '')
        if isinstance(value, dict):
            result.update(flatten(value, path))
        else:
            result[path] = value
    return result


def main():
    original, st = preflight()
    env = assignments(original)
    build_config(original, {k: env[k] for k in PRIVATE_KEYS})
    js = flatten(json.loads(env['SPRING_APPLICATION_JSON'])) if env.get('SPRING_APPLICATION_JSON') else {}
    fields = ['EnvironmentFiles', 'User', 'UnsetEnvironment', 'PassEnvironment',
              'ExecStartPre', 'ExecStartPost', 'RootDirectory', 'RootImage']
    raw = subprocess.check_output(['systemctl', 'show', 'dimabug@staging.service'] + ['-p' + x for x in fields], text=True)
    properties = dict(line.split('=', 1) for line in raw.splitlines() if '=' in line)
    exact_file = properties.get('EnvironmentFiles') == '/etc/dimabug/staging.env (ignore_errors=no)'
    emit(tipo='systemd', environmentfile_exacto=exact_file,
         archivo_legible_por_root=os.access('/etc/dimabug/staging.env', os.R_OK),
         pid1_root=Path('/proc/1').stat().st_uid == 0,
         propietario_root=st.st_uid == 0, grupo_root=st.st_gid == 0, permisos_0600=(st.st_mode & 0o777) == 0o600,
         unset_environment_presente=bool(properties.get('UnsetEnvironment')),
         pass_environment_presente=bool(properties.get('PassEnvironment')),
         comandos_prearranque_presentes=bool(properties.get('ExecStartPre')),
         comandos_postarranque_presentes=bool(properties.get('ExecStartPost')),
         raiz_alternativa=bool(properties.get('RootDirectory') or properties.get('RootImage')))
    mode = js.get('dimabug.automatizacion.modo', env.get('DIMABUG_AUTOMATIZACION_MODO'))
    if mode not in ['SIMULADO', 'SMTP', 'GMAIL_API', 'DESHABILITADO', None]:
        mode = 'OTRO_REQUIERE_REVISION'
    emit(tipo='configuracion', envio_real_false=env.get('GMAIL_ENVIO_REAL_HABILITADO') == 'false',
         json_sin_gmail=not any(k.startswith('dimabug.gmail.') for k in js),
         validacion_destino_staging=True, remitente_oficial=env['GMAIL_SENDER_EMAIL'] == 'dimabug507@gmail.com',
         modo_procedimientos_externo=mode,
         alias_envio_directos=[k for k in env if k.startswith('DIMABUG_GMAIL_')],
         smtp_credenciales_presentes=bool(env.get('SPRING_MAIL_USERNAME') or env.get('SMTP_USERNAME') or js.get('spring.mail.username')))
    candidates = []
    work = Path('/opt/dimabug/staging')
    for directory in [work, work / 'config'] + list((work / 'config').glob('*/')):
        if not directory.is_dir():
            continue
        for pattern in ['application*.properties', 'application*.yml', 'application*.yaml']:
            for path in directory.glob(pattern):
                entry = {'ruta': str(path), 'formato': path.suffix}
                if path.suffix == '.properties':
                    text = path.read_text(errors='replace')
                    entry['propiedades_relevantes'] = sorted(set(re.findall(r'^\s*((?:dimabug\.gmail\.|dimabug\.automatizacion\.|spring\.datasource\.|spring\.config\.|spring\.profiles\.)[A-Za-z0-9_.-]+)\s*[:=]', text, re.M)))
                candidates.append(entry)
    emit(tipo='configuracion_externa', archivos=candidates)
    with JAR.open('rb') as f:
        emit(tipo='jar', hash_verificado=True, sha256=hashlib.file_digest(f, 'sha256').hexdigest())
    with zipfile.ZipFile(JAR) as z:
        for name in z.namelist():
            if name.startswith('BOOT-INF/lib/') and 'spring-boot-mail-' in name:
                library = z.read(name)
                with zipfile.ZipFile(io.BytesIO(library)) as nested:
                    candidate = 'org/springframework/boot/mail/autoconfigure/MailSenderAutoConfiguration.class'
                    emit(tipo='aislamiento_smtp', biblioteca=name,
                         sha256=hashlib.sha256(library).hexdigest(),
                         autoconfiguracion_excluible_presente=candidate in nested.namelist())
            if not name.startswith('BOOT-INF/classes/cl/casol/backend/') or not name.endswith('.class'):
                continue
            data = z.read(name)
            indicators = [s for s in ['Scheduled', 'ApplicationRunner', 'CommandLineRunner', 'PostConstruct',
                'ApplicationReadyEvent', 'JavaMailSender', 'JavaMailSenderImpl', 'GmailOAuthService', 'EventListener',
                'AutomatizarEvidenciaService', 'EnableScheduling', 'CorreoSimuladoAdapter',
                'AuthController', 'AutenticarUsuarioService', 'CodigoAccesoEntity', 'CodigoAccesoRepositoryAdapter'] if s.encode() in data]
            if indicators:
                emit(tipo='clase_relevante', clase=name, indicadores=indicators, sha256=hashlib.sha256(data).hexdigest())
    emit(tipo='fin', solo_lectura=True, secretos_mostrados=False)


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        emit(tipo='incompleto', categoria=type(exc).__name__, secretos_mostrados=False)
        raise SystemExit(1)
