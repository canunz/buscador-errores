"""P0 Skill 4.5: aplica SOLO el drop-in de recursos a dimabug-wa-pilot.service, prueba el arranque y decide GO/NO-GO.

Subtareas: 1) margen de memoria (nuevos límites + pasarela Graph), 2-3) revisión del drop-in y opciones JVM con el
Java 21 del servidor, 4) instalación, 5) daemon-reload, 6) startup_check.py, 7) monitoreo de memory.events,
cpu.stat y MemAvailable durante el arranque. Si falla cualquier criterio: reversión del drop-in.
No toca otros servicios, timeouts, DNS, certificados ni envíos reales; no ejecuta el piloto de 20 eventos.
startup_check.py detiene QA y bloquea la cuenta MySQL al terminar (siempre, también ante fallo).

Uso (root, en el directorio del bundle wa_durable_0eb3331f3918, junto a 80-recursos-arranque.conf):
  sudo python3 aplicar_recursos_arranque.py --autorizacion APLICAR_RECURSOS_SOLO_QA
"""
import argparse, json, os, re, subprocess, sys, threading, time
from pathlib import Path

HERE = Path.cwd()
QA = 'dimabug-wa-pilot.service'
GRAPH = 'dimabug-wa-pilot-graph.service'
DROPIN_SRC = HERE / '80-recursos-arranque.conf'
DROPIN_DST = Path('/etc/systemd/system/dimabug-wa-pilot.service.d/80-recursos-arranque.conf')
CGROUP = Path('/sys/fs/cgroup/system.slice') / QA
EVIDENCIA = HERE / 'evidencia-recursos-arranque.json'
JVM = ['-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-Xss512k']
NUEVO_MAX_MIB, MINIMO_LIBRE_MIB = 768, 1024
ESPERADO = {'MemoryHigh': str(640 * 2**20), 'MemoryMax': str(768 * 2**20), 'CPUQuotaPerSecUSec': '1s'}


def run(args, timeout=60, check=True):
    r = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError('FALLO_' + Path(args[0]).name.upper().replace('-', '_'))
    return r.stdout.strip()


def prop(unidad, clave):
    return run(['systemctl', 'show', unidad, '-p', clave, '--value'])


def disponible_mib():
    linea = next(l for l in Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:'))
    return int(linea.split()[1]) // 1024


def mib(valor):
    return None if not valor.isdigit() else int(valor) // 2**20


def identidades():
    return {u: (prop(u, 'MainPID'), prop(u, 'NRestarts')) for u in ['mysql.service', 'nginx.service', 'dimabug@staging.service']}


def revertir(evidencia, motivo):
    if DROPIN_DST.exists():
        DROPIN_DST.unlink()
        if not any(DROPIN_DST.parent.iterdir()):
            DROPIN_DST.parent.rmdir()
        run(['systemctl', 'daemon-reload'], check=False)
    evidencia['reversion'] = {'ejecutada': True, 'motivo': motivo, 'dropin_presente': DROPIN_DST.exists()}


def monitorear(evidencia, alto):
    """Muestras cada segundo mientras la unidad exista; no modifica nada."""
    m = evidencia['monitoreo'] = {'muestras': 0, 'mem_disponible_min_mib': disponible_mib(),
                                  'memory_current_max_mib': 0, 'memory_events': {}, 'cpu_stat': {}}
    while not alto.is_set():
        m['mem_disponible_min_mib'] = min(m['mem_disponible_min_mib'], disponible_mib())
        if CGROUP.is_dir():
            try:
                actual = int((CGROUP / 'memory.current').read_text())
                m['memory_current_max_mib'] = max(m['memory_current_max_mib'], actual // 2**20)
                m['memory_events'] = dict(l.split() for l in (CGROUP / 'memory.events').read_text().splitlines())
                m['cpu_stat'] = {k: v for k, v in (l.split() for l in (CGROUP / 'cpu.stat').read_text().splitlines())
                                 if k in ('usage_usec', 'nr_periods', 'nr_throttled', 'throttled_usec')}
                m['muestras'] += 1
            except (OSError, ValueError):
                pass
        alto.wait(1)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--autorizacion', required=True)
    a = p.parse_args()
    if a.autorizacion != 'APLICAR_RECURSOS_SOLO_QA' or os.geteuid() != 0:
        p.error('Se requiere root y --autorizacion APLICAR_RECURSOS_SOLO_QA')
    evidencia = {'inicio_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()), 'criterios': {}}
    instalado = False
    try:
        # 1. Margen: lo disponible ahora debe cubrir el peor caso de QA + pasarela y seguir dejando 1 GiB libre.
        for u in (QA, GRAPH):
            if prop(u, 'ActiveState') in ('active', 'activating', 'deactivating'):
                raise RuntimeError('QA_DEBE_ESTAR_DETENIDO')
        if DROPIN_DST.exists():
            raise RuntimeError('DROPIN_YA_INSTALADO')
        libre = disponible_mib()
        pasarela = mib(prop(GRAPH, 'MemoryMax')) or 0
        margen = libre - NUEVO_MAX_MIB - pasarela
        evidencia['margen'] = {'mem_disponible_mib': libre, 'qa_memory_max_mib': NUEVO_MAX_MIB,
                               'pasarela_memory_max_mib': pasarela, 'margen_peor_caso_mib': margen}
        if margen < MINIMO_LIBRE_MIB:
            raise RuntimeError('MARGEN_INSUFICIENTE_SIN_CAMBIOS')

        # 2. Revisión del drop-in: solo recursos y entorno JVM; nada de ExecStart, timeouts ni red.
        texto = DROPIN_SRC.read_text(encoding='utf-8')
        claves = sorted({l.split('=', 1)[0] for l in texto.splitlines() if l and not l.startswith(('#', '['))})
        if claves != ['CPUQuota', 'Environment', 'MemoryHigh', 'MemoryMax']:
            raise RuntimeError('DROPIN_CONTIENE_CLAVES_NO_AUTORIZADAS')

        # 3. Compatibilidad con el Java 21 real del servidor.
        version = subprocess.run(['/usr/bin/java', *JVM, '-version'], capture_output=True, text=True, timeout=30)
        flags = run(['/usr/bin/java', *JVM, '-XX:+PrintFlagsFinal', '-version'], timeout=30)
        evidencia['java'] = {'version': (version.stderr.splitlines() or [''])[0],
                             'flags_aceptados': version.returncode == 0 and
                             bool(re.search(r'bool UseSerialGC\s+= true', flags)) and
                             bool(re.search(r'TieredStopAtLevel\s+= 1\b', flags))}
        if not evidencia['java']['flags_aceptados'] or ' 21.' not in ' ' + evidencia['java']['version'].replace('"', ' '):
            raise RuntimeError('JAVA_21_NO_ACEPTA_OPCIONES')

        # 4-5. Instalación exclusiva de QA y daemon-reload; se verifica que systemd la haya aplicado.
        antes = identidades()
        DROPIN_DST.parent.mkdir(parents=True, exist_ok=True)
        DROPIN_DST.write_text(texto, encoding='utf-8'); DROPIN_DST.chmod(0o644); instalado = True
        run(['systemctl', 'daemon-reload'])
        aplicado = {k: prop(QA, k) for k in ESPERADO}
        entorno = prop(QA, 'Environment')
        evidencia['dropin_aplicado'] = aplicado
        if aplicado != ESPERADO or 'MALLOC_ARENA_MAX=2' not in entorno or 'UseSerialGC' not in entorno:
            raise RuntimeError('SYSTEMD_NO_APLICO_EL_DROPIN')

        # 6-7. Arranque con startup_check.py (180 s, GET 200, sin OOM/reinicios, servicios ajenos intactos)
        #      mientras se registran memory.events, cpu.stat y MemAvailable.
        alto = threading.Event()
        hilo = threading.Thread(target=monitorear, args=(evidencia, alto), daemon=True); hilo.start()
        check = subprocess.run([sys.executable, '-u', str(HERE / 'startup_check.py'), '--execute',
                                '--approval', 'AJUSTAR_TIMEOUT_Y_PROBAR_SOLO_ARRANQUE_QA'],
                               capture_output=True, text=True, timeout=600)
        alto.set(); hilo.join(5)
        try:
            estado = json.loads(check.stdout.strip().splitlines()[-1])
        except (IndexError, ValueError):
            estado = {'status': 'FAIL', 'error_code': 'STARTUP_CHECK_SIN_RESULTADO'}
        evidencia['startup_check'] = estado
        m = evidencia.get('monitoreo', {})
        eventos = m.get('memory_events', {})
        evidencia['estrangulamiento'] = {
            'memory_high_eventos': int(eventos.get('high', 0)), 'memory_max_eventos': int(eventos.get('max', 0)),
            'oom_kill': int(eventos.get('oom_kill', 0)), 'cpu_throttled_s': int(m.get('cpu_stat', {}).get('throttled_usec', 0)) / 1e6}
        c = evidencia['criterios']
        c['disponible_en_180_s'] = estado.get('status') == 'PASS' and float(estado.get('ready_seconds', 999)) <= 180
        c['http_200'] = estado.get('get_http') == 200
        c['memoria_disponible_min_1_gib'] = min(m.get('mem_disponible_min_mib', 0), estado.get('available_min_mib', 0)) >= MINIMO_LIBRE_MIB
        c['sin_oom_ni_reinicios'] = (not estado.get('oom_observed', True) and estado.get('automatic_restarts') == 0
                                     and evidencia['estrangulamiento']['oom_kill'] == 0)
        c['staging_mysql_nginx_intactos'] = identidades() == antes and estado.get('existing_services_unchanged') is True
        c['qa_detenido_y_cuenta_bloqueada'] = estado.get('qa_stopped') is True and estado.get('qa_account_locked') is True
        if not all(c.values()):
            raise RuntimeError('CRITERIO_NO_CUMPLIDO')
        evidencia['decision'] = 'GO: arranque satisfactorio; piloto funcional de 20 eventos habilitado para autorización.'
    except Exception as e:
        codigo = str(e) if re.fullmatch('[A-Z0-9_]+', str(e)) else type(e).__name__
        evidencia['error'] = codigo
        if instalado:
            revertir(evidencia, codigo)
        evidencia['decision'] = 'NO-GO: ' + codigo
    finally:
        evidencia['qa_activo_al_final'] = {u: prop(u, 'ActiveState') for u in (QA, GRAPH)}
        evidencia['fin_utc'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        EVIDENCIA.write_text(json.dumps(evidencia, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        EVIDENCIA.chmod(0o644)
        print(json.dumps(evidencia, indent=2, ensure_ascii=False))
    if not evidencia['decision'].startswith('GO'):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
