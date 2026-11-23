"""Aislamiento explícito solo en staging.env; sin arrancar ni acceder a MySQL."""
import hashlib
import io
import json
import re
import traceback
import zipfile
from instalar_custodia_staging import (preflight, assignments, build_config, install,
    require, emit, PRIVATE_KEYS, JAR)

EXCLUSION = 'org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration'
HASHES = {
    'ejecucion/application/service/AutomatizarEvidenciaService': '1f6ee825699375d10ae7b7ce7f0fead6684f5ff568b56085837077227ee6fc0b',
    'ejecucion/infrastructure/automatizacion/CorreoSimuladoAdapter': '0316b777bfa7782a49f0dc6c2ab699e546fa330ffece93123f55eb69a80cf6f4',
}


def flatten(node, prefix=''):
    require(isinstance(node, dict))
    result = {}
    for key, value in node.items():
        path = (prefix + '.' + key).strip('.').lower().replace('-', '').replace('_', '')
        if isinstance(value, dict):
            nested = flatten(value, path)
            require(not set(result).intersection(nested))
            result.update(nested)
        else:
            require(path not in result)
            result[path] = value
    return result


def exclusions(value):
    values = value if isinstance(value, list) else value.split(',')
    require(all(isinstance(x, str) for x in values))
    values = [x.strip() for x in values if x.strip()]
    require(all(re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$.]*', x) for x in values))
    return values


def prepare(original):
    before = assignments(original)
    require(before.get('GMAIL_ENVIO_REAL_HABILITADO') == 'false')
    js = flatten(json.loads(before['SPRING_APPLICATION_JSON'])) if before.get('SPRING_APPLICATION_JSON') else {}
    require(js.get('dimabug.automatizacion.modo', 'DESHABILITADO') == 'DESHABILITADO')
    if 'spring.autoconfigure.exclude' in js:
        require(EXCLUSION in exclusions(js['spring.autoconfigure.exclude']))
    require(not any(k != 'DIMABUG_AUTOMATIZACION_MODO' and
                    k.replace('_', '').upper() == 'DIMABUGAUTOMATIZACIONMODO' for k in before))
    require(not any(k != 'SPRING_AUTOCONFIGURE_EXCLUDE' and
                    k.replace('_', '').upper() == 'SPRINGAUTOCONFIGUREEXCLUDE' for k in before))
    previous_exclusions = exclusions(before.get('SPRING_AUTOCONFIGURE_EXCLUDE', ''))
    merged = list(previous_exclusions)
    if EXCLUSION not in merged:
        merged.append(EXCLUSION)
    updates = {'DIMABUG_AUTOMATIZACION_MODO': 'DESHABILITADO',
               'SPRING_AUTOCONFIGURE_EXCLUDE': ','.join(merged)}
    lines = [line for line in original.decode('utf-8').splitlines()
             if line.strip().split('=', 1)[0] not in updates]
    updated = ('\n'.join(lines).rstrip('\n') + '\n' +
               '\n'.join(k + '=' + v for k, v in updates.items()) + '\n').encode('utf-8')
    after = assignments(updated)
    require(all(after[k] == v for k, v in updates.items()))
    require({k: v for k, v in before.items() if k not in updates} ==
            {k: v for k, v in after.items() if k not in updates})
    require(set(previous_exclusions).issubset(exclusions(after['SPRING_AUTOCONFIGURE_EXCLUDE'])))
    # Proteger también los bytes de cada línea ajena al cambio, incluido el JSON.
    require([l for l in original.decode('utf-8').splitlines() if l.strip().split('=', 1)[0] not in updates] ==
            [l for l in updated.decode('utf-8').splitlines() if l.strip().split('=', 1)[0] not in updates])
    return updated


def main():
    original, metadata = preflight()
    env = assignments(original)
    build_config(original, {k: env[k] for k in PRIVATE_KEYS})
    with zipfile.ZipFile(JAR) as jar:
        for name, digest in HASHES.items():
            data = jar.read('BOOT-INF/classes/cl/casol/backend/' + name + '.class')
            require(hashlib.sha256(data).hexdigest() == digest)
        library = jar.read('BOOT-INF/lib/spring-boot-mail-4.1.1.jar')
        require(hashlib.sha256(library).hexdigest() == '0e85d4b551cbdd36387be53d31062b057b7d8914eee13255d5242a9078f4d25c')
        with zipfile.ZipFile(io.BytesIO(library)) as nested:
            require(EXCLUSION.replace('.', '/') + '.class' in nested.namelist())
    updated = prepare(original)
    backup = install(original, metadata, updated)
    emit(estado='AISLAMIENTO_INSTALADO_VERIFICADO', respaldo=backup,
         respaldo_previo_verificado=True, modo_procedimientos_deshabilitado=True,
         autoconfiguracion_smtp_excluida=True, exclusiones_previas_conservadas=True,
         custodia_conservada=True, json_conservado=True, envio_real_false=True,
         otras_variables_conservadas=True, root_root_0600=True,
         ambos_servicios_detenidos=True, mysql_sin_acceso=True, secretos_mostrados=False)


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        frames = traceback.extract_tb(exc.__traceback__)
        emit(estado='AISLAMIENTO_NO_COMPLETADO', categoria=type(exc).__name__,
             linea_control=frames[-2].lineno if len(frames) > 1 else None, secretos_mostrados=False)
        raise SystemExit(1)
