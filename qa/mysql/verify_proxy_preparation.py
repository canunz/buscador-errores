"""Static checks by default; optional runtime reads, no Docker mutations or SQL."""
import argparse
import copy
import json
from pathlib import Path
import socket
import subprocess

HERE = Path(__file__).resolve().parent
PROXY_IMAGE = 'haproxy:3.2.26-alpine3.24@sha256:5378234d19457d645ed6d5a0ac994ef196614f9a2cabda8f6ccd6ac8815f9098'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate(data):
    base = json.loads((HERE / 'compose.qa-local.json').read_text())
    require(data['name'] == base['name'], 'Project mismatch')
    require(set(data['services']) == {'mysql', 'mysql-proxy'}, 'Unexpected service')
    mysql = copy.deepcopy(base['services']['mysql'])
    mysql.pop('ports')
    require(data['services']['mysql'] == mysql, 'MySQL must preserve baseline except removal of direct port')
    require(data['secrets'] == base['secrets'], 'Custody changed')
    require(data['volumes'] == {'mysql-data': {'name': 'dimabug-qa-mvp-13318-mysql-data', 'external': True}}, 'Existing volume required')
    require(data['networks'] == {
        'qa-internal': {'name': 'dimabug-qa-mvp-13318-net', 'external': True},
        'qa-access': {'name': 'dimabug-qa-mvp-13318-access', 'internal': False}}, 'Network mismatch')
    proxy = data['services']['mysql-proxy']
    require(set(proxy) == {'image', 'platform', 'pull_policy', 'container_name', 'restart', 'cpus', 'mem_limit',
        'pids_limit', 'user', 'read_only', 'cap_drop', 'security_opt', 'sysctls', 'ports', 'networks', 'volumes',
        'entrypoint', 'command', 'depends_on', 'logging'}, 'Unexpected proxy mechanism')
    expected = {'image': PROXY_IMAGE, 'platform': 'linux/amd64', 'pull_policy': 'never',
        'container_name': 'dimabug-qa-mvp-13318-proxy', 'restart': 'no', 'user': '65534:65534',
        'read_only': True, 'cap_drop': ['ALL'], 'security_opt': ['no-new-privileges:true'],
        'sysctls': {'net.ipv4.ip_forward': '0'}, 'ports': ['127.0.0.1:13318:13318'],
        'networks': ['qa-internal', 'qa-access'],
        'volumes': ['./haproxy.qa-local.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro'],
        'entrypoint': ['haproxy'], 'command': ['-db', '-f', '/usr/local/etc/haproxy/haproxy.cfg']}
    for key, value in expected.items():
        require(proxy[key] == value, 'Proxy security/configuration mismatch: ' + key)
    cfg = (HERE / 'haproxy.qa-local.cfg').read_text()
    # Any change requires reviewing the complete forwarding configuration, not just its destination.
    import hashlib
    require(hashlib.sha256(cfg.encode()).hexdigest() == CONFIG_SHA256, 'Forwarding configuration changed')


def docker(*args):
    result = subprocess.run(['docker', '--context', 'desktop-linux', *args], capture_output=True, text=True, timeout=15)
    require(result.returncode == 0, 'Docker read failed; output suppressed')
    return result.stdout


def classify_greeting(greeting):
    require(len(greeting) > 6, 'Truncated MySQL response')
    if greeting[4] == 255:
        require(int.from_bytes(greeting[5:7], 'little') == 1130, 'Unexpected MySQL error')
        return 'AUTH_PENDING'
    require(greeting[4] == 10, 'No MySQL protocol greeting')
    require(greeting[5:].split(b'\0')[0] == b'8.0.46', 'Server version mismatch')
    return 'GREETING_VERIFIED'


def runtime():
    context = json.loads(docker('context', 'inspect', 'desktop-linux'))[0]
    require(context['Endpoints']['docker']['Host'].startswith('npipe://'), 'Local Windows context required')
    def inspect(kind, name, field):
        args = ['inspect'] if kind == 'container' else [kind, 'inspect']
        return json.loads(docker(*args, '--format', '{{json ' + field + '}}', name))
    mysql = 'dimabug-qa-mvp-13318-mysql'
    proxy = 'dimabug-qa-mvp-13318-proxy'
    require(inspect('network', 'dimabug-qa-mvp-13318-net', '.Internal') is True, 'MySQL network not internal')
    require(inspect('network', 'dimabug-qa-mvp-13318-access', '.Internal') is False, 'Access network mismatch')
    require(set(inspect('container', mysql, '.NetworkSettings.Networks')) == {'dimabug-qa-mvp-13318-net'}, 'MySQL has extra network')
    require(not inspect('container', mysql, '.HostConfig.PortBindings'), 'MySQL directly published')
    require(set(inspect('container', proxy, '.NetworkSettings.Networks')) == {'dimabug-qa-mvp-13318-net', 'dimabug-qa-mvp-13318-access'}, 'Proxy networks mismatch')
    require(inspect('container', proxy, '.Config.Image') == PROXY_IMAGE, 'Proxy image mismatch')
    require(inspect('container', proxy, '.Config.User') == '65534:65534', 'Proxy runs with unexpected user')
    require(inspect('container', proxy, '.HostConfig.ReadonlyRootfs') is True, 'Proxy filesystem writable')
    require(inspect('container', proxy, '.HostConfig.Privileged') is False, 'Privileged proxy')
    require(inspect('container', proxy, '.HostConfig.CapDrop') == ['ALL'], 'Capabilities not dropped')
    require('no-new-privileges:true' in inspect('container', proxy, '.HostConfig.SecurityOpt'), 'Privilege escalation allowed')
    require(inspect('container', proxy, '.HostConfig.Sysctls').get('net.ipv4.ip_forward') == '0', 'IP forwarding enabled')
    require(inspect('container', proxy, '.NetworkSettings.Ports') == {'13318/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '13318'}]}, 'Effective publication mismatch')
    mounts = inspect('container', proxy, '.Mounts')
    require(len(mounts) == 1, 'Unexpected proxy mount count')
    for mount in mounts:
        require(mount['Destination'] == '/usr/local/etc/haproxy/haproxy.cfg' and mount['RW'] is False, 'Unexpected proxy mount')
    with socket.create_connection(('127.0.0.1', 13318), timeout=3) as connection:
        connection.settimeout(3)
        greeting = connection.recv(512)
    if classify_greeting(greeting) == 'AUTH_PENDING':
        print(json.dumps({'status': 'PROXY_TRANSPORT_OK_AUTH_PENDING', 'mysql_error': 1130,
            'host_not_authorized': True, 'sql_executed': False, 'credentials_read': False,
            'server_version_via_greeting_verified': False, 'functional_authentication_verified': False,
            'outbound_isolation_test_still_required': True}))
        return
    print(json.dumps({'status': 'PROXY_RUNTIME_CHECKS_OK', 'sql_executed': False,
        'credentials_read': False, 'outbound_isolation_test_still_required': True}))


CONFIG_SHA256 = 'c4540194de162ab1563eb5486ce2a3be74914c69fa2fd70699492280a51dd48e'

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', action='store_true', help='Read-only checks after separately approved activation')
    args = parser.parse_args()
    try:
        validate(json.loads((HERE / 'compose.qa-proxy.json').read_text()))
        print(json.dumps({'status': 'PROXY_STATIC_CHECKS_OK', 'resources_created': False}))
        if args.runtime:
            runtime()
    except Exception as exc:
        print(json.dumps({'status': 'PROXY_NOT_VERIFIED', 'category': type(exc).__name__}))
        raise SystemExit(1)
