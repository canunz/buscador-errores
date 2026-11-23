"""Inspección estática y de registros de DimaBug. No inicia el JAR ni modifica archivos."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


def emit(kind, **values):
    print(json.dumps(dict(tipo=kind, **values), ensure_ascii=True))


def run(args):
    p = subprocess.run(args, capture_output=True, text=True)
    return p.stdout if p.returncode == 0 else ''


def safe_properties(text):
    result = {}
    for line in text.splitlines():
        if '=' in line and not line.strip().startswith('#'):
            key, value = line.split('=', 1)
            result[key.strip()] = value.strip().strip('"').strip("'")
    return result


def inspect(name):
    unit = 'dimabug@' + name + '.service'
    state = run(['systemctl', 'show', unit, '-pActiveState', '-pMainPID'])
    emit('estado', unidad=unit, detenida='ActiveState=inactive\n' in state and 'MainPID=0\n' in state)
    source = run(['systemctl', 'cat', unit])
    # Rutas y opciones permitidas; nunca imprimir Environment ni ExecStart completo.
    for key in ['WorkingDirectory', 'EnvironmentFile']:
        matches = re.findall(r'^' + key + r'=([^\n]+)', source, re.M)
        emit('rutas_unidad', unidad=unit, campo=key, rutas=matches)
    for pattern, label in [(r'--server.port[= ](\d+)', 'puerto_argumento'),
                            (r'-Dserver.port=(\d+)', 'puerto_jvm')]:
        emit('argumentos_permitidos', unidad=unit, campo=label, valores=re.findall(pattern, source))
    envfile = Path('/etc/dimabug/' + name + '.env')
    env = safe_properties(envfile.read_text()) if envfile.exists() else {}
    emit('entorno', unidad=unit, archivo_existe=envfile.exists(),
         puerto=env.get('SERVER_PORT') if env.get('SERVER_PORT', '').isdigit() else None,
         perfil_staging='staging' in env.get('SPRING_PROFILES_ACTIVE', '').split(','),
         base_staging=bool(re.search(r'/dima_bug_staging(?:\?|$)', env.get('DB_URL', ''))))
    directory = Path('/opt/dimabug/' + name)
    emit('directorio', unidad=unit, ruta=str(directory), existe=directory.exists())
    jars = {Path(x.replace('%i', name)) for x in re.findall(r'(/[A-Za-z0-9_./@%+\-]+\.jar)', source)}
    # La unidad plantilla usa %i; no inferir que un archivo histórico fue ejecutado.
    for jar in sorted(jars):
        emit('jar_ruta', unidad=unit, ruta=str(jar), existe=jar.is_file())
        if not jar.is_file():
            continue
        with jar.open('rb') as f:
            digest = hashlib.file_digest(f, 'sha256').hexdigest()
        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
            manifest = safe_properties(z.read('META-INF/MANIFEST.MF').decode().replace(': ', '='))
            version = manifest.get('Implementation-Version', '')
            emit('jar_identidad', unidad=unit, sha256=digest,
                 version=version if re.fullmatch(r'[A-Za-z0-9.+_\-]{1,80}', version) else None)
            for member in names:
                if member.endswith(('build-info.properties', 'git.properties')):
                    p = safe_properties(z.read(member).decode(errors='replace'))
                    selected = {k: v for k, v in p.items() if k in ['build.version', 'build.time', 'git.commit.id', 'git.commit.id.abbrev', 'git.commit.time'] and re.fullmatch(r'[A-Za-z0-9.:+_\-TZ ]{1,100}', v)}
                    if selected:
                        emit('build', unidad=unit, archivo=member, datos=selected)
            for member in names:
                if re.fullmatch(r'BOOT-INF/classes/application[^/]*\.properties', member):
                    p = safe_properties(z.read(member).decode(errors='replace'))
                    selected = {}
                    for key, value in p.items():
                        if key in ['server.port', 'server.servlet.context-path', 'management.server.port', 'dimabug.auth.codigo.usar-gmail-api', 'dimabug.gmail.oauth-habilitado', 'dimabug.gmail.envio-real-habilitado', 'dimabug.automatizacion.modo']:
                            if re.fullmatch(r'[A-Za-z0-9_.$:{}\-/]+', value):
                                selected[key] = value
                    emit('config_empaquetada', unidad=unit, archivo=member, opciones=selected)
            classes = ['correo/infrastructure/gmail/GmailConfig', 'correo/infrastructure/gmail/CifradoGmailAes',
                       'correo/application/service/GmailOAuthService',
                       'identidad/infrastructure/correo/EnvioCodigoAccesoCorreoAdapter',
                       'identidad/application/service/DespacharCodigosService',
                       'ejecucion/infrastructure/automatizacion/SeguimientoEvidenciaWorker']
            javap = shutil.which('javap')
            emit('herramienta', unidad=unit, javap_disponible=bool(javap))
            for cls in classes:
                member = 'BOOT-INF/classes/cl/casol/backend/' + cls + '.class'
                if member not in names:
                    emit('clase', unidad=unit, clase=cls, presente=False)
                    continue
                data = z.read(member)
                emit('clase', unidad=unit, clase=cls, presente=True, sha256=hashlib.sha256(data).hexdigest())
                if javap:
                    output = run([javap, '-c', '-p', 'jar:' + jar.resolve().as_uri() + '!/' + member])
                    # Solo firmas y bytecode: ocultar constantes de cadenas y comentarios ajenos a llamadas/campos.
                    lines = []
                    for line in output.splitlines():
                        if re.match(r'^  (?:public|private|protected|static|boolean|java\.|void)', line) and '(' in line:
                            lines.append(line.strip())
                        elif re.match(r'^\s+\d+:', line):
                            if '// String ' in line:
                                literal = line.split('// String ', 1)[1]
                                allowed = ['AES/GCM/NoPadding', 'AES', 'gmail:access', 'gmail:refresh', 'v1.', 'GMAIL_API']
                                line = line.split('//', 1)[0] + ('// String ' + literal if literal in allowed else '// String [omitida]')
                            elif '//' in line and not re.search(r'// (?:Method|InterfaceMethod|Field|class|InvokeDynamic)', line):
                                line = line.split('//', 1)[0]
                            lines.append(line.strip())
                    emit('bytecode_filtrado', unidad=unit, clase=cls, lineas=lines)
    journal = run(['journalctl', '-u', unit, '-n', '1500', '--no-pager', '-o', 'cat'])
    versions = re.findall(r'Starting BackendApplication v([A-Za-z0-9.+_\-]+)', journal)
    ports = re.findall(r'Tomcat started on port(?:\(s\))?[: ]+(\d+)', journal)
    emit('historial_acotado', unidad=unit, ultimas_versiones=versions[-5:], ultimos_puertos=ports[-5:], limite_lineas=1500)


if __name__ == '__main__':
    for name in ['staging', 'staging7']:
        try:
            inspect(name)
        except Exception as e:
            emit('incompleto', unidad=name, categoria=type(e).__name__)
    # Solo rutas/proxies de archivos nginx que contengan referencias explícitas a DimaBug.
    for directory in ['/etc/nginx/sites-enabled', '/etc/nginx/conf.d']:
        for path in Path(directory).glob('*'):
            if not path.is_file():
                continue
            text = path.read_text(errors='replace')
            if 'dimabug' not in text.lower():
                continue
            selected = []
            for line in text.splitlines():
                value = line.strip()
                if re.match(r'^(?:server_name|listen|location|root|alias|proxy_pass)\s', value) and not re.search(r'://[^/\s]*@|\$|\?', value):
                    selected.append(value)
            emit('nginx_rutas', archivo=str(path), directivas=selected)
