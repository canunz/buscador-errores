"""Verificaciones de archivos; sin arranque, compilación, SQL, secretos o red."""
import importlib.util
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def main():
    baseline = json.loads((ROOT / 'qa/baseline-pendientes.json').read_text(encoding='utf-8'))
    assert len(baseline['archivos']) == 120
    assert len({x['ruta'] for x in baseline['archivos']}) == 120
    assert {x['categoria'] for x in baseline['archivos']} == {'funcionalidades_mvp', 'pruebas', 'documentacion', 'herramientas_operativas'}
    angular = json.loads((ROOT / 'frontend-dimabug/angular.json').read_text(encoding='utf-8'))
    architecture = angular['projects']['buscador-errores']['architect']
    assert architecture['build']['defaultConfiguration'] == 'production'
    assert architecture['serve']['defaultConfiguration'] == 'development'
    assert architecture['serve']['options']['proxyConfig'] == 'proxy.conf.json'
    serve = architecture['serve']['configurations']['qa-local']
    assert serve['host'] == '127.0.0.1' and serve['port'] == 14200
    assert serve['buildTarget'] == 'buscador-errores:build:qa-local'
    proxy = json.loads((ROOT / 'frontend-dimabug' / serve['proxyConfig']).read_text())
    assert set(proxy) == {'/api'} and proxy['/api']['target'] == 'http://127.0.0.1:19090'
    # Contrastar nombres de opciones con los schemas ya instalados, sin ejecutar Angular.
    dev_schema = ROOT / 'frontend-dimabug/node_modules/@angular-devkit/build-angular/src/builders/dev-server/schema.json'
    if dev_schema.exists():
        assert set(serve).issubset(json.loads(dev_schema.read_text())['properties'])
    profile_path = ROOT / 'dima-bug-backend/src/main/resources/application-qa-local.properties'
    props = {}
    for line in profile_path.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            assert key not in props
            props[key] = value
    expected = {'server.address': '127.0.0.1', 'server.port': '19090',
        'spring.datasource.username': 'dimabug_qa_app', 'spring.jpa.hibernate.ddl-auto': 'validate',
        'spring.sql.init.mode': 'never', 'dimabug.gmail.oauth-habilitado': 'false',
        'dimabug.gmail.envio-real-habilitado': 'false', 'dimabug.auth.correo.habilitado': 'false',
        'dimabug.automatizacion.seguimiento-habilitado': 'false', 'dimabug.automatizacion.modo': 'DESHABILITADO',
        'spring.autoconfigure.exclude': 'org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration'}
    for key, value in expected.items():
        assert props[key] == value
    assert props['spring.datasource.url'].startswith('jdbc:mysql://127.0.0.1:13318/dima_bug_qa_mvp?')
    assert props['spring.datasource.password'] == '${QA_DB_PASSWORD}' and props['jwt.secret'] == '${QA_JWT_SECRET}'
    assert all(props[k] == '' for k in ['dimabug.gmail.client-id', 'dimabug.gmail.client-secret', 'dimabug.gmail.clave-cifrado', 'spring.mail.password'])
    assert not any('staging' in v.lower() or 'production' in v.lower() for v in props.values())
    spec = importlib.util.spec_from_file_location('qa_manifest', ROOT / 'qa/qa_manifest.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    records = module.records()
    before = {x['path']: x['sha256'] for x in records}
    # Ningún cambio original se sobrescribe: solo angular.json recibió configuración aditiva.
    for item in baseline['archivos']:
        if item['ruta'] != 'frontend-dimabug/angular.json':
            assert before[item['ruta']] == item['sha256_preparacion_inicial'], item['ruta']
    sample = [{'path': 'a', 'sha256': '0' * 64}]
    altered = [{'path': 'a', 'sha256': '1' * 64}]
    assert module.fingerprint(sample) != module.fingerprint(altered)
    assert module.changed(sample, altered) == ['a']
    assert module.changed(sample, []) == ['a']
    assert not any(x['path'].startswith(('.dimabug-private/', 'qa/runtime/', 'qa/artifacts/')) for x in records)
    mysql_spec = importlib.util.spec_from_file_location('verify_mysql', ROOT / 'qa/mysql/verify_mysql_preparation.py')
    mysql_module = importlib.util.module_from_spec(mysql_spec)
    mysql_spec.loader.exec_module(mysql_module)
    mysql_module.validate(json.loads((ROOT / 'qa/mysql/compose.qa-local.json').read_text(encoding='utf-8')))
    print(json.dumps({'status': 'STATIC_QA_CHECKS_OK', 'baseline_files': 120,
        'existing_functional_files_preserved': True, 'external_processes_started': 0,
        'sql_executed': 0, 'credentials_generated': 0}))


if __name__ == '__main__':
    main()
