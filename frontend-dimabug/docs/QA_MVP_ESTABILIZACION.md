# DimaBug — estabilización del MVP

Fecha: 12 de octubre de 2026. Alcance: implementación local de bloques A, B y C; pruebas y preparación de despliegue (D). No se accedió a STAGING ni PRODUCCIÓN, no se ejecutaron migraciones, commits, push o despliegues.

Actualización de empaquetado: consultar [QA_EMPAQUETADO_STAGING.md](QA_EMPAQUETADO_STAGING.md). El Backend ahora incluye un `application.properties` seguro con mapeo de `DB_*`; su nuevo JAR pasó 500 pruebas y tiene un checksum distinto del artefacto anterior. Los resultados originales de este informe corresponden a la entrega anterior a esa corrección.

## Comprobación inicial

El repositorio contenía el cambio previo de budgets de `angular.json` y `dimabug-frontend.zip` sin seguimiento. Se conservaron. No había mantenimiento persistente de Hardware, Sistemas, Módulos y Frecuencias: sus API eran de consulta; la administración de maestros utilizaba almacenamiento del navegador. Usuarios, conocimiento, procedimientos, archivos y ejecuciones ya tenían servicios y pantallas, que se reutilizaron.

Versiones conservadas: Java 21, Spring Boot 4.1.1, Angular 19. No se modificaron dependencias. La confirmación de 32 FK y los datos de `dima_bug_staging` provienen del diagnóstico proporcionado por el usuario, no de consultas ejecutadas durante esta implementación.

## Bloque A — seguridad e integridad

- `/api/admin/**` exige `ROLE_ADMINISTRADOR` en Spring Security, antes de las reglas operacionales generales.
- Una política de aplicación, invocada por un interceptor MVC para consultas de contenido, protege también las rutas anidadas y descargas. `USUARIO` no puede consultar un conocimiento o procedimiento en borrador por su ID. Staff conserva consulta de borradores. Los resultados/efectividad de conocimiento eliminado solo pueden consultarlos administradores; el resto del contenido eliminado sigue no disponible.
- El listado de procedimientos filtra borradores para usuarios sin rol operacional.
- Al crear o cambiar clasificación de conocimiento se valida Hardware–Sistema y Sistema–Módulo, además de existencia/actividad. La edición de texto conserva una clasificación preexistente sin cambios, aunque un catálogo haya sido desactivado: no elimina referencias históricas.
- No se permite desactivar o cambiar el rol del último administrador activo. La operación es transaccional y bloquea las cuentas administrativas existentes antes de comprobar el resultado. Se mantiene la prevención de autodesactivación.
- Los procedimientos publicados son inmutables: no se editan datos, pasos ni materiales de la definición. El bloqueo sobre el procedimiento serializa edición y publicación. Esto evita que las ejecuciones existentes cambien de significado sin introducir tablas de versiones. Las ejecuciones siguen iniciándose sobre publicados; el checklist conserva su ciclo operacional y no permite modificar ejecuciones cerradas.
- Angular usa el nombre canónico `ADMINISTRADOR`, conserva `adminGuard` y `staffGuard`, y oculta acciones de edición de procedimientos publicados.
- En Usuarios se retiró el ocultamiento que simulaba eliminar cuentas. Se utiliza la activación/desactivación existente. Se eliminó el fallback PUT tras un PATCH de estado fallido: ese fallback podía anunciar éxito sin cambiar el estado. El rechazo del Backend ahora se muestra al usuario.

Riesgo/política: un procedimiento publicado que requiera correcciones necesita un nuevo borrador; no se implementó duplicación automática ni versionado. No se modificaron registros históricos existentes. La protección no reconstruye instrucciones que hubieran sido alteradas antes de esta entrega.

Pruebas: autorización administrativa por método y catálogo; acceso directo a hijos/archivos de borradores; consulta histórica exclusiva del administrador; último administrador; relación Hardware–Sistema; clasificación inactiva conservada; definición publicada inmutable; regresión de permisos operacionales.

## Bloque B — Backend de catálogos

Se añadió un puerto administrativo limitado a los cuatro catálogos y su adaptador JPA. Los controladores convierten DTO y delegan; las reglas están en los servicios de aplicación. No se implementó un CRUD universal ni se exponen entidades JPA.

Operaciones sobre `cl_hardware`, `cl_sistema`, `cl_modulo` y `cl_frecuencia`: consultar incluidos inactivos, crear, modificar y cambiar estado. No hay DELETE. Las asociaciones se guardan mediante `cl_hardware_sistema`; los módulos conservan su FK al sistema.

Validaciones: nombre no vacío y longitud, duplicados incluyendo inactivos, orden positivo de frecuencia, sistema requerido para módulo, referencias existentes y activas para nuevas asociaciones. Los nombres de módulos se consideran duplicados dentro del mismo sistema. Se impide mover a otro sistema un módulo usado por conocimientos. Los catálogos se bloquean durante escrituras; los módulos bloquean primero sus sistemas. Los cambios de nombres/clasificación reindexan los conocimientos afectados que no estén eliminados.

| Método | Ruta | Comportamiento |
|---|---|---|
| GET | `/api/admin/catalogos/{tipo}` | Lista activos e inactivos |
| GET | `/api/admin/catalogos/{tipo}/{id}` | Consulta un registro |
| POST | `/api/admin/catalogos/{tipo}` | Crea; responde 201 |
| PUT | `/api/admin/catalogos/{tipo}/{id}` | Modifica y asociaciones aplicables |
| PATCH | `/api/admin/catalogos/{tipo}/{id}/estado` | Activa/desactiva |

`tipo`: `hardware`, `sistemas`, `modulos`, `frecuencias`. Todas estas rutas requieren administrador. DELETE no está implementado.

DTO de guardado: `nombre`, `activo`, `sistemaOperativo` (Hardware), `descripcion` (Sistema), `sistemaId` (Módulo), `orden` (Frecuencia) y `sistemaIds` (Hardware). Los campos no aplicables se envían nulos o con lista vacía. El cambio de estado usa `{ "activo": false }` o `true`.

Las rutas operacionales existentes `/api/hardware`, `/api/sistemas`, `/api/sistemas/{id}/modulos`, `/api/hardware/{id}/sistemas` y `/api/frecuencias` conservan contratos de respuesta y permisos. Los cambios de acceso afectan contenido restringido, no el mantenimiento operacional autorizado de Técnico/Soporte.

Migraciones nuevas: **ninguna**. No hay tablas, columnas ni dependencias nuevas. La migración ya existente `V20260925_01__agregar_estado_eliminado_conocimiento.sql` no se ejecutó. Su mera presencia en resources no demuestra que exista un ejecutor de migraciones; no se añadió Flyway.

Pendiente de validación física: las pruebas locales no se conectaron a MySQL. Hay que validar collation, índices, restricciones adicionales no representadas en JPA y escrituras concurrentes en un entorno autorizado. Las restricciones únicas existentes siguen siendo la defensa final donde están declaradas.

## Bloque C — Angular

- Se reutilizaron `AdminCatalogoComponent`, sus estilos compartidos, formularios reactivos, ayuda y modal de carga. Ahora consulta y guarda mediante `AdminCatalogosApiService`, sin maestros simulados en el navegador.
- El panel administrativo muestra los cuatro catálogos con contadores obtenidos de la API y acceso a Usuarios. Los módulos aplazados se documentan como pendientes.
- Los formularios incluyen estado, orden, sistema padre y selección múltiple de sistemas asociados a Hardware. Incluyen vacíos, errores HTTP, confirmación de estado y bloqueo de envío mientras se guarda.
- Tras una escritura exitosa se invalidan las cachés de clasificación; ante error no se anuncia éxito ni se cambia la caché.
- Se retiró `AdminMaestrosService` y el Hardware simulado de `CatalogoService`. Se retiró el ocultamiento local de Hardware. Los caches de lectura de API se conservan; no actúan como fuente de modificaciones administrativas.
- «Nuevo error» consulta Hardware/Sistemas/Frecuencias reales. Su registro de incidencias continúa local y queda fuera del mantenimiento de catálogos solicitado.
- «Nuevo conocimiento» fuerza lectura de catálogos al entrar, muestra Hardware vacío explícitamente y conserva referencias existentes en edición. Una respuesta Hardware vacía ya no bloquea posteriores recargas.
- El encabezado de «Nuevo conocimiento» tiene distribución flex explícita, separación de texto/acción y apilado en ancho pequeño. Se midió que el enlace y el texto no se cruzan en ChromeHeadless con contenedores de 360 y 1024 px. La revisión humana en dispositivos/zoom forma parte de UAT.

Pruebas: contratos POST/PUT/PATCH, recarga del servicio, invalidación de cachés, errores, guard administrativo y regresión de staff, selectores API, layout del encabezado y rechazo del cambio de estado de Usuarios.

## Bloque D — compilación y evidencia

Los resultados finales se registran en `QA_RESULTADOS.txt`: Backend **497 pruebas, cero fallos/errores/omitidas**; Angular **40 pruebas exitosas en ChromeHeadless**; `mvnw verify` y build Angular production exitosos. Las suites incluyen las pruebas anteriores y las nuevas; no se conectan a STAGING. Los tests MVC usan servicios o repositorios simulados según la capa bajo prueba. Las pruebas de persistencia después de recargar verifican la nueva consulta HTTP; la persistencia real en MySQL y entre navegadores necesita UAT.

Se corrigieron fallos de configuración de pruebas MVC: faltaban beans de caché JWT y servicios de archivos. Los fixtures de escritura usan ahora procedimientos en borrador y asociaciones válidas; se añadieron pruebas negativas para las nuevas restricciones.

Comandos desde las carpetas del repositorio:

```powershell
# Backend: un JDK 21 instalado debe estar configurado en JAVA_HOME y PATH.
Set-Location dima-bug-backend
.\mvnw.cmd verify

# Frontend
Set-Location ..\frontend-dimabug
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
npm run build -- --configuration production
```

En esta sesión se reutilizó `node_modules`; no se ejecutó `npm ci` ni se actualizaron paquetes. JDK usado: `C:\Users\carol\.jdks\temurin-21.0.12.1`. La ejecución inicial de ChromeHeadless falló por el sandbox; se ejecutó correctamente con permisos ampliados. Maven Wrapper se usó con Java 21.

Artefactos locales:

- Backend: `dima-bug-backend/target/backend-0.0.1-SNAPSHOT.jar`.
- Frontend: `frontend-dimabug/dist/buscador-errores/browser/`.
- Índice exacto: `C:\Users\carol\OneDrive\Desktop\cata-front\buscador-errores\frontend-dimabug\dist\buscador-errores\browser\index.html`.

Advertencias: presupuesto CSS de `conocimiento-detalle.component.css` (22,53 kB frente a warning 16 kB) y `inicio.component.css` (18,62 kB frente a warning 16 kB). Bundle inicial final: 1,33 MB; budgets previos 1.5mb/2mb conservados. Maven advierte sobre API deprecadas y carga dinámica del agente Mockito en pruebas; no se añadieron dependencias para ocultar advertencias.

## Despliegue en STAGING — procedimiento para el operador

**No ejecutado.** No existen en el repositorio unidades de servicio, Docker/Compose, configuración de reverse proxy ni properties de conexión. No es posible fijar un comando de reinicio o ruta de publicación real sin esos datos.

Configuración comprobada:

- Desarrollo Angular: `/api` se redirige a `http://localhost:9090` mediante `proxy.conf.json`. Esto no prueba el puerto del servidor STAGING.
- Producción Angular: `environment.prod.ts` tiene `apiUrl: ''`, por lo que solicita `/api` al mismo origen. El servidor debe conservar el enrutamiento `/api` al Backend y fallback de rutas Angular a `index.html`.
- JWT requiere propiedades existentes `jwt.secret` y `jwt.expiration`; no se añadieron variables nuevas. No asumir que nombres de variables externos estén mapeados: confirmar cómo se suministran esas propiedades.
- Archivos utilizan `dimabug.storage.materiales-dir`, con default relativo `./uploads/materiales`. Confirmar el valor efectivo y directorio de trabajo; preservar los archivos al reemplazar el JAR.

Pasos:

1. Obtener del operador el mecanismo real de ejecución (servicio/proceso/contenedor), ubicación del JAR, raíz web, puerto, origen STAGING y origen de configuración externa. Verificar que la conexión corresponda a `dima_bug_staging`, sin copiar secretos al informe.
2. Respaldar artefactos anteriores de Backend y Frontend, configuración externa y checksum. Respaldar la base y materiales con el procedimiento existente; no modificar ni restaurar datos en esta fase.
3. Revisar las evidencias locales y diferencias. No desplegar el ZIP previo: corresponde a otra entrega. Empaquetar únicamente los nuevos artefactos verificados.
4. En una ventana acordada, sustituir el JAR y publicar el contenido de `dist/buscador-errores/browser` en las ubicaciones verificadas. Actualizar Backend y Frontend como una misma entrega. Mantener configuración, usuario de servicio, permisos y materiales.
5. Reiniciar mediante el mecanismo real documentado del servidor. No se proporciona un `systemctl`, nombre de contenedor o ruta ficticia.
6. Consultar `/api/health`, revisar logs y ejecutar el checklist inferior con cuentas de prueba autorizadas. Los nuevos catálogos se crean por interfaz como administrador, no mediante inserciones SQL directas.

No se requiere migración para esta entrega. Si el preflight descubre diferencias de esquema incompatibles con los mappings, detener el despliegue y revisar antes de introducir SQL.

## Rollback

1. Detener la aceptación de nuevas operaciones administrativas mientras se revierte la entrega.
2. Restaurar conjuntamente los artefactos de Backend y Frontend respaldados usando las ubicaciones y mecanismo verificados. Mantener configuración y materiales.
3. Reiniciar con el mecanismo real y comprobar health, login y consultas.
4. Conservar los registros de catálogo creados/editados durante la nueva versión: no hay migración que revertir ni DELETE que compensar. La versión anterior vuelve a consultar activos; su administración local no debe usarse para intentar reparar la base.
5. No restaurar automáticamente un backup de base: sobrescribiría operaciones realizadas desde el despliegue. Cualquier reversión de datos necesita revisión y autorización específica.

El rollback del código también revierte las protecciones de seguridad nuevas. Si estas son necesarias por un incidente, mantener el acceso administrativo limitado mientras se publica una corrección.

## Checklist posterior al despliegue / UAT pendiente

- [ ] Health, login y JWT funcionan con configuración externa existente.
- [ ] ADMINISTRADOR ve Administración; Técnico/Soporte/Usuario no pueden entrar y la API devuelve 403; sin token devuelve 401.
- [ ] Crear Sistema, Hardware asociado, Módulo del Sistema y Frecuencia con orden. Recargar y comprobar desde otro navegador.
- [ ] Editar y activar/desactivar los cuatro catálogos; confirmar persistencia real y ausencia de DELETE.
- [ ] Rechazar duplicados y referencias inválidas; revisar restricciones adicionales de MySQL.
- [ ] Cambios del catálogo se reflejan en Conocimiento y Nuevo error; no aparecen catálogos inactivos en nuevas selecciones.
- [ ] Editar solo texto de un conocimiento con clasificación desactivada conserva sus IDs.
- [ ] Rechazar Hardware–Sistema no asociado y Módulo de otro Sistema mediante API directa.
- [ ] No se puede desactivar/degradar al último administrador; comprobar solicitudes concurrentes y error visible en Usuarios.
- [ ] Usuario no puede obtener borradores ni sus hijos/archivos por ID. Histórico eliminado solo administrador.
- [ ] Técnico/Soporte conservan altas/ediciones operacionales permitidas y ejecución de publicados.
- [ ] Publicado no admite edición de definición/pasos/materiales; ejecuciones cerradas no se modifican.
- [ ] Encabezado sin solapamiento en móvil/escritorio y zoom 200 %; formularios navegables con teclado.
- [ ] Búsqueda, materiales, resultados, publicación y checklist sin regresiones.
- [ ] Validar bloqueos/transacciones y duplicados concurrentes en MySQL 8; las pruebas locales no acreditan este comportamiento físico.

## Pendientes para Sprint posterior

CRUD persistente de Departamentos, Responsables, Contactos y Pruebas; bitácora de auditoría inmutable; versionado/duplicación de procedimientos; persistencia de incidencias de «Nuevo error»; mejoras de accesibilidad de modales y revisión completa de cachés entre sesiones. No se implementaron CRUD genérico de todas las tablas ni rediseño general.

El inventario de archivos de esta entrega se adjunta en `QA_ARCHIVOS.txt`; separa el cambio previo de budgets y excluye el ZIP preexistente y artefactos generados.
