"""Verificación pasiva del arranque. Sin correos, peticiones de negocio ni MySQL."""
import hashlib
import json
from pathlib import Path
import re
import socket
import subprocess
from instalar_custodia_staging import assignments, PRIVATE_KEYS, JAR, JAR_SHA
from aislar_staging import EXCLUSION, exclusions


def emit(**data):
    print(json.dumps(data), flush=True)


def show(unit, keys):
    raw = subprocess.check_output(['systemctl', 'show', unit] + ['-p' + k for k in keys], text=True)
    return dict(line.split('=', 1) for line in raw.splitlines() if '=' in line)


def main():
    unit = 'dimabug@staging.service'
    info = show(unit, ['ActiveState', 'SubState', 'MainPID', 'InvocationID', 'ExecMainStatus', 'NRestarts'])
    emit(tipo='servicio', activo=info['ActiveState'] == 'active', ejecutandose=info['SubState'] == 'running',
         pid_presente=info['MainPID'] != '0', salida_principal=info['ExecMainStatus'], reinicios=info['NRestarts'])
    old = show('dimabug@staging7.service', ['ActiveState', 'SubState', 'MainPID'])
    emit(tipo='staging7', detenido=old['ActiveState'] == 'inactive' and old['MainPID'] == '0')
    if info['MainPID'] != '0':
        proc = Path('/proc') / info['MainPID']
        data = (proc / 'environ').read_bytes().split(b'\0')
        env = dict(item.decode().split('=', 1) for item in data if b'=' in item)
        saved = assignments(Path('/etc/dimabug/staging.env').read_bytes())
        args = (proc / 'cmdline').read_bytes().split(b'\0')
        emit(tipo='entorno_real', gmail_real_false=env.get('GMAIL_ENVIO_REAL_HABILITADO') == 'false',
             procedimientos_deshabilitados=env.get('DIMABUG_AUTOMATIZACION_MODO') == 'DESHABILITADO',
             smtp_exclusion_cargada=EXCLUSION in exclusions(env.get('SPRING_AUTOCONFIGURE_EXCLUDE', '')),
             custodia_igual_archivo=all(env.get(k) == saved.get(k) for k in PRIVATE_KEYS),
             json_igual_archivo=env.get('SPRING_APPLICATION_JSON') == saved.get('SPRING_APPLICATION_JSON'),
             jar_argumento_esperado=str(JAR).encode() in args,
             cwd_esperado=(proc / 'cwd').resolve() == Path('/opt/dimabug/staging'))
    with JAR.open('rb') as f:
        emit(tipo='jar', coincide_verificado=hashlib.file_digest(f, 'sha256').hexdigest() == JAR_SHA)
    invocation = info.get('InvocationID', '')
    if re.fullmatch(r'[a-fA-F0-9]{32}', invocation):
        raw = subprocess.check_output(['journalctl', '_SYSTEMD_INVOCATION_ID=' + invocation,
            '--no-pager', '-o', 'json', '-n', '1500'], text=True)
        messages = [str(json.loads(line).get('MESSAGE', '')) for line in raw.splitlines()]
        text = '\n'.join(messages)
        emit(tipo='registro_invocacion', entradas_observadas=len(messages),
             contexto_iniciado=bool(re.search(r'Started BackendApplication in ', text)),
             tomcat_8081=bool(re.search(r'Tomcat started on port(?:\(s\))?[: ]+8081\b', text)),
             fallo_arranque='APPLICATION FAILED TO START' in text or 'Application run failed' in text,
             dependencia_smtp_faltante='No qualifying bean' in text and ('MailSender' in text),
             errores_observados=sum(bool(re.search(r'\bERROR\b', m)) for m in messages),
             indicadores_envio_observados=sum(bool(re.search(r'GMAIL_ACEPTADO|[Cc]ódigo de acceso enviado|[Cc]ódigo de recuperación enviado', m)) for m in messages))
    else:
        emit(tipo='registro_invocacion', disponible=False)
    try:
        with socket.create_connection(('127.0.0.1', 8081), timeout=2):
            listening = True
    except OSError:
        listening = False
    emit(tipo='puerto', puerto_8081_acepta_tcp=listening)
    emit(tipo='fin', solo_lectura=True, sin_peticion_correo=True, sin_acceso_mysql=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        emit(tipo='incompleto', categoria=type(exc).__name__, secretos_mostrados=False)
        raise SystemExit(1)
