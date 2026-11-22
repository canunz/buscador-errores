"""Resumen SOLO LECTURA del arranque bloqueado del piloto QA en DigitalOcean.

Lee los dos Thread.print guardados por thread_check.py y el journal de la última ejecución de
dimabug-wa-pilot.service, y muestra únicamente lo necesario para encontrar la causa raíz:
  - hilo main y hilos de la aplicación (Spring, Hibernate, Hikari, MySQL, TLS, red) con sus frames superiores;
  - si el hilo main avanzó o quedó en el mismo punto entre ambos volcados;
  - presión de memoria/CPU del cgroup (si el servicio sigue activo) y líneas clave del journal.
No modifica archivos, servicios, cuentas ni configuración. Enmascara contraseñas, tokens y secretos.

Uso (en el servidor):  sudo python3 resumen_arranque.py [--dir /etc/dimabug-wa-pilot/thread-dumps-AAAAMMDD-HHMMSS]
"""
import argparse, re, subprocess
from pathlib import Path

CONF = Path('/etc/dimabug-wa-pilot')
UNIT = 'dimabug-wa-pilot.service'
INTERES = ('cl.casol', 'org.springframework', 'org.hibernate', 'com.zaxxer', 'com.mysql', 'sun.security',
           'javax.net', 'java.net', 'sun.nio', 'org.apache.catalina', 'org.apache.tomcat', 'java.security',
           'java.lang.ClassLoader', 'jdk.internal.loader', 'java.util.concurrent')
SECRETO = re.compile(r'(?i)((?:password|passwd|secret|token|clave[-_a-z]*|app-secret)[^=:&\s]*\s*[=:]\s*)[^&\s,;]+')


def limpiar(texto: str) -> str:
    return SECRETO.sub(r'\1<omitido>', texto)


def hilos(dump: str):
    """Devuelve [(nombre, estado, [frames])] a partir de un Thread.print."""
    resultado = []
    for bloque in re.split(r'\n(?=")', dump):
        cabecera = re.match(r'"([^"]+)"', bloque)
        if not cabecera:
            continue
        estado = re.search(r'java\.lang\.Thread\.State: (\w+)', bloque)
        frames = re.findall(r'^\s+at (\S+)', bloque, re.M)
        espera = re.findall(r'^\s+- (?:waiting on|waiting to lock|parking to wait for|locked) .*$', bloque, re.M)
        resultado.append((cabecera.group(1), estado.group(1) if estado else 'NATIVO', frames, espera))
    return resultado


def resumir(nombre: str, dump: str):
    lista = hilos(dump)
    conteo = {}
    for _, estado, _, _ in lista:
        conteo[estado] = conteo.get(estado, 0) + 1
    print(f'\n=== {nombre}: {len(lista)} hilos {conteo}')
    principal = None
    for hilo, estado, frames, espera in lista:
        es_main = hilo == 'main'
        relevante = es_main or any(f.startswith(INTERES) for f in frames[:15])
        if not relevante or (not es_main and estado in ('WAITING', 'TIMED_WAITING') and
                             all(f.startswith(('java.', 'jdk.')) for f in frames[:6])):
            continue
        print(f'\n-- "{hilo}" {estado}')
        for linea in espera[:2]:
            print('   ' + limpiar(linea.strip()))
        for frame in frames[:25 if es_main else 12]:
            print('   at ' + limpiar(frame))
        if es_main:
            principal = frames[:8]
    return principal


def journal():
    invocacion = subprocess.run(['systemctl', 'show', UNIT, '-p', 'InvocationID', '--value'],
                                capture_output=True, text=True).stdout.strip()
    args = ['journalctl', '--no-pager', '-o', 'short-iso']
    args += ['_SYSTEMD_INVOCATION_ID=' + invocacion] if invocacion else ['-u', UNIT, '-n', '400']
    salida = subprocess.run(args, capture_output=True, text=True).stdout
    clave = re.compile(r'(Starting BackendApplication|Started BackendApplication|The following|profile|HikariPool|'
                       r'Hibernate|EntityManagerFactory|Tomcat|Exception|Caused by|ERROR|WARN|OutOfMemory|'
                       r'Killed|Main process exited|Failed|Consumed|memory|CPU)', re.I)
    lineas = [limpiar(l)[:300] for l in salida.splitlines() if clave.search(l)]
    print(f'\n=== Journal ({len(lineas)} líneas clave de la última ejecución)')
    for linea in lineas[-60:]:
        print(linea)


def cgroup():
    props = ['ActiveState', 'SubState', 'Result', 'ExecMainStatus', 'NRestarts', 'MemoryCurrent',
             'MemoryPeak', 'MemoryHigh', 'MemoryMax', 'CPUUsageNSec', 'CPUQuotaPerSecUSec', 'TasksCurrent', 'TasksMax']
    salida = subprocess.run(['systemctl', 'show', UNIT, '-p', ','.join(props)], capture_output=True, text=True).stdout
    print('\n=== Unidad\n' + salida.strip())
    grupo = subprocess.run(['systemctl', 'show', UNIT, '-p', 'ControlGroup', '--value'],
                           capture_output=True, text=True).stdout.strip()
    base = Path('/sys/fs/cgroup' + grupo) if grupo else None
    if base and base.is_dir():
        for archivo in ('memory.events', 'memory.pressure', 'cpu.stat', 'cpu.pressure'):
            ruta = base / archivo
            if ruta.exists():
                print(f'--- {archivo}\n' + ruta.read_text().strip())
    else:
        print('(cgroup no disponible: el servicio está detenido; ejecutar durante un arranque para ver presión de memoria/CPU)')


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--dir', type=Path, help='Directorio thread-dumps-*. Por defecto el más reciente.')
    a = p.parse_args()
    directorio = a.dir or max(CONF.glob('thread-dumps-*'), default=None, key=lambda d: d.stat().st_mtime)
    principales = []
    if directorio and directorio.is_dir():
        archivos = sorted(f for f in directorio.iterdir() if f.is_file())
        print(f'Volcados en {directorio}: {[f.name for f in archivos]}')
        for archivo in archivos[:2]:
            principales.append(resumir(archivo.name, archivo.read_text(encoding='utf-8', errors='replace')))
        if len(principales) == 2 and principales[0] and principales[1]:
            igual = principales[0][:3] == principales[1][:3]
            print('\n=== Hilo main entre volcados: ' + ('MISMO PUNTO (bloqueado o esperando)' if igual else 'AVANZA (arranque lento, no bloqueado)'))
    else:
        print('No se encontraron volcados thread-dumps-* en ' + str(CONF))
    cgroup()
    journal()


if __name__ == '__main__':
    main()
