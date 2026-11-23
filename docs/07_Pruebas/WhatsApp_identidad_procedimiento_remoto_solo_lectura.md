# Procedimiento propuesto: identidad WhatsApp QA, solo lectura

**Estado: preparado para revisión y autorización; NO ejecutado.**

## Alcance y límite de lo demostrable

Objetivo: contrastar el script instalado, la unidad systemd y, solo si YA existe un
proceso QA activo por una autorización independiente, su identidad Linux efectiva.
No iniciar procesos QA para obtener estos datos. No ejecutar el diagnóstico activo.

El código `QA_UID_IDENTITY_MISMATCH` del intento v2 confirma que falló la condición
del script antiguo, que combinaba usuario de systemd y propietario de `/proc/PID`.
No conservó cuál de los dos operandos difería ni sus valores. Un PID desaparecido no
puede reconstruirse leyendo `/proc` ahora. Si no existe evidencia contemporánea, la
causa exacta histórica debe quedar **INDETERMINADA**, aunque la revisión encuentre
un defecto reproducible en el comprobador.

La causa del rechazo del comprobador y la causa del arranque incompleto de Spring
son dos preguntas distintas. Este procedimiento no captura hilos ni diagnostica
beans de Spring. No debe prometer que ambas se resolverán con QA detenido.

## Permisos, riesgos y límites

| Operación | Permiso | Riesgo y control |
|---|---|---|
| `systemctl show` de unidades concretas | SSH usuario carolina | Consulta al manager; no start/stop/reload ni cambios |
| Leer script instalado y resultado seguro | Lectura del directorio QA | Solo SHA-256 y campos enumerados; no ejecuta/importa el script remoto |
| `/proc/PID/status`, exe, cmdline, stat, cgroup | Sudo para sortear protecciones normales de lectura | argv se compara en memoria y nunca se imprime; no lee environ |
| Journal de una unidad diagnóstica | Sudo | Filtrado por lista de códigos; no salida cruda ni payloads |
| Estado de bloqueo MySQL QA | Sudo y privilegio de lectura de mysql.user vía socket | SELECT de una cuenta exacta; sin ALTER/UNLOCK/LOCK ni secretos en CLI |

La sonda hace una muestra si QA está detenido o hasta tres si está activo, separadas
por 250 ms, con tiempos máximos por llamada. Puede observar un proceso desaparecer;
eso se informa sin perseguir otro PID ni enviar señales. No desactivar Yama ni otras
protecciones si se deniega una lectura: detener la revisión y comunicar el permiso faltante.

No hay escrituras intencionales remotas ni archivos temporales, descargas o transferencias.
SSH/sudo pueden producir sus registros de auditoría habituales del sistema. No usar `tee`,
redirecciones remotas, `env`, `ps e`, dumps de propiedades ni `systemctl show` sin filtrar.
El contenido completo de la configuración y los secretos no se consulta.

## Comandos preparados (uno por vez, únicamente tras autorización)

Ejecutar manualmente dentro de la sesión SSH existente. La contraseña sudo se introduce
solo en el terminal, nunca en el chat. Revisar la salida de cada paso antes del siguiente.

### 1. Referencia de servicios

```bash
systemctl show dimabug-wa-pilot.service dimabug-wa-pilot-graph.service dimabug@staging.service mysql.service nginx.service --property=Id --property=ActiveState --property=MainPID --property=NRestarts
```

Esperado: QA y pasarela sin PID, staging/MySQL/Nginx activos. El estado QA puede ser
`inactive` o `failed`; ambos son detenidos si MainPID=0. Esta lectura no confirma el
bloqueo de cuenta. Conservar la salida para compararla al final, sin escribirla en el servidor.

### 2. Sonda de identidad de solo lectura

El siguiente bloque contiene la sonda local revisable
`qa/whatsapp/ubuntu/shared-mysql-pilot/inspect_identity_readonly.py`.
Pegarlo completo: se ejecuta desde stdin y no se copia al servidor. No ejecuta
`thread_check.py`, no inicia ni detiene unidades, no envía señales y no invoca jcmd.

```bash
sudo python3 - <<'PY'
"""Proposed manual read-only probe. Never starts/stops units, signals, attaches, or writes.
No imports from server scripts; no environment, config secrets, payloads or raw argv output.
"""
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import subprocess
import time

UNIT = 'dimabug-wa-pilot.service'
USER = 'dimabug-wa-pilot'
JAR = b'/opt/dimabug-wa-pilot/whatsapp-qa.jar'
ROOT = Path('/home/carolina/wa_durable_0eb3331f3918')

def read_unit():
    keys = ['Type', 'User', 'MainPID', 'ActiveState', 'InvocationID', 'ControlGroup',
            'ExecStart', 'FragmentPath', 'DropInPaths', 'PrivateTmp', 'ProtectHome',
            'MemoryMax', 'MemoryHigh', 'MemorySwapMax', 'CPUQuotaPerSecUSec',
            'TasksMax', 'TimeoutStartUSec', 'TimeoutStopUSec', 'Restart',
            'StandardOutput', 'StandardError', 'LogRateLimitBurst', 'LogRateLimitIntervalUSec']
    args = ['systemctl', 'show', UNIT] + ['--property=' + key for key in keys]
    out = subprocess.check_output(args, text=True, timeout=10)
    return dict(line.split('=', 1) for line in out.splitlines() if '=' in line)

def safe_status():
    path = ROOT / 'thread-status.json'
    if not path.exists():
        return {'available': False}
    data = json.loads(path.read_text())
    safe = {'available': True, 'captures_count': len(data.get('captures', []))}
    for key in ['status', 'error_code']:
        value = data.get(key)
        if isinstance(value, str) and re.fullmatch('[A-Z0-9_]+', value):
            safe[key] = value
    for key in ['qa_stopped', 'qa_account_locked', 'existing_services_unchanged']:
        if type(data.get(key)) is bool:
            safe[key] = data[key]
    return safe

def live_sample():
    first = read_unit()
    pid = int(first.get('MainPID', '0'))
    if pid <= 1:
        return {'result': 'NOT_OBSERVABLE_QA_STOPPED', 'pid_present': False}
    proc = Path('/proc') / str(pid)
    def ticks():
        return (proc / 'stat').read_text().rsplit(')', 1)[1].split()[19]
    start = ticks()
    raw = (proc / 'cmdline').read_bytes()
    exe = str((proc / 'exe').resolve(strict=True))
    status = (proc / 'status').read_text()
    match = re.search(r'(?m)^Uid:\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*$', status)
    if not match:
        return {'result': 'PROC_UID_STATUS_INVALID'}
    uids = tuple(map(int, match.groups()))
    expected_uid = pwd.getpwnam(USER).pw_uid
    owner = proc.stat().st_uid
    cgroup = (proc / 'cgroup').read_text().splitlines()
    stable_proc = (start == ticks() and raw == (proc / 'cmdline').read_bytes()
                   and exe == str((proc / 'exe').resolve(strict=True))
                   and re.search(r'(?m)^Uid:.*$', status).group() ==
                       re.search(r'(?m)^Uid:.*$', (proc / 'status').read_text()).group())
    last = read_unit()
    stable = stable_proc and all(first.get(k) == last.get(k)
                                  for k in ['MainPID', 'InvocationID', 'ControlGroup', 'User'])
    args = raw.rstrip(b'\0').split(b'\0') if raw else []
    pos = args.index(b'-jar') if args.count(b'-jar') == 1 else -1
    credentials = '/run/credentials/' + UNIT
    checks = dict(
        unit_user_matches=first.get('User') == USER,
        uid_real_matches=uids[0] == expected_uid,
        uid_effective_matches=uids[1] == expected_uid,
        uid_saved_matches=uids[2] == expected_uid,
        uid_filesystem_matches=uids[3] == expected_uid,
        cgroup_matches=bool(first.get('ControlGroup')) and cgroup == ['0::' + first['ControlGroup']],
        java_executable_matches=exe == str(Path('/usr/bin/java').resolve(strict=True)),
        java_argv0_matches=bool(args) and args[0] == b'/usr/bin/java',
        jar_matches=pos >= 0 and args[pos+1:pos+2] == [JAR],
        config_arguments_match=pos >= 0 and args[pos+2:] == [
            ('--spring.config.location=file:' + credentials + '/application.properties').encode(),
            ('--spring.config.import=configtree:' + credentials + '/').encode()])
    failed = [name for name, passed in checks.items() if not passed]
    return dict(result='STATE_CHANGED_DURING_READ' if not stable else ('MATCH' if not failed else 'MISMATCH'),
                stable=stable, proc_owner_matches_expected=owner == expected_uid,
                checks=checks, first_incompatible=failed[0] if stable and failed else None)

def main():
    if os.geteuid() != 0:
        raise SystemExit('ROOT_REQUIRED_FOR_READ_ONLY_PROC_ACCESS')
    unit = read_unit()
    # ExecStart is inspected in memory, never printed (could contain private arguments).
    launch = unit.pop('ExecStart', '')
    unit.pop('InvocationID', None)
    unit.pop('MainPID', None)
    safe_unit_keys = ['Type', 'ActiveState', 'PrivateTmp', 'ProtectHome', 'MemoryMax',
                      'MemoryHigh', 'MemorySwapMax', 'CPUQuotaPerSecUSec', 'TasksMax',
                      'TimeoutStartUSec', 'TimeoutStopUSec', 'Restart', 'StandardOutput',
                      'StandardError', 'LogRateLimitBurst', 'LogRateLimitIntervalUSec']
    result = dict(unit={k: unit[k] for k in safe_unit_keys if k in unit},
                  unit_user_matches=unit.get('User') == USER,
                  execstart_java_matches='path=/usr/bin/java ;' in launch,
                  execstart_jar_matches=bool(re.search(r'\s-jar /opt/dimabug-wa-pilot/whatsapp-qa\.jar(?:\s|;)', launch)),
                  dropins_present=bool(unit.get('DropInPaths')), previous_result=safe_status())
    script = ROOT / 'thread_check.py'
    result['remote_script_sha256'] = hashlib.sha256(script.read_bytes()).hexdigest() if script.exists() else None
    result['samples'] = []
    for index in range(3):
        try:
            sample = live_sample()
        except (FileNotFoundError, ProcessLookupError):
            sample = {'result': 'PROCESS_DISAPPEARED_DURING_READ'}
        except (PermissionError, KeyError):
            sample = {'result': 'PROC_ACCESS_OR_SERVICE_USER_UNAVAILABLE'}
        result['samples'].append(sample)
        if sample.get('result') == 'NOT_OBSERVABLE_QA_STOPPED':
            break
        if index < 2:
            time.sleep(.25)
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError):
        raise SystemExit('READ_ONLY_PROBE_FAILED_NO_PRIVATE_DETAILS')
PY
```

Resultados esperados:

- Con QA detenido: `NOT_OBSERVABLE_QA_STOPPED`. Es resultado válido y no permiso para arrancarlo.
- Comparación de SHA remoto con `sources/thread_check.py` del manifiesto local. Una diferencia
  solo demuestra versiones distintas; no autoriza transferir ni ejecutar la nueva.
- `Type=simple`, usuario QA, Java/JAR esperados, 512 MiB y 0,5 CPU según antecedentes;
  cualquier discrepancia se informa. No corregirla durante esta fase.
- Si YA existe QA activo: `MATCH` exige los cuatro UID, ejecutable, cgroup, JAR y argumentos
  de configuración correctos, en una lectura estable. `MISMATCH` informa el primer campo
  incompatible sin mostrar sus valores privados.
- `STATE_CHANGED_DURING_READ` o desaparición del proceso son observaciones transitorias,
  no prueba de que aquel evento histórico tuviera el mismo problema.
- `proc_owner_matches_expected` se informa aparte: no se utiliza como sustituto del UID.
- `remote_script_sha256` y `previous_result` son datos actuales del archivo existente,
  no una nueva medición del proceso que originó el rechazo v2.

### 3. Registro diagnóstico histórico acotado

```bash
sudo python3 - <<'PY'
import datetime, json, re, subprocess
allowed = {'QA_UID_IDENTITY_MISMATCH', 'QA_JAR_IDENTITY_MISMATCH',
           'QA_CGROUP_IDENTITY_MISMATCH', 'QA_PROCESS_CHANGED_DURING_CHECK',
           'QA_JAVA_EXECUTABLE_MISMATCH', 'QA_PID_CHANGED_OR_ABSENT',
           'QA_CONFIG_ARGUMENTS_MISMATCH', 'QA_UNIT_NOT_ACTIVE'}
raw = subprocess.check_output([
    'journalctl', '-u', 'dimabug-wa-thread-execution-v2.service',
    '-n', '500', '--no-pager', '-o', 'json'], text=True, timeout=15)
found = False
for line in raw.splitlines():
    row = json.loads(line)
    codes = sorted(set(re.findall(r'\bQA_[A-Z0-9_]+\b', row.get('MESSAGE', ''))) & allowed)
    if codes:
        found = True
        stamp = datetime.datetime.fromtimestamp(int(row['__REALTIME_TIMESTAMP']) / 1000000,
                                                datetime.timezone.utc).isoformat()
        print(json.dumps({'utc': stamp, 'validation_codes': codes}))
if not found:
    print('SIN_CODIGO_DE_IDENTIDAD_CONSERVADO_EN_ESTE_JOURNAL')
PY
```

Esperado: puede no aparecer el código, porque el script guardaba el detalle en
`thread-status.json`, no necesariamente en journal. No ampliar a otros servicios ni
imprimir logs completos para compensarlo. Un código sin snapshot asociado no demuestra
los UID/argv históricos. La ausencia de journal tampoco demuestra ausencia del fallo.

### 4. Confirmar bloqueo actual de la cuenta QA

```bash
sudo mysql --protocol=socket --batch --skip-column-names --execute="SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1';"
```

Esperado: exactamente una fila `Y`. Si devuelve `N`, ninguna fila o error de permiso,
informarlo y detener la revisión: este procedimiento **no autoriza** cambiar la cuenta.
No suministrar contraseñas mediante argumentos ni utilizar cuentas de aplicaciones existentes.

### 5. Comprobación final

Repetir únicamente el comando del paso 1. Comparar MainPID, NRestarts y estado de las
unidades protegidas con la referencia. Las lecturas no deben iniciar QA ni alterar unidades.
La cuenta debe seguir bloqueada conforme al paso 4. Si ocurre un cambio externo durante
la observación, no atribuirlo a la sonda sin correlación y no intervenir sobre servicios.

## Criterios de aceptación

### Revisión estática, con QA detenido

1. Autorización explícita limitada a los comandos anteriores.
2. SHA del script remoto, Type, User, ExecStart filtrado y restricciones documentados.
3. QA sin PID; cuenta con una fila `Y`; servicios existentes sin cambios entre lecturas.
4. Salida sin secretos, payloads, números telefónicos, BSUID ni argv sin filtrar.
5. Si no existe snapshot histórico, resultado **INDETERMINADO: valores efectivos no conservados**.
   Esta es una conclusión aceptable de la fase de solo lectura, no una causa raíz demostrada.

### Validación de identidad de un proceso ya activo (condicional)

Requiere una ventana de arranque autorizada por separado y ajena a este procedimiento.
No forma parte de la autorización solicitada ahora.

1. Correlación con la misma invocación y momento del rechazo; PID e inicio estables.
2. Lecturas consistentes de los cuatro UID, cgroup, exe, `-jar` y configuración.
3. Causa exacta solo si el campo incompatible se observa en la captura correlacionada.
   Un MATCH posterior no explica un rechazo previo.
4. No se usan señales, attach ni modificaciones para obtener esa observación.
5. Si el fallo fue transitorio y no se capturó, mantener INDETERMINADO; no inferirlo por descarte.

Dos Thread Dumps remotos y la causa del bloqueo de Spring necesitarán una ejecución
activa posterior expresamente autorizada. No quedan aprobados por revisar este documento.

## Autorización a solicitar

Autorizar exclusivamente la ejecución manual de los cinco pasos de lectura, sin
transferir scripts ni modificar el servidor. Si QA sigue detenido, finalizar con el
inventario seguro y el límite de evidencia histórica; no solicitar otro arranque como
parte automática de esta revisión.
