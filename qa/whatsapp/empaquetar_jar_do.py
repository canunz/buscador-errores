"""Empaqueta el JAR de DigitalOcean: JAR de Maven + fábrica TLS QA de MySQL (dimabug.qa.SharedMysqlPinnedSocketFactory).

Por qué existe: la fábrica TLS con pin del certificado vive en qa/whatsapp/ubuntu/shared-mysql-pilot (no en las
fuentes del backend) y build_shared_mysql_tls_repair.py la inyectaba a mano en BOOT-INF/classes. Un JAR recién
compilado con Maven no la contiene y el arranque falla con ClassNotFoundException. Este script hace ese paso de
forma reproducible y verificable después de cada `mvnw package`:
  1. Compila la fábrica (y su prueba) contra el mysql-connector-j incluido en el propio JAR.
  2. Ejecuta SharedMysqlPinTest (sin red, SQL ni secretos).
  3. Copia el JAR entrada por entrada (los JAR anidados siguen STORED) y agrega BOOT-INF/classes/dimabug/qa/*.class.
  4. Verifica: clase presente, JAR anidados sin comprimir, la clase carga con todas las librerías de BOOT-INF/lib
     y es asignable a com.mysql.cj.protocol.SocketFactory, y el JAR trae los cambios de OCR Linux y WhatsApp real.
  5. Escribe whatsapp-qa.jar y whatsapp-qa.jar.sha256 en qa/runtime/despliegue-do/.

Uso:  python qa/whatsapp/empaquetar_jar_do.py [--jar dima-bug-backend/target/backend-0.0.1-SNAPSHOT.jar]
Requiere JDK 21 (javac y java en PATH o en JAVA_HOME).
"""
import argparse, hashlib, json, os, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FUENTES = ROOT / 'qa/whatsapp/ubuntu/shared-mysql-pilot'
CLASES_TLS = ['SharedMysqlPinnedSocketFactory.java', 'SharedMysqlPinTest.java']
DESTINO = 'BOOT-INF/classes/dimabug/qa/'
REQUERIDAS = [  # Cambios que deben viajar en el JAR de DigitalOcean.
    'BOOT-INF/classes/cl/casol/backend/whatsapp/infrastructure/ocr/OcrNodeProcesoAdapter.class',
    'BOOT-INF/classes/cl/casol/backend/whatsapp/infrastructure/meta/MetaQaConfig.class',
    'BOOT-INF/classes/cl/casol/backend/whatsapp/infrastructure/meta/GraphMetaCliente.class',
    'BOOT-INF/classes/cl/casol/backend/whatsapp/infrastructure/tickets/TicketWhatsAppAdapter.class',
    'BOOT-INF/classes/cl/casol/backend/whatsapp/application/service/AutoatencionWhatsAppService.class',
]


def herramienta(nombre):
    home = os.environ.get('JAVA_HOME')
    exe = nombre + ('.exe' if os.name == 'nt' else '')
    if home and (Path(home) / 'bin' / exe).is_file():
        return str(Path(home) / 'bin' / exe)
    encontrada = shutil.which(nombre)
    if not encontrada:
        raise SystemExit(f'Falta {nombre} (JDK 21)')
    return encontrada


def ejecutar(args):
    r = subprocess.run(args, capture_output=True, text=True)
    if r.returncode:
        raise SystemExit(f'FALLO {Path(args[0]).name}: {(r.stderr or r.stdout).strip()[-1500:]}')
    return r.stdout.strip()


VERIFICADOR = r'''
import java.net.*; import java.nio.file.*; import java.util.*;
public class VerificarCarga { public static void main(String[] a) throws Exception {
  List<URL> urls = new ArrayList<>(); urls.add(Path.of(a[0]).toUri().toURL());
  try (var s = Files.list(Path.of(a[1]))) { for (Path p : s.toList()) urls.add(p.toUri().toURL()); }
  try (var cl = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
    Class<?> f = Class.forName("dimabug.qa.SharedMysqlPinnedSocketFactory", true, cl);
    Class<?> sf = Class.forName("com.mysql.cj.protocol.SocketFactory", false, cl);
    if (!sf.isAssignableFrom(f)) throw new AssertionError("NO_ES_SOCKET_FACTORY");
    f.getConstructor().newInstance();
    Class<?> ocr = Class.forName("cl.casol.backend.whatsapp.infrastructure.ocr.OcrNodeProcesoAdapter", false, cl);
    ocr.getConstructor(Path.class, Path.class, Path.class, java.time.Duration.class);
    System.out.println("CARGA_OK " + f.getName());
  } } }
'''


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--jar', type=Path, default=ROOT / 'dima-bug-backend/target/backend-0.0.1-SNAPSHOT.jar')
    p.add_argument('--salida', type=Path, default=ROOT / 'qa/runtime/despliegue-do')
    a = p.parse_args()
    javac, java = herramienta('javac'), herramienta('java')
    version = subprocess.run([java, '-version'], capture_output=True, text=True).stderr
    if '"21' not in version:
        raise SystemExit('Se requiere JDK 21: ' + version.splitlines()[0])
    with zipfile.ZipFile(a.jar) as jar:
        nombres = jar.namelist()
        if any(n.startswith(DESTINO) for n in nombres):
            raise SystemExit('El JAR de entrada ya contiene dimabug/qa: usar el JAR recién compilado por Maven')
        faltan = [n for n in REQUERIDAS if n not in nombres]
        if faltan:
            raise SystemExit('El JAR no trae los cambios esperados: ' + ', '.join(faltan))
        conector = [n for n in nombres if n.startswith('BOOT-INF/lib/mysql-connector-j-') and n.endswith('.jar')]
        if len(conector) != 1:
            raise SystemExit('Se esperaba un único mysql-connector-j en BOOT-INF/lib')
        with tempfile.TemporaryDirectory() as t:
            tmp = Path(t); libs = tmp / 'lib'; clases = tmp / 'clases'; app = tmp / 'app'
            libs.mkdir(); clases.mkdir(); app.mkdir()
            for n in nombres:  # Librerías para compilar y para verificar la carga real.
                if n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):
                    (libs / Path(n).name).write_bytes(jar.read(n))
                elif n.startswith('BOOT-INF/classes/') and not n.endswith('/'):
                    destino = app / n[len('BOOT-INF/classes/'):]
                    destino.parent.mkdir(parents=True, exist_ok=True); destino.write_bytes(jar.read(n))
            cp_conector = str(libs / Path(conector[0]).name)
            ejecutar([javac, '--release', '21', '-encoding', 'UTF-8', '-cp', cp_conector, '-d', str(clases),
                      *[str(FUENTES / f) for f in CLASES_TLS]])
            prueba = ejecutar([java, '-cp', os.pathsep.join([str(clases), cp_conector]), 'dimabug.qa.SharedMysqlPinTest'])
            if not prueba.startswith('QA_PIN_TESTS_PASS='):
                raise SystemExit('SharedMysqlPinTest no aprobó: ' + prueba)
            fabrica = sorted((clases / 'dimabug/qa').glob('SharedMysqlPinnedSocketFactory*.class'))
            for f in fabrica:  # Igual que el paquete anterior: solo la fábrica, no las pruebas.
                destino = app / 'dimabug/qa' / f.name; destino.parent.mkdir(parents=True, exist_ok=True)
                destino.write_bytes(f.read_bytes())
            (tmp / 'VerificarCarga.java').write_text(VERIFICADOR, encoding='utf-8')
            carga = ejecutar([java, str(tmp / 'VerificarCarga.java'), str(app), str(libs)])
            a.salida.mkdir(parents=True, exist_ok=True)
            salida = a.salida / 'whatsapp-qa.jar'; temporal = a.salida / 'whatsapp-qa.jar.tmp'
            with zipfile.ZipFile(temporal, 'w') as dest:
                for item in jar.infolist():
                    dest.writestr(item, jar.read(item.filename))  # Conserva método (STORED en BOOT-INF/lib).
                for f in fabrica:
                    dest.write(f, DESTINO + f.name, compress_type=zipfile.ZIP_DEFLATED)
            temporal.replace(salida)
    with zipfile.ZipFile(salida) as final:
        infos = {i.filename: i for i in final.infolist()}
        if final.testzip() is not None:
            raise SystemExit('JAR dañado')
        if (DESTINO + 'SharedMysqlPinnedSocketFactory.class') not in infos:
            raise SystemExit('La fábrica no quedó en el JAR')
        comprimidos = [n for n, i in infos.items() if n.startswith('BOOT-INF/lib/') and n.endswith('.jar') and i.compress_type != zipfile.ZIP_STORED]
        if comprimidos:
            raise SystemExit('JAR anidados comprimidos (Spring Boot no los cargaría): ' + ', '.join(comprimidos[:3]))
    digest = hashlib.sha256(salida.read_bytes()).hexdigest()
    (a.salida / 'whatsapp-qa.jar.sha256').write_text(f'{digest}  whatsapp-qa.jar\n', encoding='utf-8')
    print(json.dumps({'estado': 'JAR_EMPAQUETADO', 'jar': str(salida), 'sha256': digest, 'prueba_pin': prueba,
                      'carga': carga, 'clases_tls': [DESTINO + f.name for f in fabrica],
                      'conector': Path(conector[0]).name}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
