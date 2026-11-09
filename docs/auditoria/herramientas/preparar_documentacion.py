# Generador histórico de la entrega 2026-11-06; no usar para sobrescribir las guías vigentes.
"""Genera documentación y evidencias locales sin ejecutar aplicación, SQL o red.

Conserva instantáneas de los documentos que actualiza. No cambia fuentes/configuración.
El contenido describe el checkout auditado, no certifica un servidor MySQL.
"""
from pathlib import Path
import collections
import hashlib
import json
import os
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[3]
DOCS = ROOT / 'docs'
AUDIT = DOCS / 'auditoria'
DAY = '2026-11-06'
HEAD = 'e0d63d961b87c33d77937821c53802e6387a783e'
EVIDENCE = json.loads((AUDIT/'evidencias/COMPROBACIONES.json').read_text(encoding='utf-8'))
SCA = json.loads((AUDIT/'evidencias/DEPENDENCIAS_NPM.json').read_text(encoding='utf-8'))
BASE = ROOT/'dima-bug-backend/src/main/java/cl/casol/backend'
GENERATED = []
CHANGES = []
BACKUPS = []


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def relative(source, target):
    return Path(os.path.relpath(target, source.parent)).as_posix()


def link(source, target, label=None):
    return f'[{label or target.name}]({relative(source, target)})'


def save(name, text):
    p = ROOT/name
    p.parent.mkdir(parents=True, exist_ok=True)
    original=next((x for x in EVIDENCE['initial_documents'] if x['path']==p.relative_to(ROOT).as_posix()),None)
    before=original['sha256'] if original else None
    p.write_text(text.strip()+'\n', encoding='utf-8')
    GENERATED.append(p)
    CHANGES.append({'path':p.relative_to(ROOT).as_posix(),'action':'updated' if before else 'created','before_sha256':before,'after_sha256':sha(p)})
    return p


def guide(name, title, body):
    p = ROOT/name
    return save(name, f'# {title}\n\nEstado documental: actualizado desde el código auditado el {DAY}. '+
                'Los resultados de pruebas citados corresponden a esa auditoría.\n\n'+body+
                '\n\n## Referencias\n\n'+link(p,DOCS/'README.md','Índice vigente')+' · '+
                link(p,AUDIT/'AUDITORIA_DOCUMENTAL.md','Auditoría de origen')+'\n')


def backup(p):
    dest=AUDIT/'historico'/DAY/(p.relative_to(ROOT).as_posix()+'.original.txt')
    dest.parent.mkdir(parents=True,exist_ok=True)
    if not dest.exists():
        shutil.copyfile(p,dest)
    BACKUPS.append({'path':p.relative_to(ROOT).as_posix(),'backup':dest.relative_to(ROOT).as_posix(),'sha256':sha(dest)})
    return dest


def clean(s):
    return str(s).replace('|',' / ').replace('\n',' ')


def heading_ids(text):
    ids=[];counts={};fenced=False
    for line in text.splitlines():
        if line.startswith('```'):fenced=not fenced;continue
        if not fenced and line.startswith('#'):
            h=re.sub(r'[`*_]','',line.lstrip('# ').strip().lower())
            h=re.sub(r'[^\w\- ]','',h).replace(' ','-')
            n=counts.get(h,0);ids.append(h+(f'-{n}' if n else ''));counts[h]=n+1
    return ids


def source(p,needle=None):
    text=p.read_text(encoding='utf-8-sig')
    line=next((i for i,l in enumerate(text.splitlines(),1) if needle and needle in l),1)
    return f'`{p.relative_to(ROOT).as_posix()}:{line}`'


def main():
    # Source integrity snapshot is read before any authoring takes place.
    protected={}
    for top in ['dima-bug-backend','frontend-dimabug']:
        for d,dirs,files in os.walk(ROOT/top):
            dirs[:]=[x for x in dirs if x not in {'node_modules','target','dist','.angular','docs'}]
            for f in files:
                p=Path(d)/f
                if p.name!='README.md':protected[p.relative_to(ROOT).as_posix()]=sha(p)
    audit_hash=sha(AUDIT/'AUDITORIA_DOCUMENTAL.md')
    write_static()
    tables,relations=write_database()
    write_api()
    write_traceability()
    update_entries_and_history()
    # Diagram generation is documented and versioned separately from the old deliverable.
    write_diagrams(tables,relations)
    changed=[name for name,h in protected.items() if sha(ROOT/name)!=h]
    if changed:raise RuntimeError(f'Se modificó un archivo protegido: {changed}')
    assert sha(AUDIT/'AUDITORIA_DOCUMENTAL.md')==audit_hash
    record={'date':DAY,'authorization':'Usuaria: Autorizo. Continuación de fases 5 a 8, exclusivamente documental.',
            'source_head':HEAD,'protected_file_count':len(protected),'protected_changes':changed,
            'audit_original_sha256':audit_hash,'backups':BACKUPS,'changes':CHANGES,
            'tests_rerun':False,'application_or_sql_executed':False}
    save('docs/auditoria/evidencias/REGISTRO_CAMBIOS_DOCUMENTALES.json',json.dumps(record,ensure_ascii=False,indent=2))
    finish_report(record)
    package()
    validate_and_manifest()


def write_static():
    guide('docs/01_Proyecto/Descripcion_Proyecto.md','Descripción de DimaBug', '''
DimaBug reúne conocimiento para resolver fallas y gestionar solicitudes de soporte. Un usuario consulta contenido publicado o crea un ticket. El personal operacional mantiene conocimiento y procedimientos, atiende tickets y registra ejecuciones. La administración gestiona cuentas, catálogos y publicación según las reglas de cada recurso.

## Problema y solución observada

El código ofrece una base consultable de síntomas, causas, pruebas y soluciones; permite reutilizar información de resolución y dejar seguimiento mediante tickets y resultados de efectividad. La caracterización del problema institucional, usuarios reales, indicadores iniciales y validación con beneficiarios queda [PENDIENTE DE VERIFICACIÓN]. No se inventan entrevistas, ahorros o estadísticas.

## Alcance demostrado por código

Identidad y roles, catálogos, organización, conocimiento, soluciones/autoservicio, procedimientos, ejecuciones, resultados y Tickets. Hay un asistente de búsqueda de soluciones, avisos derivados de consultas y favoritos locales.

## Alcance limitado

Recuperación de contraseña carece de endpoints backend. Reportes contiene una serie mensual demostrativa; Nuevo error guarda información local, distinto de Tickets. No se encontró integración NFC, LLM, correo ni push. El snapshot histórico de pasos descrito en una guía previa no aparece implementado en este checkout. No se acredita despliegue ni MySQL actual.
''')
    guide('docs/01_Proyecto/Objetivos_Alcance.md','Objetivos y alcance', '''
## Objetivo técnico reconstruido

Centralizar conocimiento de soporte y solicitudes de atención mediante una aplicación web con control de acceso. Es una descripción derivada de la implementación, no reemplaza el objetivo formal aprobado del proyecto APT.

## Capacidades que materializan ese objetivo

- Consultar conocimiento publicado y soluciones de autoservicio.
- Mantener clasificación, pruebas y organización mediante operaciones autorizadas.
- Definir procedimientos y registrar cumplimiento y resultados.
- Reportar, asignar, conversar, resolver y cerrar tickets con historial.

## Exclusiones de la entrega documental

No se implementaron nuevas funciones ni se corrigieron riesgos de negocio, concurrencia o seguridad. No se alteraron esquema, endpoints, dependencias o configuración. No se ejecutaron migraciones, despliegues ni publicaciones.

## Criterios pendientes de acuerdo

Alcance institucional, rúbrica, RF/RNF aprobados, carga esperada, disponibilidad, RPO/RTO, accesibilidad objetivo y aceptación de usuarios: [PENDIENTE DE VERIFICACIÓN]. No hay base para declarar porcentajes de cumplimiento académico o cobertura funcional total.
''')
    guide('docs/01_Proyecto/Metodologia.md','Método y procedencia documental', '''
La documentación vigente parte del inventario y auditoría existentes. Se contrastaron contratos fuente, imports, entidades, migraciones, configuración, pruebas y documentos QA/ADR antes de redactar las guías. Las referencias históricas se conservan en sus ubicaciones y los originales modificados tienen copia verificable.

## Fases realizadas

1. Inventario sin modificar originales.
2. Auditoría estática y ejecución local de pruebas, con límites de aislamiento registrados.
3. Comparación y hallazgos priorizados.
4. Informe y parada a la espera de autorización.
5. Autorización explícita de la usuaria para organización, documentación y evidencias.
6. Guías vigentes, modelos, matriz, paquete académico y validación de enlaces/hash.

Los nombres de entregas QA y Skill 4/5 son etiquetas de documentos anteriores. No demuestran por sí solos Scrum, un número de sprints, fechas de aprobación o aplicación de una metodología institucional. La metodología de gestión formal, roles del equipo y ceremonias quedan [PENDIENTE DE VERIFICACIÓN].

Se usa «verificada» solo para el escenario y ambiente respaldados por evidencia. Lectura de código, pruebas con mocks/H2 y ensayo MySQL se identifican por separado.
''')
    guide('docs/03_Arquitectura/Arquitectura_General.md','Arquitectura general', '''
## Componentes implementados

Angular 19.2.25 es una SPA que consume la API REST Spring Boot 4.1.1/Java 21. La persistencia utiliza JPA y JDBC con MySQL como destino configurado. Los archivos se almacenan en filesystem, con referencias en BD.

```mermaid
flowchart LR
    U[Persona autenticada] --> F[SPA Angular]
    F -->|HTTP con Bearer JWT| S[Spring Security]
    S --> W[Controlador y DTO]
    W --> A[Caso de uso]
    A --> P[Puerto de salida]
    P --> J[Adaptador JPA o JDBC]
    J --> D[(MySQL)]
    P --> FS[Adaptador de archivos]
    FS --> V[(Filesystem privado)]
```

El diagrama representa el diseño observado, no una topología de producción certificada. Proxy local: puerto 9090. Producción frontend: mismo origen por defecto. Nginx/TLS/firewall/servicios instalados no se comprobaron.

## Monolito modular

Un POM y una aplicación agrupan Identidad, Catálogos, Conocimiento, Procedimientos, Ejecución, Seguimiento y Tickets. Shared proporciona contratos y controles transversales. No son microservicios ni despliegues independientes.

Se comprueban puertos/adaptadores y dependencia hacia modelos/contratos en los recorridos inspeccionados. Tickets tiene seis reglas ArchUnit. En otros módulos existen DTO y entidades compartidas, servicios concretos y dependencia Spring en Application. No se declara hexagonal pura ni aislamiento automático de todo el monolito.

## Fuentes

`dima-bug-backend/pom.xml`, `BackendApplication.java`, paquetes `application/port`, `infrastructure/persistence/adapter`, `tickets/arquitectura/TicketsArquitecturaTest.java` y `frontend-dimabug/src/app/app.config.ts`.
''')
    guide('docs/03_Arquitectura/Arquitectura_Backend.md','Arquitectura del backend', '''
## Responsabilidades

| Módulo | Contratos y comportamiento |
|---|---|
| identidad | Login, usuarios/roles, departamentos/contactos/responsables y API pública para integración |
| catalogo | Hardware, sistemas, módulos, frecuencias y asociaciones |
| conocimiento | Conocimiento, síntomas, causas, pruebas, soluciones/asignaciones, materiales, búsqueda y asistente autoservicio |
| procedimiento | Definiciones BORRADOR/PUBLICADO, pasos y materiales |
| ejecucion | Inicio, checklist, consulta por propietario/admin, completar y cancelar |
| seguimiento | Resultados y efectividad de soluciones |
| tickets | Agregado Ticket, casos de uso, comentarios, historial, etiquetas y adjuntos |
| shared | Seguridad HTTP, errores, visibilidad y contratos/adaptadores compartidos |

## Dependencias comprobadas

Domain no importa Spring/JPA ni Application/Infrastructure en la inspección realizada. Application consume puertos, pero utiliza anotaciones Spring y transacciones. Infrastructure implementa puertos y convierte DTO, dominio y entidades. Tickets expone puertos de entrada e integra otros módulos mediante sus paquetes `api`; el control ArchUnit es específico de este módulo.

Procedimiento reutiliza DTO de Conocimiento. Mappers/entidades y adaptadores de varios módulos comparten tipos de persistencia. Los controllers fuera de Tickets no tienen un puerto de entrada uniforme. Estos límites se conservan, sin rediseño documental ficticio.

## Recorrido real de creación de ticket

`TicketController.crear` valida `CrearTicketRequest` y llama a `CrearTicketUseCase`. `CrearTicketService` obtiene actor, rechaza administrador, valida clasificación, calcula código y crea el agregado. `TicketRepositoryAdapter` persiste cabecera e historial mediante Spring Data. El controller devuelve `TicketDetalleResponse` y declara HTTP 201.

La transacción de aplicación no elimina la carrera MAX+1 ni reemplaza control de edición concurrente. No hay snapshot de instrucciones en checklist actual. Los riesgos H-05/H-06 permanecen abiertos.

## Archivos y búsqueda

JPA/JDBC comparten transacciones cuando los servicios y DataSource lo permiten, pero no se comprobó MySQL en esta entrega. Filesystem queda fuera de la transacción BD. Materiales usa compensación; reindexaciones de organización/pruebas pueden ocurrir AFTER_COMMIT y quedar pendientes ante fallo, sin outbox durable identificado.
''')
    guide('docs/03_Arquitectura/Arquitectura_Frontend.md','Arquitectura del frontend', '''
## Organización

`src/app/features` contiene pantallas standalone. `core` agrupa modelos, HTTP, sesión, cachés, servicios y guards. `shared` contiene layout y UI reutilizada. Se identificaron 39 archivos component y 33 specs en la auditoría. Tickets y Chatbot cargan componentes de forma diferida; no toda la aplicación usa lazy loading.

## Rutas y permisos

`app.routes.ts` registra login/recuperar públicos mediante guestGuard y un shell con authGuard. adminGuard restringe Usuarios/Administración y staffGuard acciones operacionales. Hay redirecciones específicas de Tickets. `cambiosProcedimientoGuard` protege salida de editores. La API es responsable de la autorización definitiva.

## HTTP

`app.config.ts` registra HttpClient y authInterceptor. `api.ts` construye `/api` durante desarrollo y utiliza mismo origen en producción por defecto. `proxy.conf.json` apunta a 9090. El interceptor solo envía JWT a la API reconocida y conserva el rechazo de destinos externos.

## Estado y persistencia local

Se usan signals/computed y RxJS según el servicio. La sesión JWT vive en localStorage. Notificaciones y Tickets usan distintas cachés y marcas locales; no equivalen a una cola de notificaciones backend. Favoritos y caché de usuarios no tienen partición uniforme por cuenta. Un logout local no demuestra revocación del token en servidor.

## Funciones parciales

Recuperación envía requests a endpoints ausentes; los reportes incluyen datos literales y Nuevo error usa almacenamiento local. El asistente Chatbot recupera soluciones autoservicio mediante la API, sin SDK LLM. Los manuales conservan estas diferencias.
''')
    guide('docs/04_Base_Datos/Migraciones.md','Migraciones y provisión', '''
Los cinco SQL de `dima-bug-backend/src/main/resources/db/migration/` son archivos de ejecución manual. No hay runner Flyway/Liquibase ni DDL inicial completo en el checkout. No se ejecutaron durante la auditoría o la organización documental.

| Orden por nombre | Propósito | Precaución |
|---|---|---|
| V20260925_01 | Estado ELIMINADO de conocimiento | Requiere tabla y definición previa compatibles |
| V20261013_01 | Título, estado y autoservicio de solución | ALTER INSTANT sujeto a versión/motor/esquema |
| V20261014_01 | Versionado departamento/contacto y control organizacional | Revisar aplicación por sentencia; la fila de control es requisito del servicio |
| V20261017_01 | Tickets y relaciones internas | IF NOT EXISTS no compara una estructura existente |
| V20261017_02 | Nivel de etiqueta y adjuntos | Contiene DROP TABLE IF EXISTS ticket_adjunto: puede borrar metadatos existentes |

No se entrega un comando de aplicación masiva. La existencia de un archivo no demuestra que se haya aplicado ni que el esquema sea correcto.

## Procedimiento pendiente del DBA

Confirmar instancia autorizada, versión, esquema, procedencia del DDL base y copia de estructura saneada. Registrar restricciones y datos de ensayo, revisar cada sentencia y las que ya estén aplicadas, planificar conservación de adjuntos y probar restauración. Ejecutar únicamente en entorno aislado tras autorización independiente. No generar una falsa prueba de paridad creando esquema desde JPA y validándolo contra esas mismas entidades.

`V20261008_01__agregar_fk_usuarios_autoria_seguimiento.sql` y `V20261009_01__snapshot_pasos_ejecucion.sql` son referencias de documentos históricos y no están presentes. No se reconstruyen ni se marcan aplicadas.
''')
    guide('docs/05_API/Autenticacion.md','Autenticación y autorización API', '''
## Contratos presentes

`POST /api/auth/login` recibe email y password no vacíos. El DTO acepta alias usuario, username y correo para email y recorta identificador/clave. Devuelve `LoginResponse` con token, usuarioId, nombre, email y rol. `GET /api/auth/me` devuelve email y estado de autenticación cuando el request está autenticado. No hay endpoints implementados para `/api/auth/recuperar/*`.

Enviar `Authorization: Bearer <TOKEN>` a endpoints protegidos. El token no debe copiarse en evidencias, capturas, documentación o URLs. No hay un endpoint de refresh o revocación encontrado. Logout frontend elimina la sesión local.

## Comprobaciones del servidor

JJWT valida HMAC, subject y expiración. La clave exige 32 bytes UTF-8 como mínimo y duración positiva. BCrypt compara contraseñas. Actualmente el servicio comprueba usuario activo antes de contraseña; la guía histórica que afirmaba lo contrario no describe este árbol. Los roles se resuelven por usuario/repositorio y caché hasta cinco minutos, no por claims de rol aportados por el navegador.

La cadena distingue administración, consultas, escritura y recursos de Tickets. Los casos de uso agregan controles de propietario, actor y estado. `/error` aparece público en la configuración actual y los handlers usan sendError. El comportamiento Servlet completo debe probarse fuera de MockMvc antes de afirmar equivalencia con una corrección histórica.

## Convención de permisos

A = ADMINISTRADOR, T = TECNICO, S = SOPORTE, U = USUARIO. «Staff» no implica que administrador pueda atender tickets: en Tickets varias acciones reservadas a soporte/técnico rechazan expresamente al administrador. Véase Controles_Seguridad y el manual por perfil.
''')
    guide('docs/05_API/Errores_HTTP.md','Errores HTTP', '''
Fuente: GlobalExceptionHandler, TicketExceptionHandler y SecurityConfig. Los DTO de negocio normalmente usan un objeto `message`; los errores de filtro/Servlet pueden tener otro cuerpo. No se promete un formato uniforme para todos los despachos.

| Estado | Contratos encontrados | Acción de cliente |
|---|---|---|
| 400 | DTO inválido, JSON ilegible, regla de negocio, archivo/formato inválido, parámetro Ticket inválido | Corregir entradas, conservar formulario |
| 401 | Falta de autenticación, JWT inválido o credenciales inválidas | Revisar sesión sin repetir credenciales automáticamente |
| 403 | Rol/actor no autorizado, usuario inactivo | Informar falta de permiso, no simular éxito |
| 404 | Recurso inexistente u oculto por política; Ticket ajeno puede responder 404 | No inferir existencia de otro usuario |
| 409 | Duplicado, versión obsoleta, transición de Ticket o conflicto de integridad | Recargar y conciliar, evitar sobrescritura/reintento ciego |
| 413 | Request/archivo supera límite aceptado | Reducir archivo y revisar límites efectivos multipart/proxy |
| 500 | Almacenamiento o limpieza de archivos según handler | Distinguir registro confirmado de limpieza pendiente |
| 503 | Excepción de acceso/conexión a datos según handler global | Mostrar mensaje seguro y revisar servicio |

El estado por éxito se conserva según anotación o ResponseEntity de cada método. No se inventan errores ni payloads observados en un servidor real. Para Requests de creación con respuesta perdida, confirmar si el registro existe antes de reenviar; POST y pasos de procedimiento son operaciones independientes.
''')
    guide('docs/06_Seguridad/Arquitectura_Seguridad.md','Arquitectura de seguridad', '''
## Límites de confianza

Navegador, API autenticada, persistencia y almacenamiento de archivos son límites distintos. Los guards protegen navegación y el interceptor restringe destino de JWT. La API valida token y aplica reglas HTTP, visibilidad y propietario/actor. BD y filesystem requieren permisos y protección operacional no acreditados por el código.

Las sesiones son STATELESS con Bearer JWT. CSRF se desactiva en esa configuración. No hay CORS explícito encontrado; desarrollo usa proxy y producción mismo origen por defecto. No se declara CORS abierto ni seguridad de TLS/firewall sin consultar el entorno autorizado.

## Roles

ADMINISTRADOR gestiona cuentas y recursos administrativos y publica conocimiento/procedimientos. TECNICO y SOPORTE mantienen operaciones habilitadas y atienden Tickets. USUARIO consulta publicados y opera sus tickets. Las excepciones de método y estado se detallan en Controles_Seguridad y manual. El registro de rol activo no se comprueba explícitamente en el filtro JWT.

## Riesgos vigentes

Enumeración de cuenta inactiva; token persistente sin revocación; cachés entre cuentas/aliases; logs DEBUG con email si se habilitan; inconsistencias BD/archivos/proyección; dependencias npm afectadas. No se corrigieron como parte documental. Los avisos públicos y su contexto están en la auditoría, no son prueba de explotación del sistema.

No se publica un volcado de variables, credenciales o respuestas de login. La inspección documental no acredita ausencia de secretos en todo el historial Git o sistemas externos.
''')
    guide('docs/06_Seguridad/Controles_Seguridad.md','Controles y riesgos de seguridad', '''
## Matriz de controles implementados

| Recurso/acción | Control del código |
|---|---|
| /api/admin/** | ADMINISTRADOR antes de reglas generales |
| GET conocimiento, hardware, sistemas, frecuencias y procedimientos | A/T/S/U; visibilidad adicional de borradores/eliminados |
| Escritura operacional de catálogos/conocimiento/procedimientos y ejecuciones | A/T/S según matchers; publicación de conocimiento/procedimiento exige A |
| DELETE de conocimiento | Solo A, borrado lógico |
| /api/usuarios/** y /api/roles/** | A |
| POST /api/chatbot/consultas | A/T/S/U |
| /api/tickets y descendientes | A/T/S/U a nivel HTTP, actor/propietario/estado se valida en casos de uso |
| GET /api/etiquetas | A/T/S/U |
| Escritura /api/etiquetas/** | S/T |
| GET /, /api/health, /api/auth/login y /error según matcher | Rutas públicas declaradas; método real depende del controller |

La matriz de matchers no autoriza todas las acciones al mismo perfil. El administrador supervisa Tickets; las acciones de atención pueden rechazarlo. Un usuario ve sus tickets y no las notas internas del personal.

## Archivos

Materiales verifica MIME/firma y límite de 10 MB en código; Tickets admite hasta 25 MB en su contrato. No se comprobó que límites multipart/proxy desplegados permitan esos tamaños. Las descargas pasan por controles de pertenencia/visibilidad. Se mantiene riesgo operativo de huérfanos, crash y limpieza fallida.

## Dependencias y OWASP

La auditoría npm del 06-11-2026 informó 54 entradas afectadas, cuatro críticas en dependencias transitivas dev. Revisar paquete/ruta, versión y exposición antes de decidir actualizaciones. No se ejecutó audit fix ni se modificó el lockfile. No hay escaneo SCA Maven completo. Referencia de taxonomía: OWASP Top 10:2025, enlazada en la auditoría original.

## Validaciones pendientes

HTTP real y redespacho de errores, revocación/cambio de rol entre instancias, expiración visible en frontend, limpieza/partición de caché, rate limiting externo, TLS/CORS efectivo, mínimo privilegio MySQL y almacenamiento, revisión de secretos y restauración probada. [PENDIENTE DE VERIFICACIÓN] donde dependa del entorno o de decisiones del equipo.
''')
    guide('docs/07_Pruebas/Estrategia_Pruebas.md','Estrategia y alcance de pruebas', '''
## Capas existentes

| Clase de evidencia | Herramientas/escenario | Alcance |
|---|---|---|
| Unitarias backend | JUnit/Mockito y puertos simulados | Reglas, interacción y casos de uso |
| Slices web | WebMvcTest/MockMvc con servicios o puertos simulados | DTO, controllers y cadena de seguridad en contexto de test |
| Arquitectura | ArchUnit, seis reglas Tickets | Dependencias específicas del módulo |
| Persistencia local | Dos clases JPA H2 en memoria | Copia concurrente de solución y materiales sin OSIV, no InnoDB/FULLTEXT |
| Frontend | Jasmine/Karma/ChromeHeadless | Componentes, servicios, HTTP simulado, guards y navegación probada |
| Compilación | TypeScript noEmit | Tipado de aplicación y specs |
| Funcionales E2E / UAT | No disponibles en este árbol | [PENDIENTE DE VERIFICACIÓN] |

La palabra IntegrationTest en un nombre no prueba que se conecte a MySQL. Las cinco IT Testcontainers citadas en guías históricas no están presentes.

## Ejecución local de referencia

Backend: `mvnw.cmd -o test`, desde su carpeta, con dependencias Maven ya existentes y Java 21. Frontend: `npm.cmd test -- --watch=false --browsers=ChromeHeadless`, con paquetes ya instalados. Los comandos fueron ejecutados en la auditoría, no se repitieron por cambios exclusivamente documentales.

## Próximos ensayos necesarios

Checkout limpio/configuración reproducible, MySQL aislado con DDL revisado, FULLTEXT, correlativo/edición simultánea Tickets, cierre/checklist, conservación de historia, reindexación postcommit, recuperación BD/filesystem, UAT por perfil y accesibilidad. No hay autorización implícita de crear bases o ejecutar migraciones.
''')
    totals=EVIDENCE['backend']['totals']
    rows=['| Clase | Ejecutadas | Fallos | Errores | Omitidas |','|---|---|---|---|---|']
    for x in EVIDENCE['backend']['suites']:
        rows.append('| '+x['name']+' | '+' | '.join(str(x[k]) for k in ['tests','failures','errors','skipped'])+' |')
    guide('docs/07_Pruebas/Resultados_Pruebas.md','Resultados verificables de pruebas', f'''
Fecha del ensayo: {DAY}, America/Santiago. Rama `codex/integracion-mvp`, HEAD `{HEAD}`, con cambios locales anteriores descritos en auditoría. No es resultado de CI remoto ni certificación del artefacto desplegado.

| Comprobación | Resultado | Evidencia |
|---|---|---|
| Backend, Maven offline | 920 ejecutadas, 0 fallos, 0 errores, 0 omitidas | 92 XML Surefire y resumen COMPROBACIONES.json |
| Angular, Chrome Headless 157 | TOTAL: 284 SUCCESS, salida 0 | Salida de herramienta resumida en COMPROBACIONES.json |
| TypeScript aplicación | Salida 0 | tsc noEmit, tsconfig.app.json |
| TypeScript specs | Salida 0 | tsc noEmit, tsconfig.spec.json |
| npm audit | 54 entradas afectadas: 4 críticas, 20 altas, 28 moderadas, 2 bajas | DEPENDENCIAS_NPM.json |

Backend terminó 23:11:44 -03:00 con BUILD SUCCESS, tiempo Maven 02:13. Usó target incremental existente; no se declara compilación limpia. Java 21.0.12.1, Maven 3.9.16, Boot 4.1.1. Los intentos iniciales restringidos se interrumpieron por el agente JVM/lanzador de navegador; no cuentan como PASS o defecto funcional. La repetición autorizada fuera del sandbox completó las suites.

Los logs originales pueden contener valores sintéticos de autenticación: no se distribuyen. Se entrega un resumen saneado con conteos y hashes; la evidencia de frontend es un resumen de salida, no un XML JUnit inventado. Los XML backend generados se conservaron en target local, fuera de la entrega académica.

No se ensayó MySQL, STAGING, UAT, carga, pentest, restauración o despliegue. No hay porcentaje de cobertura calculado. Warnings de Mockito y 404 del logo en Karma no alteraron el resultado final; siguen documentados como límites.

## Detalle por clase backend

{chr(10).join(rows)}
''')
    guide('docs/08_Despliegue/Instalacion_Local.md','Instalación y ejecución local', '''
## Requisitos

Java 21 y Maven Wrapper del repositorio; Node/npm compatibles con Angular 19 y el lockfile; paquetes descargados si se pretende modo offline; esquema MySQL revisado y directorios privados de almacenamiento. Versiones usadas por la auditoría: Java 21.0.12.1, Maven 3.9.16 y Node 24.15.0. Son versiones observadas, no una matriz completa de soporte certificada.

## Preparación

Trabajar desde la raíz del repositorio confirmado. Las instrucciones de instalación no fueron ejecutadas durante la fase documental. La autorización de documentación no incluye instalar paquetes o crear una base. No ejecutar npm install/ci o una descarga Maven sin el permiso correspondiente en un entorno restringido.

## Backend

El archivo compartido application.properties no está presente y sigue ignorado. El perfil staging sí mapea DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, JWT_EXPIRATION y fija server.port=9090. No usar credenciales de staging para desarrollo.

Para una ejecución local aprobada se pueden suministrar directamente las propiedades estándar por el proceso/IDE, evitando modificar archivos:

```text
SPRING_DATASOURCE_URL=<JDBC_LOCAL_AUTORIZADO>
SPRING_DATASOURCE_USERNAME=<USUARIO_LOCAL>
SPRING_DATASOURCE_PASSWORD=<SECRETO_LOCAL_NO_VERSIONADO>
JWT_SECRET=<CLAVE_LOCAL_DE_AL_MENOS_32_BYTES>
JWT_EXPIRATION=<MILISEGUNDOS_POSITIVOS>
SERVER_PORT=9090
SPRING_JPA_HIBERNATE_DDL_AUTO=none
SPRING_SQL_INIT_MODE=never
DIMABUG_STORAGE_MATERIALES_DIR=<RUTA_PRIVADA_LOCAL>
DIMABUG_STORAGE_TICKETS_DIR=<RUTA_PRIVADA_LOCAL>
```

JWT_SECRET y JWT_EXPIRATION enlazan con jwt.secret/jwt.expiration por resolución de propiedades de Spring. Los nombres DB_* solo se mapean en el perfil presente; no equivalen por sí solos a spring.datasource.* en un arranque sin ese recurso. Los secretos se definen en el proceso/IDE por un mecanismo seguro, no en esta guía ni en argv.

Con esquema y destino local aprobados, el comando de arranque sería `mvnw.cmd spring-boot:run` desde dima-bug-backend. No fue ejecutado en esta fase ni se acredita el arranque completo. Nunca cambiar ddl-auto a create/update para ocultar un esquema faltante.

## Frontend

Con dependencias existentes, ejecutar `npm start` desde frontend-dimabug y abrir localhost:4200. `/api` se reenvía a localhost:9090. Verificar que proxy y puerto real coincidan. Producción usa configuración diferente y no depende del proxy de ng serve.

## Comprobaciones

El health del código no sustituye una prueba de todas las tablas/índices. Revisar login, consultas y escritura con datos sintéticos únicamente cuando se autorice el ambiente. Para pruebas aisladas existentes, ver la estrategia y resultados; no iniciar MySQL real para probar que el comando compila.
''')
    guide('docs/08_Despliegue/Variables_Entorno.md','Variables y configuración', '''
## Origen de configuración comprobado

Solo existe application-staging.properties como configuración funcional fuente. Su cambio previo se preservó. No hay carga automática comprobada de archivos .env arbitrarios. Un servicio debe heredar las variables desde el mecanismo que opere el equipo; no se presume systemd, Docker o IDE específico instalado.

| Propiedad / variable | Uso | Valor o límite documentable |
|---|---|---|
| spring.datasource.url / SPRING_DATASOURCE_URL | URL JDBC | Sin URL real en la documentación |
| spring.datasource.username / SPRING_DATASOURCE_USERNAME | Usuario BD | Mínimo privilegio a definir por DBA |
| spring.datasource.password / SPRING_DATASOURCE_PASSWORD | Credencial BD | Secreto externo, nunca en evidencias |
| DB_URL, DB_USERNAME, DB_PASSWORD | Placeholders del perfil staging | No hay recurso compartido que los mapee fuera de ese perfil |
| jwt.secret / JWT_SECRET | Clave HMAC | Obligatoria, al menos 32 bytes UTF-8 |
| jwt.expiration / JWT_EXPIRATION | Vigencia | Valor positivo en milisegundos; no consta default funcional en properties actuales |
| server.port / SERVER_PORT | Puerto HTTP | Perfil staging declara 9090 |
| dimabug.storage.materiales-dir / DIMABUG_STORAGE_MATERIALES_DIR | Archivos de materiales | Adaptador declara default relativo ./uploads/materiales |
| dimabug.storage.tickets-dir / DIMABUG_STORAGE_TICKETS_DIR | Adjuntos de Tickets | Adaptador declara default relativo ./uploads/tickets |
| spring.jpa.hibernate.ddl-auto | Política DDL | none propuesto para ejecución revisada, no fijado por el archivo staging presente |
| spring.sql.init.mode | Inicialización SQL | never propuesto para ejecución revisada, no prueba de configuración desplegada |

Los defaults de adaptador no acreditan permisos de disco ni aislamiento entre entornos. Las rutas relativas dependen del directorio de trabajo. Multipart, límites de proxy, TLS, logs y orígenes efectivos del despliegue quedan [PENDIENTE DE VERIFICACIÓN].

No se inspeccionan o copian valores del entorno del usuario, archivos .env reales o credenciales. La guía distingue configuración actual de recomendaciones para un arranque futuro.
''')
    guide('docs/08_Despliegue/Despliegue_STAGING.md','Preparación de STAGING', '''
**Estado: despliegue no ejecutado ni certificado.** Esta guía organiza las condiciones previas documentadas por QA015/QA016 y handoff. No convierte esas propuestas en evidencia de un servidor instalado o actualizado.

## Puertas antes de un GO

1. Confirmar versión exacta MySQL, instancia/esquema autorizado y DDL base saneado.
2. Resolver discrepancias de snapshot, integración MySQL y CI entre documentación y checkout.
3. Validar los cinco scripts por sentencia y conservación de datos, especialmente DROP de adjuntos.
4. Reproducir persistencia, FULLTEXT, transacciones y carreras prioritarias en ambiente aislado.
5. Revisar dependencias afectadas y riesgos de archivos/índice, con decisión formal del equipo.
6. Acreditar respaldo/restauración de BD y uploads y compatibilidad semántica del retorno de aplicación.
7. Definir build/release, SHA, checksums y correspondencia entre Angular, backend y esquema.
8. Verificar TLS, proxy SPA/API, límites de archivo, permisos, variables externas y monitoreo.
9. Obtener UAT por perfil y aprobación específica de publicación/SQL.

## Artefactos previstos, no generados aquí

JAR ejecutable, contenido completo del build Angular de producción y configuración externa. No se reutilizan checksums históricos como si fueran archivos actuales. No se incluye .jar.original como entregable ejecutable. Las instrucciones de servicio, rutas del servidor, DNS y responsable quedan [PENDIENTE DE VERIFICACIÓN].

## Retorno

Un rollback de aplicación no deshace DDL ni garantiza compatibilidad con estados/contratos nuevos. No se entrega un DROP de reversión ni una restauración automática. Preferir recuperación hacia adelante cuando corresponda, con DBA y operación autorizados. Nunca ejecutar una migración o despliegue por leer esta guía.
''')
    write_manuals()


def write_manuals():
    guide('docs/09_Manuales/Manual_Usuario.md','Manual de usuario', '''
## Acceso y sesión

Entrar por /login con cuenta habilitada. El sistema conserva una sesión local con JWT. Cerrar sesión al terminar, especialmente en un equipo compartido. La pantalla /recuperar existe, pero el backend de recuperación no está implementado: contactar a la administración por el canal que defina el equipo, [PENDIENTE DE VERIFICACIÓN].

## Usuario

1. Abrir Conocimiento y buscar por texto o clasificación. Consultar los contenidos publicados y sus soluciones disponibles.
2. Usar Chatbot para consultar soluciones autoservicio. Sus respuestas recuperan contenido existente, no proceden de un modelo generativo externo.
3. Marcar favoritos si resulta útil. Se guardan en ese navegador y no se acredita sincronización entre equipos o cuentas.
4. Crear un ticket desde Tickets/Nuevo: ingresar título (máximo 150), descripción, prioridad y clasificación si corresponde. El código de seguimiento lo genera el servidor.
5. Abrir su ticket, leer/responder conversación y adjuntar archivos admitidos. Las notas internas de soporte no son visibles para el usuario.
6. Cuando soporte lo resuelva, revisar solución y confirmar cierre o solicitar reapertura según las acciones habilitadas. No puede acceder a tickets de otra persona.

## Técnico y soporte

Trabajar desde la bandeja, consultar filtros/prioridades, tomar/asignar según el caso de uso, registrar conversación, notas internas y solución. Los estados del dominio incluyen ABIERTO, EN_PROGRESO, EN_ESPERA, RESUELTO, CERRADO y CANCELADO; el servidor valida actor y transición. El técnico asignado puede confirmar cierre de un resuelto según el contrato actual. No interpretar un comentario antiguo o una etiqueta visual como autorización del servidor.

Mantener conocimiento y borradores de procedimientos según permisos. En un procedimiento, primero guardar cabecera BORRADOR y luego cada paso. Si un paso falla, los anteriores confirmados permanecen. Usar las opciones de consulta/recuperación de la pantalla antes de repetir una creación incierta. Los pasos usan orden positivo y pueden tener saltos; posición y orden no son lo mismo.

Iniciar una ejecución solo sobre un procedimiento PUBLICADO con pasos. Marcar checklist y completar cuando todos estén cumplidos; cancelar si corresponde. El propietario/admin puede acceder según el contrato; la definición publicada es inmutable. No se ofrece snapshot histórico independiente de las instrucciones actuales.

## Administración

Gestionar usuarios/roles asignados, estados y catálogos; mantener departamentos/contactos/responsables y pruebas desde las pantallas administrativas. Publicar conocimiento/procedimientos solo con el rol autorizado. El sistema protege al último administrador activo y rechaza escrituras de definición de procedimientos PUBLICADOS.

En Tickets el administrador supervisa y puede asignar por el contrato correspondiente, pero no es un técnico de atención: crear o resolver tickets y otras acciones operacionales pueden rechazarlo. Los controles concretos provienen del caso de uso, no solo del menú visible.

## Avisos, reportes y otras pantallas

Los avisos se calculan a partir de consultas y marcas de lectura del navegador; no se acredita correo, push o entrega instantánea. Reportes contiene una serie mensual demostrativa y debe interpretarse con ese límite. Nuevo error conserva registros locales y no garantiza que otra sesión de soporte los reciba: para una solicitud compartida usar Tickets.

## Mensajes y recuperación

Ante 400, corregir datos; ante 403, revisar permiso; ante 409, recargar y conciliar. No ocultar filas ni anunciar éxito después de una operación rechazada. Si una respuesta de creación se pierde, comprobar si se guardó antes de repetir. Ante error de limpieza de archivos, el registro BD puede haberse confirmado y requerir mantenimiento operacional.

Los tamaños de código son hasta 10 MB en materiales y 25 MB en Tickets, sujetos a límites multipart/proxy efectivos no verificados. El sistema rechaza formato/tamaño no permitido. No se incluyen capturas ficticias: la UAT visual y accesibilidad en dispositivos reales siguen pendientes.
''')
    guide('docs/09_Manuales/Manual_Tecnico.md','Manual técnico', '''
## Punto de entrada

Usar el índice vigente para arquitectura, instalación, configuración, API y datos. El repositorio contiene frontend-dimabug y dima-bug-backend bajo una sola raíz. El backend usa Maven Wrapper/Java 21; frontend Angular CLI con lockfile. Las versiones exactas auditadas están en README y resultados.

## Ejecución y configuración

Seguir Instalacion_Local y Variables_Entorno. El recurso compartido application.properties falta en el checkout; el perfil staging y las propiedades estándar son rutas de configuración diferentes. No llenar esa ausencia con credenciales por defecto. Revisar instancia, esquema, uploads y puerto antes de un arranque autorizado. No se ejecuta el backend contra una base para descubrir a cuál conecta.

## Contratos de mantenimiento

No confundir entidades JPA con el DDL físico. El diccionario indica origen y límites por columna. La búsqueda requiere una proyección JDBC y FULLTEXT cuyo DDL no está incluido. Scripts parciales no son un procedimiento de provisión completo. No usar create/update de Hibernate para hacer pasar una verificación física.

Los controllers convierten DTO y delegan a casos de uso/servicios. Cambios futuros deben conservar puertos y límites de Tickets y revisar acoplamientos existentes. No existe control ArchUnit global del monolito. Respetar inmutabilidad de PUBLICADOS, permisos y pertenencia de hijos.

## Diagnóstico

Usar errores HTTP seguros y trazas restringidas. DEBUG de JWT puede contener email/URI/rol: confirmar nivel antes de compartir logs. No adjuntar tokens, passwords, hashes de cuentas reales, env, dumps o reportes sin saneamiento. Una excepción SQL no debe publicarse como mensaje de usuario.

Archivos y BD requieren recuperación coordinada. La compensación no equivale a una transacción distribuida; una limpieza/reindexación postcommit puede quedar pendiente. Definir reconciliación y registrar incidencias sin borrar datos para ocultar fallos. Ninguna tarea destructiva está automatizada por esta documentación.

## Pruebas y releases

Consultar Estrategia_Pruebas y Resultados_Pruebas. La suite local aprobada no prueba MySQL, carga o el despliegue. Una entrega posterior debe relacionar commit/working tree, build limpio, lockfile, JAR/Angular/esquema, checksums, ensayos y UAT. No hay workflow ejecutable de CI en este árbol aunque existan guías históricas.

## Pendientes operacionales

Propietarios del sistema, canal de incidentes, RPO/RTO, rotación de claves, backups/restauración, límites de producción, observabilidad y aprobaciones académicas: [PENDIENTE DE VERIFICACIÓN]. No se inventan runbooks con nombres de unidades/rutas/servidores no confirmados.
''')


def balanced_end(text,start):
    depth=0;quote=None;escaped=False
    for i in range(start,len(text)):
        ch=text[i]
        if quote:
            if escaped:escaped=False
            elif ch=='\\':escaped=True
            elif ch==quote:quote=None
            continue
        if ch in ['"',"'"]:quote=ch;continue
        if ch=='(':depth+=1
        elif ch==')':
            depth-=1
            if depth==0:return i+1
    raise ValueError('Paréntesis incompleto')


def columns_java(p):
    s=p.read_text(encoding='utf-8-sig')
    for annot in ['JoinTable','CollectionTable']:
        while (m:=re.search('@'+annot+r'\s*\(',s)):
            end=balanced_end(s,s.index('(',m.start()))
            s=s[:m.start()]+s[end:]
    fields=[]
    for m in re.finditer(r'@(Column|JoinColumn)\s*\(',s):
        end=balanced_end(s,s.index('(',m.start()));a=s[m.start():end]
        name=re.search(r'name\s*=\s*"([^"]+)"',a)
        tail=s[end:s.find(';',end)+1]
        field=re.search(r'private\s+([\w<>.?]+)\s+(\w+)',tail)
        if not name or not field or field[1].startswith(('Set<','List<')):continue
        segment=s[max(s.rfind(';',0,m.start()),s.rfind('{',0,m.start()))+1:m.start()]
        fields.append({'name':name[1],'java':field[1],'nullable':not bool(re.search(r'nullable\s*=\s*false',a)),
                       'length':int(x[1]) if (x:=re.search(r'length\s*=\s*(\d+)',a)) else None,
                       'column_definition':x[1] if (x:=re.search(r'columnDefinition\s*=\s*"([^"]+)"',a)) else None,
                       'unique':bool(re.search(r'unique\s*=\s*true',a)),
                       'jpa_relation':m[1]=='JoinColumn','version':'@Version' in segment,
                       'pk':'@Id' in segment,'source':p.relative_to(ROOT).as_posix()})
    if '@EmbeddedId' in s:
        fields=[{'name':x,'java':'Integer','nullable':False,'pk':True,'jpa_relation':x=='prueba_id','length':None,'column_definition':None,'unique':False,'version':False,'source':p.relative_to(ROOT).as_posix()} for x in ['conocimiento_id','prueba_id']]+[f for f in fields if f['name'] not in ['conocimiento_id','prueba_id']]
    fields=list({f['name']:f for f in fields}.values())
    # Identifier nullability follows the JPA identity contract even when the
    # Column annotation omits nullable=false. This does not certify physical DDL.
    for f in fields:
        if f['pk']:f['nullable']=False
    return fields


def write_database():
    tables={};sqlcols={};fks={}
    for p in sorted(BASE.rglob('*Entity.java')):
        txt=p.read_text(encoding='utf-8-sig');name=re.search(r'@Table\(name\s*=\s*"([^"]+)',txt)[1]
        tables[name]={'origin':'JPA','source':p.relative_to(ROOT).as_posix(),'fields':columns_java(p)}
    for name,cols in [('cl_hardware_sistema',['hardware_id','sistema_id']),('ticket_ticket_etiqueta',['ticket_id','etiqueta_id'])]:
        tables[name]={'origin':'Unión JPA','source':'mapeo HardwareEntity / TicketEntity','fields':[{'name':c,'java':'Integer' if name.startswith('cl_') else 'Long','nullable':None if name.startswith('cl_') else False,'pk':name.startswith('ticket'),'jpa_relation':name.startswith('cl_'),'length':None,'column_definition':None,'unique':False,'version':False} for c in cols]}
    tables['co_conocimiento_busqueda']={'origin':'JDBC','source':(BASE/'conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java').relative_to(ROOT).as_posix(),
        'fields':[{'name':c,'java':'Integer (parámetro JDBC)' if c=='conocimiento_id' else 'String (parámetro JDBC)','nullable':None,'pk':False,'jpa_relation':False,'length':None,'column_definition':None,'unique':False,'version':False} for c in ['conocimiento_id','contenido_busqueda']]}
    assert len(tables)==31
    for p in sorted((ROOT/'dima-bug-backend/src/main/resources/db/migration').glob('*.sql')):
        s=p.read_text(encoding='utf-8-sig');s=re.sub(r'--[^\n]*','',s)
        for m in re.finditer(r'CREATE TABLE(?: IF NOT EXISTS)?\s+(\w+)\s*\(([\s\S]*?)\)\s*ENGINE',s,re.I):
            table=m[1]
            for l in m[2].splitlines():
                c=re.match(r'\s*(\w+)\s+([A-Z]+(?:\([^)]*\))?)(.*)',l)
                if c and c[1].upper() not in ['PRIMARY','CONSTRAINT','UNIQUE','KEY','CHECK']:
                    sqlcols[(table,c[1])]={'declaration':l.strip().rstrip(','),'source':p.relative_to(ROOT).as_posix(),'type':c[2],'nullable':'NOT NULL' not in c[3].upper()}
            for fk in re.finditer(r'CONSTRAINT\s+(\w+)\s+FOREIGN KEY\s*\((\w+)\)\s+REFERENCES\s+(\w+)\s*\((\w+)\)',m[2],re.I):fks[(table,fk[2])]={'constraint':fk[1],'parent':fk[3],'parent_column':fk[4],'source':p.relative_to(ROOT).as_posix()}
        for m in re.finditer(r'ALTER TABLE\s+(\w+)\s+([^;]+)',s,re.I):
            for c in re.finditer(r'ADD COLUMN\s+(\w+)\s+([A-Z]+(?:\([^)]*\))?)([^,;]*)',m[2]):
                sqlcols[(m[1],c[1])]={'declaration':c[0],'source':p.relative_to(ROOT).as_posix(),'type':c[2],'nullable':'NOT NULL' not in c[3].upper()}
    relations=[]
    old=(ROOT/'entregables/modelo-relacional/LEEME.md').read_text(encoding='utf-8')
    for line in old.splitlines():
        cells=[x.strip() for x in line.strip('|').split('|')]
        if len(cells)==3 and cells[0] in tables and cells[1] in tables:
            a,b,c=cells;field=next(f for f in tables[b]['fields'] if f['name']==c)
            kind='SQL' if (b,c) in fks else 'JPA' if field['jpa_relation'] else 'REF'
            relations.append({'parent':a,'child':b,'column':c,'kind':kind,'optional':field['nullable']})
    relations.append({'parent':'co_conocimiento','child':'co_conocimiento_busqueda','column':'conocimiento_id','kind':'JDBC','optional':None})
    assert len(fks)==6
    text='''
## Origen y lectura

Este diccionario describe **31 tablas requeridas**: 28 entidades, dos uniones y una proyección JDBC. Es un diccionario lógico/técnico del código, no un export del servidor. La columna Java y los atributos JPA se leen de las entidades; el SQL se copia como declaración de los scripts existentes. No se transforma una anotación en una restricción física confirmada.

PK = clave declarada por el modelo; JPA = JoinColumn/relación del mapeo; SQL = FOREIGN KEY explícita en un script; REF = ID sin FK SQL confirmada; JDBC = uso en consultas sin DDL disponible. Para co_conocimiento_busqueda se conocen dos columnas usadas por el adaptador, pero PK, tipos físicos, nulabilidad e índice FULLTEXT quedan [PENDIENTE DE VERIFICACIÓN]. La asociación única por conocimiento es una expectativa del upsert, no un UNIQUE instalado acreditado.

`String` sin length explícito se mantiene como String/JPA por defecto, sin adivinar VARCHAR físico. `columnDefinition` y length son declaración JPA, no tipo del servidor. Los tipos ENUM de Java se documentan desde su enum y no se convierten automáticamente en ENUM MySQL. El registro no incluye datos de usuarios ni hashes de contraseñas.

Los identificadores @Id/@EmbeddedId no admiten nulos en el contrato de identidad, aunque su @Column omita nullable=false. En cl_hardware_sistema no se certifican PK ni nulabilidad física a partir del Set y JoinTable. La tabla de etiquetas de Tickets sí tiene PK compuesta/NOT NULL en el script. Estos niveles se identifican sin afirmar ejecución del DDL.
'''
    for name,t in sorted(tables.items()):
        text+=f'\n## {name}\n\nOrigen: {t["origin"]}. Fuente: `{t["source"]}`.\n\n'
        text+='| Columna | Tipo Java / JPA | Nulo en modelo o SQL declarado | Clave o referencia declarada | Atributos JPA | SQL disponible, no ejecutado |\n|---|---|---|---|---|---|\n'
        for f in t['fields']:
            attrs=[]
            if f['length']:attrs.append('length='+str(f['length']))
            if f['column_definition']:attrs.append('columnDefinition='+f['column_definition'])
            if f['unique']:attrs.append('unique=true')
            if f['version']:attrs.append('@Version')
            flags=['PK'] if f['pk'] else []
            rr=[r for r in relations if r['child']==name and r['column']==f['name']]
            flags += [r['kind']+' a '+r['parent'] for r in rr]
            sc=sqlcols.get((name,f['name']));decl=clean(sc['declaration']) if sc else '[PENDIENTE DE VERIFICACIÓN]'
            text+='| `'+f['name']+'` | '+f['java']+' | '+('No determinado' if f['nullable'] is None else 'Sí' if f['nullable'] else 'No')+' | '+('; '.join(flags) or '—')+' | '+(', '.join(attrs) or 'Sin atributo adicional extraído')+' | '+decl+' |\n'
    text+='\n## Restricciones de conjunto\n\npr_paso declara UNIQUE(procedimiento_id, paso_orden); se_ejecucion_paso declara UNIQUE(ejecucion_id, paso_id); ConocimientoPruebaId declara PK compuesta. cl_hardware_sistema usa columnas de unión; no se certifica una PK física por inferirla del Set JPA. ticket_ticket_etiqueta tiene PK compuesta explícita en el SQL presente. Las seis FK SQL internas de Tickets están listadas en Modelo_Relacional. No se dispone de índices/collation/defaults completos del servidor.\n'
    guide('docs/04_Base_Datos/Diccionario_Datos.md','Diccionario de datos',text)
    text='''
## Alcance

El modelo distingue JPA, FK explícitas en SQL y referencias por ID. Incluye co_conocimiento_busqueda (JDBC), omitida en el entregable previo. No se conectó a MySQL ni se modificó el esquema. Los archivos anteriores de entregables se conservan como versión histórica.

La cardinalidad es una lectura del contrato: un padre puede tener cero o muchos hijos; cada hijo requiere uno o admite cero/uno según nulabilidad declarada. No expresa filas existentes. La proyección JDBC espera como máximo un documento por conocimiento, sin certificar unicidad física.

Archivos: [Draw.io editable](../03_Arquitectura/Diagramas/DimaBug_Modelo_Relacional_v2.drawio), [imagen PNG](../03_Arquitectura/Diagramas/DimaBug_Modelo_Relacional_v2.png) y [SVG ampliable](../03_Arquitectura/Diagramas/DimaBug_Modelo_Relacional_v2.svg).

## Relaciones y opcionalidad

| Padre | Hija | Columna hija | Evidencia | Padre por fila hija | Hijos por padre |
|---|---|---|---|---|---|
'''
    for r in relations:
        cardinal='No confirmado' if r['optional'] is None else '0..1' if r['optional'] else '1'
        many='0..1 esperado, no DDL' if r['kind']=='JDBC' else '0..N'
        text+=f'| {r["parent"]} | {r["child"]} | {r["column"]} | {r["kind"]} | {cardinal} | {many} |\n'
    text+='\n## FK explícitas en los scripts\n\n| Hija y columna | Padre | Constraint | Fuente |\n|---|---|---|---|\n'
    for (t,c),fk in sorted(fks.items()):text+=f'| {t}.{c} | {fk["parent"]}.{fk["parent_column"]} | {fk["constraint"]} | `{fk["source"]}` |\n'
    text+='\nNo hay FK SQL hacia usuarios/catálogos/conocimiento en los scripts de Tickets. La relación Conocimiento–Prueba y Hardware–Sistema son N:M mediante tablas puente. La relación Solución–Procedimiento directa no se encuentra mapeada. La definición de pasos no tiene snapshot capturado por ejecución en este árbol.\n'
    guide('docs/04_Base_Datos/Modelo_Relacional.md','Modelo relacional y origen de relaciones',text)
    save('docs/auditoria/evidencias/MODELO_LOGICO.json',json.dumps({'tables':tables,'relations':relations,'sql_foreign_keys':[dict(child=t,column=c,**f) for (t,c),f in fks.items()],'physical_mysql_verified':False},ensure_ascii=False,indent=2))
    return tables,relations


def permission(method,path):
    if path.startswith('/api/admin/'):return 'A'
    if path.startswith('/api/tickets'):return 'A/T/S/U + actor, propietario y estado'
    if path.startswith('/api/chatbot/'):return 'A/T/S/U'
    if path.startswith('/api/etiquetas'):return 'A/T/S/U' if method=='GET' else 'S/T'
    if method=='DELETE' and re.fullmatch('/api/conocimientos/[^/]+',path):return 'A'
    if method=='GET' and any(path.startswith('/api/'+x) for x in ['conocimientos','hardware','sistemas','frecuencias','procedimientos']):return 'A/T/S/U + visibilidad'
    if any(path.startswith('/api/'+x) for x in ['sistemas','hardware','conocimientos','frecuencias','departamentos','pruebas','procedimientos','ejecuciones']):return 'A/T/S + reglas adicionales'
    if path.startswith(('/api/usuarios','/api/roles')):return 'A'
    if path in ['/','/api/health','/api/auth/login','/error']:return 'Público según método/matcher'
    return 'Autenticación requerida'


def write_api():
    text='''
## Lectura del catálogo

Se documentan **124 mappings de método** extraídos de 37 controladores. Son rutas del código, no endpoints probados en el servidor desplegado. A/T/S/U se definen en Autenticacion. La columna de acceso reproduce la cadena HTTP y advierte controles adicionales; no reemplaza la lógica de cada caso de uso.

El DTO de entrada y retorno Java provienen de la firma. «Sin DTO JSON» puede significar query/path, multipart o ausencia de body; no significa que el método no tenga parámetros. «Ver ResponseEntity» evita inventar un status/cuerpo dinámico. Las validaciones exactas están en Contratos_DTO y las fuentes. Los ejemplos son sintéticos y no contienen credenciales ejecutables.

| Método | Ruta | Body declarado | Retorno Java | Éxito declarado | Acceso base | Fuente |
|---|---|---|---|---|---|---|
'''
    contract_rows=[]
    for e in EVIDENCE['endpoints']:
        p=ROOT/e['source'];s=p.read_text(encoding='utf-8-sig');start=sum(len(x)+1 for x in s.splitlines()[:e['line']-1]);part=s[start:]
        m=re.search(r'public\s+([\w<>,?.\[\] ]+)\s+(\w+)\s*\(',part)
        if not m:raise ValueError(e)
        end=balanced_end(part,part.index('(',m.start()))
        sig=part[m.start():end];req=re.search(r'@RequestBody\s+(\w+)',sig)
        body=req[1] if req else 'multipart' if 'MultipartFile' in sig else 'Sin DTO JSON'
        ann=part[:m.start()];status=re.search(r'@ResponseStatus\(HttpStatus\.(\w+)\)',ann)
        status=status[1] if status else 'Ver ResponseEntity' if 'ResponseEntity' in m[1] else '200 por retorno normal'
        access=permission(e['method'],e['route'])
        if e['route'].endswith('/estado') and e['route'].startswith(('/api/conocimientos/','/api/procedimientos/')):access+='; publicar exige A'
        text+=f'| {e["method"]} | `{e["route"]}` | {body} | `{clean(m[1].strip())}` | {status} | {access} | `{p.relative_to(ROOT).as_posix()}:{e["line"]}` |\n'
        contract_rows.append(dict(e,request=body,response=m[1].strip(),status=status,access=access,signature=sig))
    text+='''
## Ejemplos sintéticos de payload

Crear Ticket, POST /api/tickets, con sesión autorizada:

```json
{"titulo":"Falla en equipo de prueba","descripcion":"Descripción sintética del problema","prioridad":"MEDIA","hardwareId":null,"sistemaId":null,"moduloId":null}
```

No incluir solicitante ni rol enviados por el cliente: el caso de uso deriva el actor autenticado. El controller declara 201; la respuesta contiene identificador, código, estado, referencias y colecciones conforme a TicketDetalleResponse. No se inventa un ID/código como si fuese respuesta real.

Consultar asistente, POST /api/chatbot/consultas:

```json
{"consulta":"problema de conexión"}
```

El servicio devuelve hasta cinco soluciones autoservicio encontradas. No se acredita llamada a un LLM externo.

GET /api/tickets admite estado, prioridad, etiquetaId, asignadoId, texto, page y size según TicketController; GET /api/conocimientos/buscar admite texto y clasificación. Revisar los tipos/defaults en las firmas fuente antes de automatizar un cliente. El catálogo no habilita rutas ausentes de recuperación ni inventa un POST NFC.
'''
    guide('docs/05_API/Endpoints.md','Catálogo de endpoints REST',text)
    text='''
## Contratos fuente

Las siguientes declaraciones record se conservan con sus anotaciones. No se afirman nombres de campo para DTO que no se hayan encontrado. Los alias Jackson y Bean Validation se distinguen de restricciones de BD. Los records anidados adicionales se consultan en el archivo indicado. Los tipos Java de respuesta no prueban una serialización observada en un servidor real.
'''
    for p in sorted(BASE.glob('*/infrastructure/web/dto/*.java')):
        s=p.read_text(encoding='utf-8-sig');m=re.search(r'public\s+record\s+(\w+)\s*\(',s)
        if not m:continue
        end=balanced_end(s,s.index('(',m.start()))
        text+=f'\n## {m[1]}\n\nFuente: `{p.relative_to(ROOT).as_posix()}`.\n\n```java\n{s[m.start():end]}\n```\n'
    guide('docs/05_API/Contratos_DTO.md','Contratos DTO y validaciones declaradas',text)
    save('docs/auditoria/evidencias/CONTRATOS_API.json',json.dumps(contract_rows,ensure_ascii=False,indent=2))


FEATURES=[
('RF-01','Autenticar y consultar sesión','identidad/infrastructure/web/AuthController.java','AutenticarUsuarioServiceTest','Implementada y verificada en escenarios locales'),
('RF-02','Administrar usuarios y consultar roles','identidad/application/service/AdministrarUsuariosService.java','AdministrarUsuariosServiceTest','Implementada y verificada en escenarios locales'),
('RF-03','Administrar catálogos y asociaciones','catalogo/application/service/AdministrarCatalogosService.java','AdministrarCatalogosServiceTest','Implementada y verificada en escenarios locales'),
('RF-04','Administrar departamentos y contactos','identidad/application/service/AdministrarOrganizacionService.java','AdministrarOrganizacionServiceTest','Implementada y verificada en escenarios locales'),
('RF-05','Administrar responsables','identidad/application/service/AdministrarResponsablesService.java','AdministrarResponsablesServiceTest','Implementada y verificada en escenarios locales'),
('RF-06','Mantener conocimiento y visibilidad','conocimiento/application/service/MantenerConocimientoService.java','MantenerConocimientoServiceTest','Implementada y verificada en escenarios locales'),
('RF-07','Buscar contenido publicado mediante índice','conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java','BusquedaConocimientoJdbcAdapterTest','Implementada, pero no verificada en MySQL'),
('RF-08','Mantener soluciones y autoservicio','conocimiento/application/service/AdministrarSolucionService.java','AdministrarSolucionServiceTest','Implementada y verificada en escenarios locales'),
('RF-09','Administrar y asociar pruebas','conocimiento/application/service/AdministrarPruebasService.java','AdministrarPruebasServiceTest','Implementada y verificada en escenarios locales'),
('RF-10','Definir y publicar procedimientos con pasos','procedimiento/application/service/MantenerProcedimientoService.java','MantenerProcedimientoServiceTest','Implementada y verificada en escenarios locales'),
('RF-11','Registrar ejecución y cumplimiento','ejecucion/application/service/GestionarEjecucionService.java','GestionarEjecucionServiceTest','Implementada y verificada en escenarios locales; concurrencia pendiente'),
('RF-12','Registrar resultados y efectividad','seguimiento/application/service/RegistrarEfectividadSolucionService.java','RegistrarEfectividadSolucionServiceTest','Implementada y verificada en escenarios locales'),
('RF-13','Gestionar materiales y archivos','conocimiento/application/service/ArchivoMaterialService.java','ArchivoMaterialServiceTest','Implementada y verificada en escenarios locales; recuperación pendiente'),
('RF-14','Gestionar ciclo de tickets','tickets/domain/Ticket.java','TicketTest','Implementada y verificada en escenarios locales; MySQL pendiente'),
('RF-15','Consultar soluciones autoservicio con asistente','conocimiento/application/service/ConsultarChatbotService.java','ConsultarChatbotServiceTest','Implementada y verificada como búsqueda, no LLM'),
]


def write_traceability():
    p=DOCS/'02_Requisitos/Requisitos_Funcionales.md'
    text='''
## Estado de aprobación

Los identificadores RF de esta guía son una **reconstrucción documental para trazabilidad**, derivados del código. No se presentan como requisitos originales aprobados por Duoc, el cliente o un Product Owner. La equivalencia con requisitos oficiales queda [PENDIENTE DE VERIFICACIÓN].

| ID documental | Capacidad observada | Evidencia fuente | Estado |
|---|---|---|---|
'''
    for fid,title,src,test,state in FEATURES:
        assert (BASE/src).exists(),src
        text+=f'| {fid} | {title} | `{(BASE/src).relative_to(ROOT).as_posix()}` | {state} |\n'
    text+='''
## Capacidades parciales y ausencias

Avisos: derivados de consultas y caché local, entrega integrada pendiente. Favoritos: navegador local, sin sincronización backend acreditada. Reportes: serie mensual literal. Nuevo error: persistencia local distinta de Tickets. Recuperación de contraseña: frontend y requests, sin endpoints. Snapshot de pasos: documentado históricamente, no encontrado. NFC, LLM externo, email y push: sin implementación o requisito aprobado encontrado; no se reclasifican como plan oficial.

La aceptación funcional integral y el cierre de cada RF requieren datos/ambiente y criterio formal, no solo presencia de clases. Los roles y transiciones actuales se documentan en manual y controles de seguridad.
'''
    guide(p.relative_to(ROOT).as_posix(),'Requisitos funcionales reconstruidos',text)
    guide('docs/02_Requisitos/Requisitos_No_Funcionales.md','Requisitos no funcionales y evidencia', '''
No se recibieron umbrales o requisitos no funcionales aprobados. Esta tabla diferencia controles observados de objetivos pendientes; no inventa SLA, tiempos máximos, porcentajes de cobertura ni niveles de accesibilidad.

| ID documental | Tema | Evidencia actual | Criterio formal pendiente |
|---|---|---|---|
| RNF-01 | Acceso y confidencialidad | JWT/BCrypt, roles, propietario, validaciones | Política de sesión, privacidad, rate limiting y pruebas integrales |
| RNF-02 | Integridad | Transacciones, versiones y bloqueos parciales, seis FK en SQL Tickets | DDL físico, carreras prioritarias y escrituras externas |
| RNF-03 | Recuperación | Compensación de archivos y guías de respaldo propuestas | RPO/RTO y restauración BD/uploads comprobada |
| RNF-04 | Rendimiento | Cachés, filtros y consultas parametrizadas | Carga, concurrencia, latencia y uso de recursos acordados |
| RNF-05 | Mantenibilidad | Puertos/adaptadores, pruebas, ArchUnit Tickets | Límites globales y criterios de revisión/CI |
| RNF-06 | Compatibilidad | Versiones de lock/POM, compilación y Chrome local | Matriz de navegadores, SO y versión MySQL objetivo |
| RNF-07 | Usabilidad/accesibilidad | Formularios y guards con pruebas locales | UAT, móvil, teclado, zoom y estándar objetivo |
| RNF-08 | Cadena de suministro | Lockfile y consulta npm con avisos | Evaluación y corrección de dependencias; SCA Maven |
| RNF-09 | Operación | Perfil staging y configuraciones externas previstas | TLS, monitoreo, logs seguros y responsables |

Todos los criterios formales no entregados quedan [PENDIENTE DE VERIFICACIÓN]. Una prueba H2 no cierra compatibilidad MySQL; una guía de respaldo no cierra recuperación.
''')
    text='''
Las HU siguientes son formulaciones documentales derivadas de funciones presentes. No son una sustitución de historias aprobadas, estimaciones o actas. La numeración es nueva y se relaciona con RF de esta documentación; no se confunde con los nombres históricos Hu01SecurityIntegrationTest y similares.

| HU documental | Historia | Criterio observable candidato | RF |
|---|---|---|---|
| HU-D01 | Como persona con cuenta quiero iniciar sesión para acceder según mi perfil | Login válido emite JWT; inválido no autoriza, sujeto a política de inactivo actual | RF-01 |
| HU-D02 | Como administrador quiero mantener usuarios/catálogos para controlar disponibilidad | Operación autorizada persiste y rechazos no anuncian éxito; último admin protegido | RF-02 a RF-05 |
| HU-D03 | Como usuario quiero encontrar contenido publicado para resolver una falla | Búsqueda filtra publicado y asistente solo ofrece soluciones autoservicio | RF-06, RF-07, RF-15 |
| HU-D04 | Como personal operacional quiero mantener conocimiento y pruebas para reutilizar soluciones | Relaciones, estados, campos y pertenencia validados por los servicios | RF-06, RF-08, RF-09 |
| HU-D05 | Como técnico quiero definir un procedimiento para registrar pasos de trabajo | Cabecera BORRADOR y pasos se confirman incrementalmente, PUBLICADO inmutable | RF-10 |
| HU-D06 | Como ejecutor quiero registrar cumplimiento para dejar seguimiento | Propietario/admin accede; completar exige cumplimiento; concurrencia pendiente | RF-11 |
| HU-D07 | Como usuario quiero reportar y seguir un ticket para recibir atención | Alta autorizada crea ABIERTO e historial; solo actor permitido cambia estados | RF-14 |
| HU-D08 | Como personal de soporte quiero resolver tickets y registrar resultado para reutilizar atención | Resolución exige texto y permiso; notas internas se ocultan al usuario | RF-12, RF-14 |
| HU-D09 | Como editor quiero asociar materiales para apoyar la solución | Tipo/tamaño/pertenencia válidos y descarga autorizada; recuperación pendiente | RF-13 |

Aprobación, prioridad de negocio, estimación, responsable y aceptación de cada historia: [PENDIENTE DE VERIFICACIÓN]. No se fabrican UAT ni capturas para cerrar criterios.
'''
    guide('docs/02_Requisitos/Historias_Usuario.md','Historias de usuario reconstruidas',text)
    guide('docs/02_Requisitos/Casos_Uso.md','Casos de uso documentados', '''
## CU-D01. Inicio de sesión

Actor: persona con cuenta. Precondición: API/configuración disponibles. Flujo: ingresar identificador/clave, frontend envía login, servicio resuelve usuario y valida activo/contraseña, adaptador genera JWT y frontend conserva sesión. Alternativas: datos inválidos, usuario inexistente o inactivo, contraseña incorrecta y fallo de conexión. No se acredita recuperación automática ni refresh/revocación.

## CU-D02. Crear y atender un ticket

Actor inicial: usuario u otro actor de creación permitido; administrador rechazado en el servicio de alta. Precondición: autenticación y clasificación válida cuando se informa. Flujo: completar datos, POST /api/tickets, validar DTO/actor, generar código y guardar ABIERTO/historial, abrir detalle. Soporte/técnico trabaja desde bandeja y ejecuta acciones permitidas de asignación, espera, conversación y resolución. El solicitante o técnico asignado puede confirmar cierre según contrato. Reapertura/cancelación tienen reglas de actor/estado específicas.

Alternativas: 400/403/404/409, respuesta de creación incierta y archivo rechazado. No repetir alta por un fallo posterior de adjuntos o navegación. La auditoría no certifica dos altas/ediciones simultáneas en MySQL.

## CU-D03. Mantener y publicar conocimiento

Actor editor: perfil operacional autorizado. Flujo: crear borrador, completar clasificación y elementos asociados, mantener soluciones activas y publicar con administrador. La publicación exige reglas del servicio y las soluciones activas necesarias. Borrado de conocimiento es lógico mediante DELETE autorizado, no borrado físico de hijos. Lecturas de borrador/eliminado se filtran por política; resultados históricos tienen excepciones acotadas.

## CU-D04. Definir y ejecutar un procedimiento

Actor editor: staff autorizado; publicación: administrador. Cabecera y pasos se guardan en requests independientes. Una falla intermedia conserva los confirmados y permite recuperar el borrador. Al publicar se impide edición posterior de la definición. Iniciar ejecución requiere PUBLICADO con pasos; completar exige todos cumplidos. No se implementa captura de snapshot histórico ni cierre concurrente certificado. Publicar vacío e iniciar vacío son reglas diferentes: el inicio lo rechaza aunque el servicio de publicación no exija mínimo.

## CU-D05. Consulta autoservicio

Actor: A/T/S/U autenticado. Introducir consulta, llamar POST /api/chatbot/consultas, buscar conocimientos elegibles y recuperar hasta cinco soluciones autoservicio. Si no encuentra resultados, mostrar respuesta del contrato. No se ejecuta un proveedor LLM ni lectura NFC.

Las precondiciones/alternativas son reconstrucciones de código, no actas de aceptación. UAT y criterios oficiales quedan [PENDIENTE DE VERIFICACIÓN].
''')
    text='''
## Convenciones

Los RF/HU documentales se reconstruyeron del código y siguen pendientes de equivalencia con requisitos aprobados. PASS indica el escenario local respaldado por la auditoría, no cierre E2E/MySQL. Fuente de resultados: COMPROBACIONES.json, 920 backend y 284 Angular. No se repitieron suites por cambios documentales.

| RF | Función | Evidencia fuente | Prueba existente y resultado de la auditoría | Estado / límite |
|---|---|---|---|---|
'''
    testbase=ROOT/'dima-bug-backend/src/test'
    for fid,title,src,test,state in FEATURES:
        ps=list(testbase.rglob(test+'.java'));assert len(ps)==1,test
        suites=[x for x in EVIDENCE['backend']['suites'] if x['name'].endswith('.'+test)];assert len(suites)==1,test
        x=suites[0]
        text+=f'| {fid} | {title} | `{(BASE/src).relative_to(ROOT).as_posix()}` | `{ps[0].relative_to(ROOT).as_posix()}`: {x["tests"]} ejecutadas, {x["failures"]} fallos, {x["errors"]} errores | {state} |\n'
    text+='''
## Elementos no cerrados

| Elemento | Evidencia disponible | Estado |
|---|---|---|
| NFC | Ningún lector/contrato encontrado; Form.NFC es normalización Unicode | No verificable como función del proyecto |
| Snapshot | Guía histórica frente a campos/migración ausentes | Documentado, no encontrado en código |
| CI/MySQL IT | Guías históricas, sin clases/perfil/workflow citados | No verificable en este checkout |
| Recuperación password | UI y requests, sin endpoints | Parcial |
| Notificaciones | Consultas + caché/marcas locales | Implementado en código, entrega integrada no verificada |
| Reportes/Nuevo error | Serie literal / persistencia local | Parcial, no tratar como métricas o recepción compartida reales |
| E2E/UAT/despliegue/restauración | Sin actas/reportes actuales disponibles | [PENDIENTE DE VERIFICACIÓN] |
'''
    guide('docs/auditoria/MATRIZ_TRAZABILIDAD.md','Matriz de trazabilidad documental',text)
    guide('docs/07_Pruebas/Matriz_Trazabilidad.md','Referencia de trazabilidad', '''
La matriz única se mantiene en [MATRIZ_TRAZABILIDAD](../auditoria/MATRIZ_TRAZABILIDAD.md), con RF documentales, fuente y pruebas realmente encontradas. No se duplica su contenido en esta carpeta. Los resultados y límites de ejecución se mantienen en Resultados_Pruebas.
''')


def update_entries_and_history():
    entries=[('01_Proyecto/Descripcion_Proyecto.md','Proyecto y alcance'),('01_Proyecto/Objetivos_Alcance.md','Objetivos y exclusiones'),('01_Proyecto/Metodologia.md','Método y procedencia'),
             ('02_Requisitos/Requisitos_Funcionales.md','RF reconstruidos'),('02_Requisitos/Requisitos_No_Funcionales.md','RNF y criterios pendientes'),('02_Requisitos/Historias_Usuario.md','HU documentales'),('02_Requisitos/Casos_Uso.md','Casos de uso'),
             ('03_Arquitectura/Arquitectura_General.md','Arquitectura general'),('03_Arquitectura/Arquitectura_Backend.md','Backend'),('03_Arquitectura/Arquitectura_Frontend.md','Frontend'),
             ('04_Base_Datos/Modelo_Relacional.md','Modelo relacional'),('04_Base_Datos/Diccionario_Datos.md','Diccionario de datos'),('04_Base_Datos/Migraciones.md','Migraciones'),
             ('05_API/Endpoints.md','124 mappings REST'),('05_API/Contratos_DTO.md','DTO y validaciones'),('05_API/Autenticacion.md','Autenticación'),('05_API/Errores_HTTP.md','Errores HTTP'),
             ('06_Seguridad/Arquitectura_Seguridad.md','Seguridad'),('06_Seguridad/Controles_Seguridad.md','Controles y pendientes'),('07_Pruebas/Estrategia_Pruebas.md','Estrategia de pruebas'),('07_Pruebas/Resultados_Pruebas.md','Resultados verificados'),
             ('08_Despliegue/Instalacion_Local.md','Ejecución local'),('08_Despliegue/Variables_Entorno.md','Configuración sin secretos'),('08_Despliegue/Despliegue_STAGING.md','Preparación STAGING'),
             ('09_Manuales/Manual_Usuario.md','Manual de usuario'),('09_Manuales/Manual_Tecnico.md','Manual técnico'),('auditoria/MATRIZ_TRAZABILIDAD.md','Trazabilidad'),('auditoria/AUDITORIA_DOCUMENTAL.md','Auditoría inicial'),('auditoria/REPORTE_FINAL_DOCUMENTACION.md','Cierre documental y pendientes')]
    index='''# Documentación vigente de DimaBug

Fecha de referencia: 2026-11-06. Este índice reúne las guías basadas en el checkout auditado. Los documentos de dima-bug-backend/docs y frontend-dimabug/docs conservan su valor histórico y no sustituyen estas guías actualizadas.

## Consulta

| Documento | Uso |
|---|---|
'''
    for path,label in entries:index+=f'| [{label}]({path}) | {label} |\n'
    index+='''
## Qué está acreditado

920 pruebas backend y 284 Angular aprobadas en la auditoría, más comprobaciones TypeScript. Arquitectura de monolito modular con puertos/adaptadores y ArchUnit específico de Tickets. Modelo lógico de 31 tablas, sin certificación de esquema físico. Los resultados no se volvieron a ejecutar por cambios exclusivamente documentales.

## Límites vigentes

Sin MySQL real, E2E/UAT, CI remoto, despliegue o restauración certificados. Snapshot de pasos ausente en este árbol; recuperación de contraseña parcial; avisos/favoritos locales y reportes demostrativos. Dependencias afectadas y riesgos funcionales no fueron corregidos. La entrega documental no es un GO de producción.

## Historia y conservación

- [Referencias históricas ausentes](auditoria/REFERENCIAS_HISTORICAS.md).
- [Registro de conservación de originales](auditoria/historico/README.md).
- Las 43 fuentes documentales iniciales constan en la auditoría original, que permanece intacta.
- [Carpeta de entrega académica](../entrega_duoc/README.md).
'''
    save('docs/README.md',index)
    root=ROOT/'README.md';backup(root)
    save('README.md',f'''# DimaBug

Aplicación web para consultar conocimiento de soporte, mantener procedimientos y gestionar tickets. Proyecto APT / Capstone, Duoc UC. La definición institucional y aceptación formal se mantienen pendientes donde no existe evidencia proporcionada.

## Documentación

[Índice vigente](docs/README.md) · [Manual de usuario](docs/09_Manuales/Manual_Usuario.md) · [Manual técnico](docs/09_Manuales/Manual_Tecnico.md) · [Entrega académica](entrega_duoc/README.md)

## Tecnologías comprobadas

Frontend Angular Core 19.2.25, CLI 19.2.27, TypeScript 5.7.3. Backend Java 21, Spring Boot 4.1.1, JPA/JDBC y MySQL Connector/J. Maven Wrapper utiliza Maven 3.9.16. Versiones resueltas desde POM/lockfile, no de documentación antigua.

## Estructura

```text
frontend-dimabug/   SPA, servicios HTTP, guards y pruebas
dima-bug-backend/   API, casos de uso, dominio, adaptadores y pruebas
docs/              Guías vigentes y auditoría
entregables/       Material de presentaciones y modelos anteriores
entrega_duoc/      Evidencias académicas documentales y estado de pendientes
```

## Ejecución y configuración

Consultar [Instalacion_Local](docs/08_Despliegue/Instalacion_Local.md) y [Variables_Entorno](docs/08_Despliegue/Variables_Entorno.md) antes de arrancar. El recurso compartido application.properties no está presente; el perfil staging y las variables estándar se describen por separado. Es necesario un esquema MySQL aprobado: los cinco SQL disponibles no constituyen un DDL base completo y uno contiene DROP de adjuntos. No ejecutar migraciones ni usar credenciales reales a partir de este README.

Con dependencias existentes y ambiente local autorizado, el frontend usa `npm start` desde su carpeta (localhost:4200, proxy API 9090). El backend se inicia mediante su wrapper y configuración externa aprobada. No se ejecutó una base o despliegue durante la organización documental.

## Pruebas

Desde backend: `mvnw.cmd -o test` si las dependencias ya están disponibles. Desde frontend: `npm.cmd test -- --watch=false --browsers=ChromeHeadless`.

Última auditoría local: 920 backend y 284 Angular aprobadas, sin fallos reportados; [resultados y límites](docs/07_Pruebas/Resultados_Pruebas.md). No certifica build limpio, MySQL, UAT o producción. No se instalaron dependencias para esta documentación.

## Estado funcional

Identidad, catálogos, organización, conocimiento/soluciones, procedimientos/ejecuciones, resultados y Tickets tienen recorridos implementados y pruebas locales. Chatbot es búsqueda autoservicio, sin proveedor LLM. Recuperación de contraseña, métricas demostrativas y Nuevo error local no se presentan como funciones integradas completas. No hay implementación NFC ni snapshot histórico encontrados. [Alcance](docs/01_Proyecto/Objetivos_Alcance.md).

La auditoría npm registró 54 entradas afectadas, cuatro críticas en herramientas transitivas dev. La documentación no actualiza dependencias ni cierra riesgos técnicos. [Auditoría](docs/auditoria/AUDITORIA_DOCUMENTAL.md).

El contenido anterior de este README se conserva en [histórico](docs/auditoria/historico/README.md). No se realizaron commits, push o despliegues.
''')
    front=ROOT/'frontend-dimabug/README.md';backup(front)
    save('frontend-dimabug/README.md','''# DimaBug frontend

SPA standalone Angular Core 19.2.25 y CLI 19.2.27 según lockfile. Usa TypeScript 5.7.3, RxJS y CSS propio. Consume la API Spring Boot, no Django.

## Documentación vigente

[Índice](../docs/README.md) · [Arquitectura frontend](../docs/03_Arquitectura/Arquitectura_Frontend.md) · [Manual](../docs/09_Manuales/Manual_Usuario.md)

## Desarrollo

Desde esta carpeta y con paquetes ya instalados: `npm start`. Abre localhost:4200. El proxy reenvía /api a localhost:9090; no a 8080. Debe existir un backend local configurado y un esquema aprobado. La preparación y requisitos están en [Instalacion_Local](../docs/08_Despliegue/Instalacion_Local.md). No se instalaron paquetes durante la organización documental.

## Pruebas y build

`npm.cmd test -- --watch=false --browsers=ChromeHeadless` ejecuta los specs. Resultado de auditoría: 284 SUCCESS. `npm run build -- --configuration production` es el comando definido para un build futuro; no se ejecutó en la fase documental. Producción usa mismo origen de API por defecto y no el proxy de ng serve.

## Rutas y límites

Login, conocimiento, procedimientos, ejecuciones, catálogos, administración, avisos, favoritos, reportes, chatbot y Tickets se registran en app.routes.ts. Guards no reemplazan permisos backend. La pantalla recuperar no tiene implementación backend de sus requests; no debe ofrecerse como recuperación operativa. Favoritos/avisos tienen estado local; reportes incluye series literales y Nuevo error no es el módulo Tickets persistente.

Las guías QA en docs son antecedentes fechados. El README anterior se conserva en [registro histórico](../docs/auditoria/historico/README.md).
''')
    save('dima-bug-backend/README.md','''# DimaBug backend

Spring Boot 4.1.1, Java 21, Maven Wrapper 3.9.16. Monolito modular con puertos/adaptadores, JPA/JDBC, JWT y filesystem.

- [Documentación vigente](../docs/README.md).
- [Arquitectura y límites](../docs/03_Arquitectura/Arquitectura_Backend.md).
- [API y DTO](../docs/05_API/Endpoints.md).
- [Datos y migraciones](../docs/04_Base_Datos/Modelo_Relacional.md).
- [Configuración](../docs/08_Despliegue/Variables_Entorno.md).
- [Pruebas y resultados](../docs/07_Pruebas/Resultados_Pruebas.md).

El recurso application.properties no está presente; no copiar la configuración supuesta por una guía antigua. El perfil staging y los placeholders existentes se describen en configuración. No se ejecutaron SQL o arranques reales para esta documentación.

Desde esta carpeta, `mvnw.cmd -o test` ejecuta la suite si dependencias y wrapper ya están disponibles. La auditoría registró 920 pruebas aprobadas, sin certificar MySQL ni build limpio. Los documentos de docs/ conservan historia de otras entregas; la ruta de documentación vigente es el índice anterior.
''')
    missing=[]
    for original in sorted((ROOT/'dima-bug-backend/docs').rglob('*.md')):
        preserved=backup(original);text=preserved.read_text(encoding='utf-8-sig')
        def replace(m):
            label,dest=m[1],m[2]
            if re.match(r'\w+://|#',dest):return m[0]
            target=dest.split('#')[0].strip('<>')
            targetfile=(original.parent/target) if target else original
            fragment=dest.partition('#')[2]
            exists=targetfile.exists()
            anchor_ok=not fragment or targetfile.suffix!='.md' or (exists and fragment in heading_ids(targetfile.read_text(encoding='utf-8-sig')))
            if exists and anchor_ok:return m[0]
            missing.append({'source':original.relative_to(ROOT).as_posix(),'label':label,'target':dest})
            url=relative(original,AUDIT/'REFERENCIAS_HISTORICAS.md')+'#archivos-no-disponibles'
            return f'[{label} — referencia no disponible]({url})'
        text=re.sub(r'\[([^\]]+)\]\(([^)]+)\)',replace,text)
        banner=('> Referencia histórica conservada. Describe una entrega previa y puede diferir del checkout actual. '+
                link(original,DOCS/'README.md','Documentación vigente')+'. Los enlaces a archivos ausentes se identifican expresamente; '+
                link(original,AUDIT/'historico/README.md','original íntegro y hash')+'.\n\n')
        save(original.relative_to(ROOT).as_posix(),banner+text)
    text='''# Referencias históricas y procedencia

Los documentos anteriores se conservan como antecedentes fechados. Una referencia histórica a un ensayo o archivo ausente no acredita que la funcionalidad exista en este checkout, ni demuestra que el ensayo nunca ocurrió en otro árbol.

## Archivos no disponibles

Estos enlaces se sustituyeron por una referencia explícita a este registro. No se crearon scripts, workflows, IT o configuración ficticios para hacer que sus destinos existan. El contenido original completo está preservado en el histórico. El README backend ahora existe y proporciona entrada a las guías vigentes.

| Documento histórico | Etiqueta original | Destino original no disponible |
|---|---|---|
'''
    for row in missing:text+=f'| `{row["source"]}` | {clean(row["label"])} | `{row["target"]}` |\n'
    text+='\nLos resultados de integración Testcontainers/CI/snapshots siguen pendientes de conciliación. La suite disponible usa mocks/MVC/H2; las referencias sustituidas no cambian ese límite. Ver [auditoría original](AUDITORIA_DOCUMENTAL.md) y [matriz vigente](MATRIZ_TRAZABILIDAD.md).\n'
    save('docs/auditoria/REFERENCIAS_HISTORICAS.md',text)
    text='# Conservación de originales\n\nSe archivaron únicamente los documentos existentes que fue necesario actualizar. Las copias .original.txt conservan los bytes y SHA-256 anteriores; no son guías activas y sus enlaces relativos no se reinterpretan desde la nueva ubicación. Los QA frontend y entregables visuales anteriores no se movieron ni borraron. La auditoría inicial permanece intacta.\n\n| Documento actualizado | Copia íntegra anterior | SHA-256 original |\n|---|---|---|\n'
    here=AUDIT/'historico/README.md'
    for b in BACKUPS:text+=f'| `{b["path"]}` | {link(here,ROOT/b["backup"],"Original conservado")} | `{b["sha256"]}` |\n'
    save('docs/auditoria/historico/README.md',text)


def write_diagrams(tables,relations):
    from PIL import Image, ImageDraw, ImageFont
    import html
    target=DOCS/'03_Arquitectura/Diagramas';target.mkdir(parents=True,exist_ok=True)
    groups=[('Identidad y roles','#2455CE',[n for n in tables if n.startswith('u_')]),('Catálogos','#087E8B',[n for n in tables if n.startswith('cl_')]),('Conocimiento','#6C49B8',[n for n in tables if n.startswith('co_')]),('Procedimientos y ejecución','#AD6818',[n for n in tables if n.startswith(('pr_','se_'))]),('Tickets','#B83D60',[n for n in tables if n.startswith('ticket')])]
    mx=ET.Element('mxfile',host='app.diagrams.net',version='24.7.17')
    paths=[]
    def cell(root,ident,value,x,y,w,h,style,parent='1'):
        c=ET.SubElement(root,'mxCell',id=ident,value=value,style=style,vertex='1',parent=parent)
        ET.SubElement(c,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
    def page(name,nodes,edges,w,h):
        diag=ET.SubElement(mx,'diagram',name=name);model=ET.SubElement(diag,'mxGraphModel',page='1',pageWidth=str(w),pageHeight=str(h));root=ET.SubElement(model,'root');ET.SubElement(root,'mxCell',id='0');ET.SubElement(root,'mxCell',id='1',parent='0')
        cell(root,'title',name,30,20,w-60,45,'text;html=0;align=left;fontSize=28;fontStyle=1;fontColor=#101F50;')
        for n,(x,y,width,height,color,fields) in nodes.items():
            label=('↗ ' if n.startswith('ext_') else '')+n.removeprefix('ext_')
            cell(root,n,label,x,y,width,height,'swimlane;html=0;startSize=34;rounded=1;fillColor='+color+';swimlaneFillColor=#F6F8FC;fontColor=#FFFFFF;strokeColor=#CAD3E2;align=left;spacingLeft=10;fontSize=14;fontStyle=1;')
            for i,f in enumerate(fields):cell(root,n+'_f'+str(i),f,0,37+i*20,width,22,'text;html=0;align=left;spacingLeft=10;fontSize=12;fontColor=#172744;',parent=n)
        for i,r in enumerate(edges):
            a=r['parent'] if r['parent'] in nodes else 'ext_'+r['parent'];b=r['child']
            if a not in nodes or b not in nodes:continue
            label=r['column']+' ['+r['kind']+'] '+('0..1 esperado' if r['kind']=='JDBC' else '1:N')
            start_arrow='none' if r['optional'] is None else 'ERzeroToOne' if r['optional'] else 'ERone'
            end_arrow='none' if r['kind']=='JDBC' else 'ERzeroToMany'
            e=ET.SubElement(root,'mxCell',id='e'+str(i),value=label,edge='1',parent='1',source=a,target=b,style='edgeStyle=orthogonalEdgeStyle;rounded=1;endArrow='+end_arrow+';startArrow='+start_arrow+';fontSize=10;labelBackgroundColor=#FFFFFF;strokeColor=#8B99B2;'+('dashed=1;' if r['kind'] in ['REF','JDBC'] else ''))
            ET.SubElement(e,'mxGeometry',relative='1',attrib={'as':'geometry'})
        cell(root,'legend','PK = clave declarada; SQL = FK en script; JPA = relación mapeada; REF/JDBC = referencia, sin FK física certificada. ? = nulo JPA; ND = DDL no disponible.',30,h-55,w-60,35,'text;html=0;align=left;fontSize=13;fontColor=#53627A;')
    def fieldtext(t,f):
        r=[r for r in relations if r['child']==t and r['column']==f['name']]
        tag='/'.join((['PK'] if f['pk'] else [])+([r[0]['kind']] if r else []))
        return (tag+'  ' if tag else '')+f['name']+(' ?' if f['nullable'] is True else ' [ND]' if f['nullable'] is None else '')
    nodes={};W=1680;H=1420;svg=[]
    im=Image.new('RGB',(W*2,H*2),'white');draw=ImageDraw.Draw(im)
    fp=Path('C:/Windows/Fonts')
    def font(size,bold=False):return ImageFont.truetype(str(fp/('arialbd.ttf' if bold else 'arial.ttf')),size*2)
    def tx(x,y,s,size=14,color='#172744',bold=False):draw.text((x*2,y*2),s,font=font(size,bold),fill=color)
    draw.rectangle((0,0,W*2,160),fill='#101F50');tx(30,20,'DimaBug · Modelo relacional',28,'white',True)
    tx(30,96,'31 tablas requeridas por el código · síntesis de claves y referencias · sin certificación del esquema MySQL',18)
    svg.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><rect width="100%" height="100%" fill="white"/><rect width="{W}" height="80" fill="#101F50"/><g font-family="Arial,sans-serif"><text x="30" y="51" fill="white" font-size="28" font-weight="bold">DimaBug · Modelo relacional</text><text x="30" y="116" font-size="18" fill="#172744">31 tablas requeridas por el código · sin certificación del esquema MySQL</text>')
    for col,(title,color,names) in enumerate(groups):
        x=30+col*330;tx(x,142,title,14,color,True)
        svg.append(f'<text x="{x}" y="159" font-size="14" font-weight="bold" fill="{color}">{html.escape(title)}</text>')
        for row,n in enumerate(sorted(names)):
            y=180+row*126;fs=tables[n]['fields'];primary=[f for f in fs if f['pk']];refs=[f for f in fs if any(r['child']==n and r['column']==f['name'] for r in relations)]
            chosen=list({f['name']:f for f in primary+refs}.values())[:3]
            lines=[fieldtext(n,f) for f in chosen]
            if len(lines)<3:lines += [f['name'] for f in fs if f['name'] not in [c['name'] for c in chosen]][:3-len(lines)]
            lines=lines[:3];nodes[n]=(x,y,300,105,color,lines)
            draw.rounded_rectangle((x*2,y*2,(x+300)*2,(y+105)*2),radius=10,fill='#F6F8FC',outline='#CAD3E2',width=2);draw.rectangle((x*2,y*2,(x+300)*2,(y+29)*2),fill=color);tx(x+9,y+7,n,13,'white',True)
            svg.append(f'<rect x="{x}" y="{y}" width="300" height="105" rx="6" fill="#F6F8FC" stroke="#CAD3E2"/><rect x="{x}" y="{y}" width="300" height="29" fill="{color}"/><text x="{x+9}" y="{y+20}" font-size="13" font-weight="bold" fill="white">{html.escape(n)}</text>')
            for j,l in enumerate(lines):tx(x+9,y+37+j*19,l,12);svg.append(f'<text x="{x+9}" y="{y+51+j*19}" font-size="12" fill="#172744">{html.escape(l)}</text>')
    legend='PK: clave declarada   SQL: FK en script   JPA: relación mapeada   REF/JDBC: referencia sin FK SQL confirmada'
    tx(30,H-63,legend,15);tx(30,H-34,'Vista completa de tablas, atributos resumidos. Las cinco páginas de detalle Draw.io contienen todas las columnas y relaciones.',14,'#53627A')
    svg.append(f'<text x="30" y="{H-48}" font-size="15" fill="#172744">{html.escape(legend)}</text></g></svg>')
    page('Modelo global · 31 tablas · atributos resumidos',nodes,[],W,H)
    for title,color,names in groups:
        ns={};rowheights=[]
        for i,n in enumerate(sorted(names)):
            row=i//3
            while len(rowheights)<=row:rowheights.append(0)
            rowheights[row]=max(rowheights[row],58+len(tables[n]['fields'])*20)
        for i,n in enumerate(sorted(names)):
            row=i//3;x=30+(i%3)*430;y=100+sum(h+65 for h in rowheights[:row]);fs=[fieldtext(n,f) for f in tables[n]['fields']]
            ns[n]=(x,y,385,58+len(fs)*20,color,fs)
        edges=[r for r in relations if r['child'] in names]
        external=sorted({r['parent'] for r in edges if r['parent'] not in names});y=130+sum(h+65 for h in rowheights)
        for i,n in enumerate(external):
            pk=[f['name'] for f in tables[n]['fields'] if f['pk']]
            ns['ext_'+n]=(30+(i%3)*430,y+(i//3)*108,385,80,'#67758D',['Referencia externa: '+(', '.join(pk) or 'ver diccionario')])
        height=y+((len(external)+2)//3)*108+90
        page(title+' · detalle y referencias externas',ns,edges,1310,height)
    ET.indent(mx);drawio=target/'DimaBug_Modelo_Relacional_v2.drawio';ET.ElementTree(mx).write(drawio,encoding='utf-8',xml_declaration=True)
    png=target/'DimaBug_Modelo_Relacional_v2.png';im.save(png)
    svgp=target/'DimaBug_Modelo_Relacional_v2.svg';svgp.write_text(''.join(svg),encoding='utf-8')
    for p in [drawio,png,svgp]:GENERATED.append(p);CHANGES.append({'path':p.relative_to(ROOT).as_posix(),'action':'created','after_sha256':sha(p)})
    save('docs/03_Arquitectura/Diagramas/README.md','''# Diagramas vigentes

[Draw.io editable](DimaBug_Modelo_Relacional_v2.drawio), [PNG](DimaBug_Modelo_Relacional_v2.png) y [SVG](DimaBug_Modelo_Relacional_v2.svg).

La primera página inventaría las 31 tablas y resume atributos; no dibuja todas las conexiones para evitar cruces ilegibles. Las otras cinco páginas muestran todas las columnas de cada módulo y conectores con referencias externas. La cardinalidad exacta y su origen se consultan en [Modelo_Relacional](../../04_Base_Datos/Modelo_Relacional.md). Los conectores 1:N describen agrupación lógica; la proyección JDBC espera 0..1 sin unicidad física acreditada. ? indica nulabilidad JPA y ND declaración física no disponible.

No se certifica esquema MySQL. Los archivos antiguos de entregables se conservan. La vista global PNG/SVG es una imagen estática real generada desde el inventario, no una captura de la aplicación ni de MySQL Workbench. El diagrama general de aplicación se mantiene como Mermaid editable en Arquitectura_General.
''')


PACKAGE_MAP={}


def finish_report(record):
    text=f'''# Reporte final de documentación de DimaBug

Fecha de referencia: {DAY}. Continuación autorizada expresamente por la usuaria después de la auditoría y parada de fase 4. Se ejecutaron las fases 5 a 8 en su **alcance documental**. La aceptación académica, UAT, MySQL físico y despliegue siguen sujetos a evidencias independientes.

## Resultado

Se estableció un índice vigente sin eliminar la historia. Se completaron guías de proyecto, requisitos reconstruidos, arquitectura, datos, API, seguridad, pruebas, ejecución y manuales desde las fuentes reales. Los RF/HU nuevos están identificados como reconstrucciones pendientes de aprobación, no como actas institucionales inventadas.

El modelo revisado contiene 31 tablas lógicas, incluyendo la proyección JDBC; el catálogo identifica 124 mappings de método en 37 controladores y declaraciones DTO. La entrega académica reúne documentación, referencias al código, resultados saneados y cinco scripts SQL originales con advertencia de no ejecución. No se duplica el código fuente completo.

Los resultados de 920 pruebas backend/284 Angular y las dos comprobaciones TypeScript pertenecen a la auditoría anterior. No se repitieron suites ni builds por cambios de documentación. El análisis npm de esa fecha conserva 54 entradas afectadas, cuatro críticas dev. No se aplicaron actualizaciones o correcciones funcionales.

## Matriz de corrección

| Documento o grupo | Estado inicial | Corrección realizada | Estado final documental | Evidencia |
|---|---|---|---|---|
| README raíz | Incompleto | Propósito, stack resuelto, alcance, configuración, pruebas e índice | Actualizado; límites explícitos | README.md y copia original en histórico |
| README frontend | Desactualizado | Puerto 9090, rutas, recuperación parcial y referencia vigente | Actualizado | frontend-dimabug/README.md y original preservado |
| README backend | No encontrado | Entrada única a guías y advertencia de configuración | Creado | dima-bug-backend/README.md |
| Índice central | No encontrado | Orden de consulta y separación entre vigente e histórico | Creado | docs/README.md |
| Proyecto/objetivos/método | Disperso o ausente | Descripción derivada del código y pendientes formales | Documentado, validación institucional pendiente | docs/01_Proyecto |
| RF/RNF/HU/casos de uso | No encontrado como conjunto aprobado | Reconstrucción identificada, sin inventar aprobaciones/umbrales | Trazabilidad candidata disponible | docs/02_Requisitos y MATRIZ_TRAZABILIDAD |
| Arquitectura | Incompleta/desactualizada | Puertos/adapters reales, Tickets/ArchUnit y acoplamientos | Conciliada con fuentes observadas | docs/03_Arquitectura |
| Modelo relacional previo | Incompleto (30 tablas) | Nueva revisión de 31 tablas, origen de relación y nulabilidad | Modelo lógico actualizado, servidor pendiente | Modelo_Relacional y Draw.io/PNG/SVG v2 |
| Diccionario de datos | No encontrado | Columnas JPA, Java, nulabilidad y declaraciones SQL disponibles | Completo respecto del inventario fuente; físico pendiente | Diccionario_Datos y MODELO_LOGICO.json |
| Migraciones | Cinco scripts parciales y referencias ausentes | Inventario, límites y DROP señalado, sin ejecución | Documentadas, aplicación no certificada | Migraciones.md y copias SQL con hash |
| API y DTO | Sin catálogo consolidado | Extracción de rutas, firmas, permisos base y records | Contratos fuente documentados | Endpoints, Contratos_DTO y CONTRATOS_API.json |
| Seguridad | Roles/mecanismos históricos divergentes | Cadena actual, cachés, archivos y avisos con límites | Documentación conciliada; riesgos abiertos | docs/06_Seguridad |
| Pruebas | Conteos históricos mezclados | Separación de mocks/MVC/H2 frente a MySQL/UAT y reporte local | Evidencia local verificable | docs/07_Pruebas y resumen saneado |
| Configuración y STAGING | Guías no coincidentes con recurso presente | Perfil staging vs propiedades estándar, sin defaults inventados | Guías coherentes, arranque/servidor no probados | docs/08_Despliegue |
| Manuales | No encontrados como guías completas | Instrucciones por perfil y mantenimiento con funciones parciales identificadas | Creados, UAT pendiente | docs/09_Manuales |
| Veinte guías backend previas | Históricas, varias desactualizadas | Aviso de vigencia y referencias a archivos ausentes identificadas | Historia conservada; rutas de consulta corregidas | Copias íntegras y hashes en histórico |
| QA frontend y entregables previos | Antecedentes/entregas anteriores | Conservación sin mover o regenerar | Preservados como historia | Hashes de auditoría y registro de cambios |
| Entrega académica | No encontrada | Tres carpetas de evidencia, fuentes por referencia y manifiesto | Paquete preparado con pendientes explícitos | entrega_duoc/README.md y MANIFIESTO.json |

## Qué permanece pendiente

- Rúbrica, requisitos/objetivos oficiales, responsables y actas de aceptación: [PENDIENTE DE VERIFICACIÓN].
- DDL completo, restricciones/índices físicos, MySQL real, carreras de Tickets y cierre/checklist, snapshot y provisión: sin validar o sin implementación acreditada.
- CI remoto, build limpio, JAR/Angular desplegados, TLS/proxy/backup/restauración y UAT/E2E: sin evidencia actual disponible.
- Recuperación de contraseña, reportes/Nuevo error locales, avisos/favoritos y NFC/LLM/email/push: estado documentado sin convertirlos en integraciones terminadas.
- Vulnerabilidades de dependencias, autenticación/cachés, concurrencia, archivos e índice: no se corrigió su implementación. H-10, H-11 y H-19 tienen corrección documental; aclarar las capacidades ausentes no implementa snapshot, CI o integración MySQL ni cierra la brecha de evidencia.

No se asigna un porcentaje de cumplimiento o una aprobación Duoc sin rúbrica. Preparar evidencias no acredita la persistencia física ni autoriza producción.

## Conservación y trazabilidad

La auditoría original se conserva sin cambios. Los documentos anteriores que se actualizaron tienen copia de bytes originales, extensión .original.txt y SHA-256. Las guías QA frontend, modelos/PPT previos y fuentes de aplicación no se movieron ni eliminaron. Los enlaces históricos a archivos que no están presentes apuntan ahora a un registro explícito de ausencia; no se crearon archivos ficticios de SQL, CI o pruebas.

Se verificaron {record['protected_file_count']} archivos protegidos de frontend/backend (excluyendo documentación, READMEs, dependencias y cachés), con cero cambios. El cambio previo de application-staging.properties se preservó sin alterarlo. No se ejecutaron SQL, aplicación, instalación de dependencias, commits, push o despliegue en estas fases.

## Archivos modificados o creados

La lista siguiente registra los documentos principales y artefactos nuevos. El registro JSON y el manifiesto de la entrega añaden hashes y comprobaciones finales. Las copias de preservación son intencionales para conservar los originales, no documentos vigentes duplicados.

| Ruta | Acción |
|---|---|
'''
    for row in CHANGES:text+=f'| `{row["path"]}` | {row["action"]} |\n'
    text+='''
## Comprobación final

El resultado mecánico se registra en evidencias/VALIDACION_DOCUMENTAL.json: existencia de destinos Markdown activos, integridad de copias originales, tablas/relaciones, contratos, paquete y conservación de fuentes. Los snapshots .original.txt se mantienen como texto exacto y no se reinterpretan como guías navegables. No se sustituyen con esos controles los ensayos operacionales pendientes.

La revisión documental está completada dentro del alcance autorizado. El siguiente trabajo debe elegirse entre los riesgos y evidencias pendientes; esta conclusión no ejecuta ni autoriza una corrección de código o una migración.
'''
    save('docs/auditoria/REPORTE_FINAL_DOCUMENTACION.md',text)


def package():
    package_root=ROOT/'entrega_duoc'
    for name in ['01_Evidencias_Documentacion','02_Evidencias_Aplicacion','03_Evidencias_Base_Datos']:(package_root/name).mkdir(parents=True,exist_ok=True)
    candidates=[]
    for p in DOCS.rglob('*'):
        if not p.is_file():continue
        r=p.relative_to(DOCS)
        if r.parts[0].startswith(tuple(f'{i:02d}_' for i in range(1,10))) or r.as_posix() in ['README.md','auditoria/MATRIZ_TRAZABILIDAD.md','auditoria/REPORTE_FINAL_DOCUMENTACION.md','auditoria/REFERENCIAS_HISTORICAS.md']:
            candidates.append(p)
    for p in candidates:
        r=p.relative_to(DOCS)
        if r.parts[0]=='04_Base_Datos':dest=package_root/'03_Evidencias_Base_Datos/docs'/r
        elif r.parts[:2]==('03_Arquitectura','Diagramas'):dest=package_root/'03_Evidencias_Base_Datos/Diagramas'/Path(*r.parts[2:])
        else:dest=package_root/'01_Evidencias_Documentacion/docs'/r
        PACKAGE_MAP[p.resolve()]=dest
    for p,dest in PACKAGE_MAP.items():
        dest.parent.mkdir(parents=True,exist_ok=True)
        if p.suffix=='.md':dest.write_text(rewrite_packaged_links(p,p.read_text(encoding='utf-8')),encoding='utf-8')
        else:shutil.copyfile(p,dest)
    report={'date':DAY,'source':'Auditoría local, no repetida en fase documental','backend':EVIDENCE['backend'],'frontend':EVIDENCE['frontend'],'sca':EVIDENCE['npm_audit'],'limitations':EVIDENCE['limitations']}
    save('entrega_duoc/02_Evidencias_Aplicacion/RESUMEN_PRUEBAS.json',json.dumps(report,ensure_ascii=False,indent=2))
    files=[]
    for d,dirs,fs in os.walk(ROOT):
        dirs[:]=[x for x in dirs if x not in {'.git','node_modules','target','dist','.angular','entrega_duoc','docs','entregables','__pycache__'}]
        for f in fs:
            p=Path(d)/f
            if p.suffix in ['.java','.ts','.html','.css','.xml','.json','.sql'] and p.relative_to(ROOT).parts[0] in ['frontend-dimabug','dima-bug-backend']:
                files.append({'path':p.relative_to(ROOT).as_posix(),'sha256':sha(p)})
    save('entrega_duoc/02_Evidencias_Aplicacion/MANIFIESTO_FUENTES.json',json.dumps({'head':HEAD,'branch':'codex/integracion-mvp','working_tree':'Incluye cambios locales previos a la auditoría; HEAD por sí solo no reproduce todos los archivos.','files':files,'full_source_included':False},ensure_ascii=False,indent=2))
    save('entrega_duoc/02_Evidencias_Aplicacion/README.md',f'''# Evidencias de aplicación

El código completo se entrega por referencia al repositorio DimaBug-MVP, rama codex/integracion-mvp y HEAD `{HEAD}`, con cambios locales previos descritos en el reporte. MANIFIESTO_FUENTES.json permite identificar las fuentes observadas por SHA-256. No se duplica el código ni se incluye node_modules, target, credenciales o binarios supuestamente desplegados.

[Resumen de pruebas saneado](RESUMEN_PRUEBAS.json) y [manifiesto de fuentes](MANIFIESTO_FUENTES.json). Se registraron 920 backend y 284 Angular aprobadas, sin certificar MySQL. Los raw XML/logs no se distribuyen por sus secciones de logging. No se incluyen capturas de ejecución, UAT o integración inventadas. Evidencia funcional E2E, persistencia MySQL y run CI: [PENDIENTE DE VERIFICACIÓN].
''')
    for p in sorted((ROOT/'dima-bug-backend/src/main/resources/db/migration').glob('*.sql')):
        dest=package_root/'03_Evidencias_Base_Datos/SQL'/p.name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest);assert sha(p)==sha(dest)
    save('entrega_duoc/03_Evidencias_Base_Datos/README.md','''# Evidencias de base de datos

[Modelo y relaciones](docs/04_Base_Datos/Modelo_Relacional.md), [diccionario](docs/04_Base_Datos/Diccionario_Datos.md), [migraciones](docs/04_Base_Datos/Migraciones.md), [Draw.io editable](Diagramas/DimaBug_Modelo_Relacional_v2.drawio), [PNG](Diagramas/DimaBug_Modelo_Relacional_v2.png), [SVG](Diagramas/DimaBug_Modelo_Relacional_v2.svg).

31 tablas requeridas por código; no inventario del servidor. SQL/ conserva los cinco scripts originales como evidencia, no como instalador. **No ejecutarlos automáticamente.** V20261017_02 contiene DROP TABLE ticket_adjunto. DDL completo/FULLTEXT, esquema físico, persistencia MySQL y restauración siguen [PENDIENTE DE VERIFICACIÓN]. No se incluyen filas, respaldos de producción o cuentas reales.
''')
    save('entrega_duoc/01_Evidencias_Documentacion/README.md','''# Evidencias documentales

El [índice de guías](docs/README.md) reúne proyecto, requisitos reconstruidos, arquitectura, API, seguridad, pruebas, instalación y manuales. Los enlaces internos se adaptaron al paquete. Las referencias a código o historia no incluidos se expresan como rutas del repositorio, no como archivos ficticios.

La rúbrica, requisitos aprobados, firmas y UAT siguen pendientes. El reporte final distingue corrección documental y riesgos sin resolver. Las fuentes de la auditoría original y snapshots íntegros permanecen en el repositorio; no se copian aquí documentos históricos con rutas personales o logs de autenticación.
''')
    save('entrega_duoc/README.md','''# Entrega documental DimaBug para revisión académica

Referencia: 2026-11-06. Proyecto APT / Capstone, Duoc UC. Paquete preparado desde el checkout auditado y la autorización de continuidad documental. No constituye aprobación institucional ni GO de producción.

- [Documentación](01_Evidencias_Documentacion/README.md).
- [Aplicación y pruebas](02_Evidencias_Aplicacion/README.md).
- [Base de datos y modelo](03_Evidencias_Base_Datos/README.md).
- [Manifiesto de integridad](MANIFIESTO.json).

## Evidencia disponible

Guías coherentes con el código, 124 mappings REST, DTO, modelo lógico de 31 tablas, diccionario, cinco SQL originales, manifiesto de fuentes y resumen de 920 pruebas backend/284 Angular aprobadas en auditoría. Los resultados no se volvieron a ejecutar durante la organización documental.

## Ausencias explícitas

Rúbrica/requisitos aprobados, actas, capturas UAT/E2E, MySQL físico, persistencia integrada, DDL base completo, despliegue/CI actuales y restauración probada: [PENDIENTE DE VERIFICACIÓN]. Recuperación de contraseña parcial, snapshot no encontrado y avisos/reportes/estado local tienen los límites descritos en los manuales. Dependencias y riesgos técnicos no fueron corregidos.

No contiene secretos, env reales, datos personales, filas de usuarios, respaldos de producción, node_modules ni target. Los SQL se incluyen como documentos de evidencia: no ejecutarlos al abrir o revisar el paquete. El código completo se referencia mediante repositorio y hashes, sin duplicación innecesaria.
''')


def rewrite_packaged_links(src,text):
    dest=PACKAGE_MAP[src.resolve()]
    def replace(m):
        label,url=m[1],m[2]
        if re.match(r'\w+://|#',url):return m[0]
        raw,sep,anchor=url.partition('#');target=(src.parent/raw).resolve()
        if target in PACKAGE_MAP:
            return '['+label+']('+relative(dest,PACKAGE_MAP[target])+('#'+anchor if sep else '')+')'
        path=target.relative_to(ROOT).as_posix() if target.is_relative_to(ROOT) else 'referencia externa no incluida'
        return label+' (fuente en repositorio: `'+path+'`)'
    return re.sub(r'\[([^\]]+)\]\(([^)]+)\)',replace,text)


def validate_and_manifest():
    active=[ROOT/'README.md',ROOT/'frontend-dimabug/README.md',ROOT/'dima-bug-backend/README.md']
    active+=list(DOCS.rglob('*.md'))+list((ROOT/'dima-bug-backend/docs').rglob('*.md'))+list((ROOT/'frontend-dimabug/docs').rglob('*.md'))
    bad=[]
    for p in active:
        for i,l in enumerate(p.read_text(encoding='utf-8-sig').splitlines(),1):
            for url in re.findall(r'\]\(([^)]+)\)',l):
                if re.match(r'\w+://|#',url):continue
                target=url.split('#')[0].strip('<>')
                tf=p.parent/target if target else p
                frag=url.partition('#')[2]
                if not tf.exists() or (frag and tf.suffix=='.md' and frag not in heading_ids(tf.read_text(encoding='utf-8-sig'))):bad.append({'path':p.relative_to(ROOT).as_posix(),'line':i,'target':url})
    if bad:raise RuntimeError(json.dumps(bad,ensure_ascii=False))
    originals={x['path']:x['sha256'] for x in EVIDENCE['initial_documents']}
    for b in BACKUPS:
        assert sha(ROOT/b['backup'])==b['sha256']
        assert b['sha256']==originals[b['path']]
    model=json.loads((AUDIT/'evidencias/MODELO_LOGICO.json').read_text(encoding='utf-8'));assert len(model['tables'])==31 and len(model['relations'])==47
    xml=ET.parse(DOCS/'03_Arquitectura/Diagramas/DimaBug_Modelo_Relacional_v2.drawio');assert len(xml.getroot().findall('diagram'))==6
    # Fill in the final package after validation reports are created.
    validation={'date':DAY,'active_markdown_files_checked':len(active),'broken_links':bad,'backups_verified':len(BACKUPS),
                'logical_tables':31,'relationships':47,'drawio_pages':6,'sql_scripts_copied':5,'api_mappings':124,
                'source_code_changed':False,'tests_rerun_in_documentation_phase':False,'mysql_verified':False,
                'historical_missing_files_resolved':'Links now identify absence; no missing implementation fabricated.',
                'limitations':['No official rubric or approved requirements provided','No UAT/E2E/MySQL/deployment/restoration evidence','SCA and functional risks remain open']}
    save('docs/auditoria/evidencias/VALIDACION_DOCUMENTAL.json',json.dumps(validation,ensure_ascii=False,indent=2))
    # Generated academic files contain repository-relative references, never machine-local paths.
    package_root=ROOT/'entrega_duoc'
    for p in package_root.rglob('*'):
        if p.is_file() and p.suffix in ['.md','.json','.sql']:
            s=p.read_text(encoding='utf-8-sig')
            if re.search(r'(?i)[A-Z]:[\\/](?:Users|Proyectos)[\\/]|-----BEGIN .*PRIVATE KEY|eyJ[A-Za-z0-9_-]{20,}\.',s):raise RuntimeError(f'Contenido no permitido en paquete: {p}')
    manifest=[]
    for p in sorted(package_root.rglob('*')):
        if p.is_file() and p.name!='MANIFIESTO.json':manifest.append({'path':p.relative_to(package_root).as_posix(),'bytes':p.stat().st_size,'sha256':sha(p)})
    save('entrega_duoc/MANIFIESTO.json',json.dumps({'date':DAY,'files':manifest,'scope':'Documentación y evidencia local, sin certificación de servidor ni datos personales'},ensure_ascii=False,indent=2))
    pb=[]
    for p in package_root.rglob('*.md'):
        for url in re.findall(r'\]\(([^)]+)\)',p.read_text(encoding='utf-8')):
            if re.match(r'\w+://|#',url):continue
            if not (p.parent/url.split('#')[0]).exists():pb.append((str(p),url))
    if pb:raise RuntimeError(pb)
    dest=ROOT/'entregables'/('DimaBug_Entrega_Duoc_Documentacion_'+DAY+'.zip')
    with zipfile.ZipFile(dest,'w',compression=zipfile.ZIP_DEFLATED) as z:
        for p in sorted(package_root.rglob('*')):
            if p.is_file():z.write(p,p.relative_to(ROOT).as_posix())
    with zipfile.ZipFile(dest) as z:assert z.testzip() is None
    validation.update(package_files=len(manifest)+1,package_markdown_broken_links=0,zip_path=dest.relative_to(ROOT).as_posix(),zip_sha256=sha(dest))
    (AUDIT/'evidencias/VALIDACION_DOCUMENTAL.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2),encoding='utf-8')
    record_path=AUDIT/'evidencias/REGISTRO_CAMBIOS_DOCUMENTALES.json';record=json.loads(record_path.read_text(encoding='utf-8'))
    final_changes=[]
    for row in CHANGES:
        item=dict(row)
        if (ROOT/row['path']).resolve()==record_path.resolve():
            item.pop('after_sha256',None);item['integrity']='Self hash omitted to avoid circular digest; original backups and package have independent hashes.'
        else:item['after_sha256']=sha(ROOT/row['path'])
        final_changes.append(item)
    record['changes']=final_changes;record['package_zip']={'path':dest.relative_to(ROOT).as_posix(),'sha256':sha(dest)}
    record['packaged_documents']=[{'source':src.relative_to(ROOT).as_posix(),'copy':copy.relative_to(ROOT).as_posix(),'sha256':sha(copy)} for src,copy in PACKAGE_MAP.items()]
    record['documentation_generator']={'path':Path(__file__).relative_to(ROOT).as_posix(),'sha256':sha(Path(__file__))}
    record_path.write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'guides_and_artifacts':len(GENERATED),'backups':len(BACKUPS),'tables':31,'relationships':47,'api_mappings':124,'package_files':len(manifest)+1,'zip':str(dest),'zip_bytes':dest.stat().st_size,'broken_links':0},ensure_ascii=False))


if __name__=='__main__':
    main()
