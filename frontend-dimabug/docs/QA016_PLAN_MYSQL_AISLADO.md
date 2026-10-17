# QA-016 — Plan de validación MySQL aislada

Fecha: 2026-10-14, America/Santiago. Referencia: QA015_AUDITORIA_INTEGRACION.md.

**Solo preparación:** se inspeccionaron versiones de herramientas, servicios, puertos, archivos y configuración sin conectar a MySQL. No se ejecutó SQL, no se creó base/contenedor/volumen ni se inició servicio, no se restauraron datos y no se modificó código. STAGING/PRODUCCIÓN no se accedieron. Solo se creó este informe. El NO-GO para STAGING permanece vigente.

## 1. Entorno disponible

| Componente | Evidencia local | Conclusión |
|---|---|---|
| MySQL Shell | `C:/Program Files/MySQL/MySQL Shell 26.7/bin/mysqlsh.exe`, versión **26.7.0** mediante --version; opciones SQL/file/schema/password comprobadas en --help | Cliente instalado. **No demuestra un servidor MySQL8 instalado o accesible.** |
| MySQL Server8 | No se encontraron mysqld.exe/mysqldump.exe en Program Files/MySQL; solo carpeta MySQL Shell26.7; 0 servicios MySQL/MariaDB/Docker coincidentes vía CIM | Servidor8 no confirmado. Versión exacta de un servidor: **no determinada**. No inferir que sea26.7 por el cliente. |
| CLI mysql/mysqldump/mysqladmin | No disponibles en PATH | No se puede proponer ejecución inmediata de mysqldump local como si estuviera instalado. |
| Docker CLI | Versión **29.8.2**, build7fc2dff; binario en `C:/Users/carol/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe` | Instalado; contexto seleccionado desktop-linux. |
| Docker Engine | docker ps falló: pipe dockerDesktopLinuxEngine inexistente | No operativo/accesible en ese contexto durante la revisión. No se inició ni se cambió contexto. No se conocen imágenes/volúmenes/contenedores existentes. |
| Puertos observados | Sin listeners detectados en3306/3307/33060 y candidatos13316/19090 | Observación puntual, no reserva ni prueba de instalación. Repetir antes de utilizarlos. |
| Java/Maven | Temurin **21.0.12.1** en `C:/Users/carol/.jdks/temurin-21.0.12.1`; mvnw.cmd y wrapper existentes | Java/mvn no están en PATH del proceso inspeccionado; usar JAVA_HOME/ruta explícita y wrapper. |
| SQL/dumps locales | En los repositorios solo se localizaron tres migraciones; no dump ni DDL base completo; no Dockerfile/Compose de DB | No se puede crear todo el esquema únicamente con las migraciones. No se escanearon discos/carpetas personales ajenos al proyecto. |
| Variables del proceso actual | DB_URL/DB_USERNAME/DB_PASSWORD/SPRING_PROFILES_ACTIVE/JWT_SECRET/JWT_EXPIRATION/SPRING_JPA_HIBERNATE_DDL_AUTO ausentes | Necesario preparar variables exclusivas QA después de autorización; no se imprimieron valores ni se inspeccionaron secretos externos. |

Get-Service no tuvo acceso al Service Control Manager dentro del sandbox; se completó la lectura mediante CIM con permiso para inspección local. No se afirma inexistencia absoluta de instalaciones portables/no registradas: el alcance fue PATH, servicios, Program Files/MySQL y ubicaciones comunes XAMPP/Laragon.

Backend compartido contiene:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

JWT requiere jwt.secret/jwt.expiration, y uploads usa dimabug.storage.materiales-dir (default relativo). Para QA se usarán fuentes/env y rutas nuevas, no secretos ni materiales de STAGING. No se encontró runner de migraciones, Flyway/Liquibase o inicializador de aplicación en las fuentes inspeccionadas. De todas formas se fijará explícitamente validate y SQL init never antes del arranque autorizado.

## 2. Base e instancia propuestas

Nombre propuesto: **dima_bug_qa_integracion**.

**Nombre NO verificado todavía.** Confirmar ausencia requiere SQL contra la instancia destino; esta etapa prohíbe SQL y no hay servidor confirmado. No se reserva ni se utiliza el nombre. Antes de cualquier CREATE/restauración, la siguiente fase autorizada debe consultar information_schema.schemata. Si existe, detenerse, identificar propietario/uso y elegir otro nombre aprobado; no sobrescribir, vaciar o reutilizar automáticamente.

Se propone instancia Docker local dedicada cuando se habilite Docker y se confirme imagen oficial con versión MySQL8 exacta, preferiblemente igual a la versión de referencia documentada. No usar latest ni suponer equivalencia8.0/8.4. Sin acceso STAGING en esta fase, la versión de referencia debe venir de evidencia ya autorizada o quedar pendiente.

| Recurso propuesto | Aislamiento |
|---|---|
| Contenedor `dimabug-qa016-mysql` | Nuevo y exclusivo QA; comprobar ausencia cuando Engine sea accesible. |
| Volumen `dimabug-qa016-mysql-data` | Nuevo y exclusivo; no reutilizar ningún volumen existente. |
| MySQL `127.0.0.1:13316` | Publicar solo loopback, no3306 ni interfaces de red. Revalidar disponibilidad. |
| Backend `127.0.0.1:19090` | Proceso independiente, perfil qa-integracion y validate. |
| Usuario aplicación `dimabug_qa_app` | Credencial nueva, DML solo sobre schema QA; no root ni privilegios sobre otras bases. Usuario DDL/restauración separado. |
| Uploads QA | Directorio nuevo privado fuera del checkout/OneDrive, con material sintético; no bind/montaje de uploads existentes. |

No usar dima_bug_dev, dima_bug_staging, bases de PRODUCCIÓN, sus usuarios o direcciones. El Backend debe incluir únicamente URL localhost:13316/schema QA. Revisar overrides antes de iniciar; perfil nuevo por sí solo no impide heredar DB_URL incorrecta.

## 3. Estrategia de datos

### A. Restaurar un respaldo existente

Ventaja: puede reproducir mejor tipos, FK, índices, collation, triggers y datos legacy. Riesgos: no hay respaldo disponible en el repositorio; puede incluir datos personales, hashes de usuarios, credenciales/grants, DEFINER, nombres originales y statements que alteren otra base. El parámetro --schema del cliente **no impide** que un archivo ejecute USE/DDL de otro esquema.

Solo considerar un respaldo autorizado revisado, preferentemente **solo estructura** y sin cuentas/grants/datos personales. Rechazar una restauración ciega: revisar CREATE/DROP DATABASE, USE, nombres plenamente cualificados, usuarios/grants/DEFINER y comandos globales. No modificar un dump original con reemplazo textual indiscriminado. Preparar una copia de esquema revisada dirigida a QA y conservar su hash/procedencia.

Un dump SQL clásico y un dump de directorio de MySQL Shell no son el mismo formato. Las utilidades de carga necesitan compatibilidad entre herramienta, formato y servidor; no usar ignoreVersion para eludir revisión. [Documentación oficial MySQL Shell](https://dev.mysql.com/doc/mysql-shell/26.7/en/mysql-shell-utilities.html).

### B. Esquema de prueba y datos sintéticos — recomendada

Es la alternativa más segura para evitar copiar información personal/credenciales. Recomendación concreta: **DDL base revisado representativo + datos sintéticos**, usando una exportación de solo estructura autorizada si ya existe, o un esquema de prueba revisado por separado.

Generar automáticamente DDL desde JPA y luego validarlo contra esas mismas entidades sería evidencia circular de paridad con STAGING. JPA no documenta todas las FK/collations físicas. Si se usa un esquema sintético derivado del modelo, etiquetar sus resultados como integración del modelo, no como prueba de compatibilidad con el servidor real; no autoriza GO-STAGING.

El DDL base debe representar el esquema **anterior** a QA-009/QA-012 para validar las migraciones (sin esas cinco columnas ni tabla de control). Otra copia separada puede representar esquema ya migrado para comprobar detección de reejecución. No usar ddl-auto=create/update en la instancia de validación.

Datos sintéticos mínimos:

- Roles/usuarios de prueba locales, contraseña/hash generados exclusivamente para QA; no importar u_usuario reales. ADMINISTRADOR/TECNICO/SOPORTE/USUARIO para matriz de permisos.
- Conocimiento PUBLICADO con dos soluciones y otro con una; BORRADOR; ELIMINADO para referencias históricas. Soluciones legacy sin título antes de migrar, asignaciones y resultados sintéticos.
- Sistemas activos/inactivos, dos Hardware con selección múltiple, Módulos del mismo/diferente Sistema y referencias incluso eliminadas.
- Departamentos activos/inactivos, contactos/responsables propios con estados individuales; referencias de asignación. No personas/contactos reales.
- Pruebas compartidas activas/inactivas y asociaciones con orden; Procedimiento BORRADOR/PUBLICADO, Pasos con saltos, material sintético y ejecución/checklist.
- Juegos **separados** con duplicados/huérfanos/inconsistencias para probar detección, sin contaminar el fixture normal ni corregirlos automáticamente. No desactivar FK para insertar una inconsistencia en el juego normal.

No se creó DDL base o seed SQL en QA-016; son dependencias revisables de la siguiente fase.

## 4. Migraciones y orden

Archivos en `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/src/main/resources/db/migration/`.

| Orden futuro | Archivo | Comprobación |
|---|---|---|
| 0 | DDL base/fixtures revisados | Motor/versiones/columnas/FK/índices/data conocidos; dataset sintético anterior a QA. |
| 1 si falta | V20260925_01__agregar_estado_eliminado_conocimiento.sql | MODIFY enum co_conocimiento. Confirmar definición/valores actuales antes de aplicarlo; no repetir si ya corresponde. |
| 2 | V20261013_01__ampliar_solucion.sql | ADD titulo VARCHAR150 NULL, estado TINYINT NOT NULL DEFAULT1, autoservicio DEFAULT0. Existing rows conservan IDs, quedan activas/no autoservicio/título NULL. |
| 3 | V20261014_01__organizacion_persistente.sql | ADD departamento_version/contacto_version BIGINT NOT NULL DEFAULT0, tabla u_organizacion_control InnoDB/fila1. |
| 4 | Backend con validate | Arranque solo cuando todo el esquema es compatible; después matriz HTTP. |

QA-009/12 no dependen directamente entre sus tablas, pero el JAR consolidado requiere ambos. QA-010/11/13/14 no añaden SQL propio. No hay índices UNIQUE nuevos sobre datos existentes ni FK nuevas en esos scripts; hay PK de una tabla nueva. Revisar los índices/FK existentes, no asumir que JPA los demuestra.

Riesgos: columnas/tabla ya existentes → error y posible ejecución parcial; formato/motor/versión no compatible con INSTANT; límites de fila/columnas y FULLTEXT; enum previo diferente; duplicados que entren en conflicto con reglas de aplicación; huérfanos/versiones inválidas. No sustituir INSTANT por COPY/INPLACE sin evaluación/autorización. [Operaciones online MySQL8](https://dev.mysql.com/doc/refman/8.0/en/innodb-online-ddl-operations.html).

QA-012 usa lock_wait_timeout de sesión5s y restaura al final; QA-009 no impone límite propio. Definir la sesión/control de espera de toda la ejecución autorizada; restaurar/cerrar también si falla el script. Atomic DDL no convierte todos los statements del archivo en una transacción reversible: registrar cada objeto y no reiniciar el archivo a ciegas después de un fallo. [Atomic DDL MySQL8](https://dev.mysql.com/doc/refman/8.0/en/atomic-ddl.html).

## 5. Comandos propuestos — NO ejecutados

Todos los comandos siguientes son para **una fase posterior autorizada**. No copiarlos/ejecutarlos antes de aprobar instancia, versión, DDL, credenciales exclusivas y verificación del nombre. No contienen valores reales de contraseñas.

### A. Revalidar Docker y nombres en la PC

```powershell
docker context show
docker --context desktop-linux info
docker --context desktop-linux ps -a --format '{{.Names}} | {{.Image}} | {{.Ports}}'
docker --context desktop-linux volume ls
```

Actualmente Engine no responde. Habilitar Docker requiere acción/autorización posterior; no se inició aquí. Detenerse si desktop-linux ya no es el contexto **local** verificado. No utilizar un contexto remoto.

Después de confirmar ausencia de esos recursos y elegir imagen oficial8 exacta por digest:

```powershell
$qaImagen = '<IMAGEN_MYSQL8_VERSION_EXACTA@sha256:DIGEST_VERIFICADO>'
docker --context desktop-linux volume create dimabug-qa016-mysql-data
docker --context desktop-linux run --name dimabug-qa016-mysql --detach `
  --publish 127.0.0.1:13316:3306 `
  --mount type=volume,source=dimabug-qa016-mysql-data,target=/var/lib/mysql `
  --env-file '<ARCHIVO_ENV_QA_PRIVADO_FUERA_DE_GIT>' $qaImagen
```

El archivo privado debe contener únicamente configuración nueva QA y credencial root exclusiva para inicializar; **no MYSQL_DATABASE** (crear QA solo tras verificar ausencia), ni credenciales de entornos existentes. Verificar las opciones admitidas por la imagen seleccionada y no usar contraseña vacía. No compartir env/inspect/logs que puedan mostrar secretos. El arranque inicializa datos del servidor/volumen, por lo que necesita autorización, aunque todavía no cree el schema de aplicación. [MySQL en Docker](https://dev.mysql.com/doc/mysql-linuxunix-excerpt/8.0/en/docker-mysql-more-topics.html).

No se proponen docker rm, volume rm, prune ni limpieza destructiva.

### B. Verificar servidor y nombre antes de CREATE

El cliente instalado admite --sql/--mysql/--schema/--file y password prompt. Ejemplo futuro, protocolo clásico:

```powershell
$qaShell = 'C:\Program Files\MySQL\MySQL Shell 26.7\bin\mysqlsh.exe'
& $qaShell --sql --mysql --host=127.0.0.1 --port=13316 --user=root --password
```

Dentro de esa sesión QA aislada, **primero solo lectura**:

```sql
SELECT VERSION(), @@hostname, @@port, @@transaction_isolation, @@sql_mode;
SELECT schema_name FROM information_schema.schemata
WHERE schema_name = 'dima_bug_qa_integracion';
```

Registrar versión exacta del servidor; no confundirla con --version de mysqlsh. La segunda consulta debe devolver cero filas. Si devuelve algo, detenerse; no usar IF NOT EXISTS para ocultar una colisión.

Solo después de autorización de creación, ausencia confirmada y elección de collation compatible, preparar el CREATE (placeholder **no ejecutable sin reemplazo aprobado**):

```sql
CREATE DATABASE dima_bug_qa_integracion
  CHARACTER SET utf8mb4 COLLATE <COLLATION_MYSQL8_REVISADA>;
```

Crear usuarios QA/privilegios mediante procedimiento administrativo revisado sin contraseñas en argv/chat/historial o archivos versionados. Separar usuario de DDL/restauración del usuario Backend (DML schema QA únicamente).

### C. DDL, fixtures y migraciones revisadas

No restaurar directamente un backup con USE de otro nombre o cuentas reales. Para SQL clásico, después de revisar contenido/encoding/hash, una conexión con usuario DDL limitado al schema QA:

```powershell
& $qaShell --sql --mysql --host=127.0.0.1 --port=13316 --user=dimabug_qa_ddl --password `
  --schema=dima_bug_qa_integracion --file='<DDL_BASE_QA_REVISADO.sql>'
```

Aplicar fixtures/migraciones con el mismo destino, uno por uno, verificando errores y objetos después de cada archivo. Para los archivos QA existentes, sus rutas exactas están en sección4. La conexión/schema seleccionados no hacen seguro un archivo arbitrario: los statements deben haberse revisado. El límite de espera debe fijarse/revisarse en la misma sesión real que ejecuta el SQL, no en una conexión previa independiente.

Un dump de directorio requiere herramienta/formato compatibles y util.loadDump con opciones revisadas; no se propone un comando genérico que pueda importar users/grants, crear otras bases o eludir versiones. Si faltan mysql/mysqldump compatibles, validar los binarios disponibles en el contenedor/instalación elegidos antes de preparar backup/restore; no se instalaron herramientas aquí.

### D. Inicio futuro del Backend con validate

Terminal PowerShell exclusiva QA, después de esquema/migraciones y usuario local preparados. No usar terminal que herede env de dev/staging. No imprimir DB_PASSWORD/JWT_SECRET ni emplear `Get-ChildItem Env:` para compartir valores.

```powershell
$env:JAVA_HOME = 'C:\Users\carol\.jdks\temurin-21.0.12.1'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:13316/dima_bug_qa_integracion'
$qaCredencial = Get-Credential -UserName 'dimabug_qa_app' -Message 'Credencial exclusiva de MySQL QA aislado'
$env:DB_USERNAME = $qaCredencial.UserName
$env:DB_PASSWORD = $qaCredencial.GetNetworkCredential().Password
$env:SPRING_PROFILES_ACTIVE = 'qa-integracion'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPRING_SQL_INIT_MODE = 'never'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '19090'
$env:DIMABUG_STORAGE_MATERIALES_DIR = '<DIRECTORIO_NUEVO_UPLOADS_QA_FUERA_DE_GIT>'
```

Cargar jwt.secret/jwt.expiration mediante variables nuevas QA o configuración privada revisada; no reusar el secreto STAGING. El valor de expiración debe verificarse contra las unidades usadas por JwtTokenServiceAdapter. No se inventa un archivo application-qa-integracion.properties ni se modifica application.properties.

Desde `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/`, con JAR consolidado verificado:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar target/backend-0.0.1-SNAPSHOT.jar
```

Antes del comando, verificar URL/puertos/schema **sin mostrar password**, fuentes/overrides y ausencia de init automático. Si validate falla, detenerse y comparar DDL/mapping; no cambiar a update/create para “hacerlo arrancar”. Arrancar Backend con conexión real también queda pendiente de autorización.

## 6. Matriz de pruebas en MySQL aislado

| Caso | Preparación / acción autorizada | Evidencia / resultado esperado |
|---|---|---|
| Q16-01 identidad | VERSION/host/puerto/schema, contenedor/volumen/destino | MySQL8 exacto y loopback dedicado; nunca nombres prohibidos. |
| Q16-02 restauración | DDL solo estructura revisado + seed sintético | Tablas/PK/FK/índices/collation y conteos corresponden al fixture; no users/datos reales ni otro schema. |
| Q16-03 QA-009 | Aplicar ADD sobre esquema previo con Soluciones existentes | IDs/textos/vínculos conservados; títuloNULL/estado1/autoservicio0. Sin recorte de datos ni borrados. |
| Q16-04 QA-012 | Aplicar columnas/tabla/fila1 sobre organización existente | Versiones0; IDs/estados/hijos conservados; control_id1 disponible y bloqueable. |
| Q16-05 falla parcial | En instancia de ensayo separada, comprobar detección de objetos ya aplicados/error | Detención sin repetir a ciegas/limpiar; manifest de statements aplicado y restauración revisada. No alterar fixture aprobado. |
| Q16-06 validate | Inicio con ddl-auto=validate/initnever | Arranque sin DDL automático, sin discrepancias de columnas/tipos; health y conexión QA. |
| Q16-07 Soluciones | Admin/técnico, crear título/editar, estado/autoservicio, legadoNULL | RN-08/15/16/17, 400/403/409 según contrato; activas operativas y títuloslegacy; histórico/resultados intactos. |
| Q16-08 RN-08 concurrente | Dos conexiones desactivar/publicar sobre mismo conocimiento | PUBLICADO conserva ≥1activa; uno debe fallar si intenta retirar última. No confiar solo en mocks. |
| Q16-09 Organización | CRUD, nombretrim/case, contacto propio, padreactivo/inactivo | Admin solamente; historial/estados individuales; contactos no operativos bajo padre inactivo. |
| Q16-10 @Version/mutex | Dos ediciones con misma versión, dos altas nombre igual, crear contacto vs desactivar padre | 409 por versión obsoleta; un ganador del duplicado; locks/liberación/rollback comprobados. |
| Q16-11 Pruebas | CRUD/adminestado, prueba compartida, asociación después de desactivar | Inactiva no seleccionable ni nueva asignación; GET histórico conserva ID/orden/activa=false; índice excluye inactiva. |
| Q16-12 clasificación | Hardware múltiple y PUTomitido/[]; Módulo con mismo nombre entre sistemas | Persistencia real y validaciónFK/índices coherentes; referencias históricas incluidas ELIMINADO se conservan. |
| Q16-13 Procedimiento | Crear BORRADOR sin pasos, añadir/editar pasos con saltos, publicar vacío | Flujo incremental, orden por padre, PUBLICADO no editable; vacío publicable pero ejecución rechazada. |
| Q16-14 parcial/retry | Fallo intermedio/GET posterior/respuesta perdida con BackendQA real | Padre/confirmados conservados; pendientes en Angular; GETverificación evita POST ya confirmado; no garantía de idempotencia global inventada. |
| Q16-15 historial | Reinicio y otra sesión; estados/restauración/material/ejecución | Identidad y relaciones/resultados persistidos, uploads sintéticos íntegros; no eliminación física. |
| Q16-16 índice/recovery | Cambio de estado y fallo postcommit simulado en entorno de ensayo | CRUD confirmado se conserva; detectar log e índice pendiente/reintento aprobado. Sin afirmar retry durable inexistente. |
| Q16-17 limitación conocida | Responsable activo cuyo padre inactivo, solo responsableId | Registrar comportamiento pendiente QA-012; el usuario aplazó ese ajuste. No clasificarlo como regla ya corregida. |

Cada caso requiere request/status, IDs/conteos antes-después, dos sesiones cuando corresponda, duración/locks y logs sanitizados. Privilegios root no se usan en peticiones Backend. Pruebas sintéticas no justifican borrar registros históricos ni hacer smoke sobre STAGING.

## 7. Riesgos y bloqueantes de preparación

- Docker Engine inaccesible y servidor MySQL8/versión exacta no confirmados; no puede certificarse hoy el entorno listo para ejecutar pruebas.
- Nombre schema/contenedor/volumen no comprobados. La prohibición de SQL impide verificar nombre de base en esta etapa; se requiere esa puerta antes de usarlo.
- Falta DDL base o respaldo solo estructura revisado. Las migraciones no contienen CREATE de todas las entidades.
- Versión de Shell26.7 no es versión de servidor; confirmar compatibilidad de SQL/dump/restore y usar herramientas/versiones apropiadas, sin ignoreVersion ni cambio de esquema para ocultar errores.
- Collation, índices UNIQUE, FK, motor/rowformat y datos no comprobados. Esquema solo JPA puede dar falso positivo de paridad física.
- INSTANT/metadata locks/ejecución parcial; no ETA ni rollbackglobal garantizados. Describir recuperación por statements y respaldo probado.
- Aislamiento debe incluir URL, perfil, puerto, usuarios, uploads, proceso y context Docker; nombre distinto de schema por sí solo no protege otros entornos.
- Los secretos nuevos viven fuera de Git/OneDrive y del informe; no dump de cuentas/hashes reales. Tampoco distribuir Dockerinspect/env/logs con secretos.
- Backend QA no corrige automáticamente PUBLICADOSlegacy sin solución ni la limitación de Responsables aplazada. Registrar defectos previos/datos separados, no inventar reparación por SQL.

## 8. Criterios de aprobación y próximo paso

**Plan preparado, entorno de ejecución aún no habilitado.** Para autorizar la siguiente fase deben existir:

1. Servidor aislado local aprobado, versión MySQL8 exacta y herramienta compatible; Engine/context operativo y confirmado local.
2. Ausencia del nombre schema y recursos demostrada por inspección autorizada antes de creación, sin reutilizar recursos.
3. DDL base y seed sintético revisados/hash/procedencia; paridad física declarada y límites de cualquier esquema derivado de JPA.
4. Credenciales/usuarios/privilegios/env/uploads nuevos QA y rutas/puertos confirmados; ninguna configuración de dev/staging/producción.
5. Autorización explícita de creación/restauración/SQL/arranque, con límites de espera y recuperación ante fallo.
6. Tras ejecutar: migraciones/defaults/JPAvalidate, persistencia, concurrencia, historial y restauración pasan con evidencia; discrepancias y riesgos aceptados registrados.

No se aprueba STAGING por preparar este plan. Su NO-GO de QA-015 permanece hasta completar todas sus puertas. No se modificaron fuentes QA-009 a QA-014 ni se crearon scripts SQL/Compose o artefactos nuevos de aplicación.

**QA-016 se detiene con este plan y espera autorización para habilitar el entorno y ejecutar la fase de validación.**
