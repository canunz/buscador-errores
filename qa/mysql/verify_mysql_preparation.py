"""Solo validación de archivos e inventario opcional; no crea ni arranca recursos."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / 'qa/mysql/compose.qa-local.json'
IMAGE_PATTERN = r'mysql:8\.(?:0|4)\.\d+(?:-[a-z0-9.-]+)?@sha256:[a-f0-9]{64}'


def validate(data):
    assert data['name'] == 'dimabug-qa-local-13318'
    assert set(data['services']) == {'mysql'}
    service = data['services']['mysql']
    assert service['container_name'] == 'dimabug-qa-mvp-13318-mysql'
    assert service['ports'] == ['127.0.0.1:13318:3306']
    assert service['pull_policy'] == 'never' and service['restart'] == 'no'
    assert service['cpus'] == 2.0 and service['mem_limit'] == '4g'
    assert service['image'] == 'mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'
    assert service['platform'] == 'linux/amd64'
    assert service['environment'] == {'MYSQL_ROOT_PASSWORD_FILE': '/run/secrets/mysql_root_password', 'MYSQL_ROOT_HOST': 'localhost'}
    assert service['secrets'] == ['mysql_root_password']
    assert service['volumes'] == ['mysql-data:/var/lib/mysql']
    assert service['networks'] == ['qa-internal']
    assert not any(k in service for k in ['privileged', 'network_mode', 'env_file', 'build', 'entrypoint', 'extra_hosts'])
    assert data['networks'] == {'qa-internal': {'name': 'dimabug-qa-mvp-13318-net', 'internal': True}}
    assert data['volumes'] == {'mysql-data': {'name': 'dimabug-qa-mvp-13318-mysql-data'}}
    assert data['secrets'] == {'mysql_root_password': {'file': '../runtime/secrets/mysql-root.txt'}}
    assert '--event-scheduler=OFF' in service['command'] and '--local-infile=OFF' in service['command']
    profile = (ROOT / 'dima-bug-backend/src/main/resources/application-qa-local.properties').read_text(encoding='utf-8')
    assert 'jdbc:mysql://127.0.0.1:13318/dima_bug_qa_mvp?' in profile
    assert '13316' not in profile and '13317' not in profile


def docker(*args):
    result = subprocess.run(['docker', *args], capture_output=True, text=True, timeout=15)
    if result.returncode != 0:
        raise RuntimeError('Consulta Docker no completada; salida no expuesta')
    return result.stdout.strip()


def inspect(data):
    # Solo contexto local. No imprimir datos de entorno, logs ni contraseñas.
    context = docker('context', 'show')
    definition = json.loads(docker('context', 'inspect', context))[0]
    endpoint = os.environ.get('DOCKER_HOST') or definition['Endpoints']['docker']['Host']
    local = endpoint.startswith(('npipe://', 'unix://')) or (endpoint.startswith('tcp://') and urlsplit(endpoint).hostname in ['127.0.0.1', 'localhost', '::1'])
    if not local:
        raise RuntimeError('No se consulta un motor remoto')
    info = json.loads(docker('--context', context, 'version', '--format', '{{json .Server}}'))
    assert info['Os'] == 'linux'
    containers = docker('--context', context, 'ps', '-a', '--format', '{{.Names}}').splitlines()
    volumes = docker('--context', context, 'volume', 'ls', '--format', '{{.Name}}').splitlines()
    networks = docker('--context', context, 'network', 'ls', '--format', '{{.Name}}').splitlines()
    collisions = []
    for kind, name, names in [('container', data['services']['mysql']['container_name'], containers),
                              ('volume', data['volumes']['mysql-data']['name'], volumes),
                              ('network', data['networks']['qa-internal']['name'], networks)]:
        if name in names:
            collisions.append({'type': kind, 'name': name})
    import psutil
    busy = any(c.status == psutil.CONN_LISTEN and c.laddr.port == 13318 for c in psutil.net_connections(kind='tcp'))
    # Un contenedor detenido puede tener reservado el puerto sin escucha actual.
    ids = docker('--context', context, 'ps', '-a', '--format', '{{.ID}}').splitlines()
    configured = False
    for container in ids:
        bindings = json.loads(docker('--context', context, 'inspect', '--format', '{{json .HostConfig.PortBindings}}', container)) or {}
        configured = configured or any(str(binding.get('HostPort')) == '13318'
            for group in bindings.values() for binding in (group or []))
    print(json.dumps({'status': 'MYSQL_QA_READ_ONLY_INVENTORY', 'engine_local': True,
        'engine_version': info.get('Version'), 'resource_name_collisions': collisions,
        'port_13318_listening': busy, 'port_13318_configured_in_existing_container': configured,
        'does_not_authorize_creation': True}))
    if collisions or busy or configured:
        raise RuntimeError('Hay recursos existentes; no reutilizar ni sobrescribir')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--inspect-docker', action='store_true')
    args = parser.parse_args()
    data = json.loads(COMPOSE.read_text(encoding='utf-8'))
    validate(data)
    print(json.dumps({'status': 'MYSQL_QA_DEFINITION_OK', 'port': 13318,
        'image_pending_approval': False,
        'root_secret_file_present': (ROOT / 'qa/runtime/secrets/mysql-root.txt').is_file(),
        'no_automatic_app_schema_or_app_user': True, 'resources_created': 0, 'sql_executed': 0}))
    if args.inspect_docker:
        inspect(data)


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print(json.dumps({'status': 'MYSQL_QA_NOT_CONFIRMED', 'category': type(exc).__name__}))
        raise SystemExit(1)
