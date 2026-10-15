# QA-009 — administración persistente de soluciones

Fecha: 2026-10-13, America/Santiago.

## Resultado local

Se implementaron Backend y Angular sobre la rama `catalina`, preservando los cambios anteriores identificados en la Etapa A. No se hicieron commits, push, despliegues, conexiones a STAGING/PRODUCCIÓN ni ejecuciones de migraciones SQL. No se cambiaron dependencias, autenticación ni permisos operacionales.

### Reglas y contratos

- RN-08: PUBLICADO conserva al menos una solución activa; no se puede publicar sin activas. BORRADOR admite cero activas. Conflicto HTTP 409.
- RN-15: estado y autoservicio solo se administran bajo `/api/admin/**`, ya restringido a ADMINISTRADOR. Los técnicos conservan POST/PUT operacional.
- RN-16: las inactivas no se editan, no reciben nuevas asignaciones ni modificaciones de asignaciones, y no registran nuevos resultados. Se conservan consultas de asignaciones, resultados y efectividad según los controles existentes del conocimiento.
- RN-17: POST/PUT requieren título no vacío, máximo 150 caracteres. Los registros históricos admiten título nulo hasta su edición.
- POST fija activo=true/autoservicio=false. PUT conserva los atributos administrativos almacenados. El DTO operacional rechaza campos desconocidos, incluidos atributos administrativos, mediante validación localizada; no se modificó configuración JSON global.
- Listado operacional solo devuelve activas; listado administrativo incluye activas e inactivas. La indexación excluye contenido y asignaciones de inactivas e incorpora el título.
- Reactivar conserva autoservicio; Angular advierte si estaba habilitado. El indicador por sí solo no concede acceso ni implementa autoservicio/WhatsApp.

Endpoints añadidos:

- GET `/api/admin/conocimientos/{conocimientoId}/soluciones`
- PATCH `/api/admin/conocimientos/{conocimientoId}/soluciones/{solucionId}/estado` con `{ "activo": boolean }`
- PATCH `/api/admin/conocimientos/{conocimientoId}/soluciones/{solucionId}/autoservicio` con `{ "autoservicio": boolean }`

### Concurrencia

Los comandos bloquean primero el conocimiento propietario. Las desactivaciones/publicación leen las soluciones con bloqueo PESSIMISTIC_WRITE y refresh para evitar contar un estado obsoleto bajo REPEATABLE READ. Las ediciones del conocimiento, soluciones, asignaciones y nuevos resultados se coordinan mediante el mismo bloqueo padre. El refresh usa EntityManager dentro del adaptador; no se filtra persistencia hacia los puertos o servicios de aplicación.

### Angular

Título e instrucciones independientes en catálogo/formularios/detalle. Compatibilidad visual con títulos nulos. Desactivar/reactivar y autoservicio solo visibles para administrador. Inactivas no editables. Actualización posterior al éxito HTTP; errores no ocultan filas ni anuncian éxitos. Los antiguos IDs ocultos de sessionStorage se ignoran, nunca se convierten en desactivaciones. Se eliminó el almacenamiento del catálogo de soluciones en sessionStorage; otras cachés del proyecto se mantienen. Las soluciones operacionales se consultan siempre al servidor. Se versionó la clave de sección para no recuperar modelos antiguos.

## Archivos de esta implementación

Rutas relativas a `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores`.

Backend, prefijo `dima-bug-backend/src/main/java/cl/casol/backend/`:

Modificados:

- conocimiento/domain/Solucion.java
- conocimiento/application/port/out/ConocimientoRepository.java
- conocimiento/application/port/out/SolucionRepository.java
- conocimiento/application/service/MantenerConocimientoService.java
- conocimiento/application/service/MantenerSolucionService.java
- conocimiento/application/service/GestionarAsignacionSolucionService.java
- conocimiento/infrastructure/persistence/entity/SolucionEntity.java
- conocimiento/infrastructure/persistence/mapper/SolucionMapper.java
- conocimiento/infrastructure/persistence/repository/ConocimientoJpaRepository.java
- conocimiento/infrastructure/persistence/repository/SolucionJpaRepository.java
- conocimiento/infrastructure/persistence/adapter/ConocimientoRepositoryAdapter.java
- conocimiento/infrastructure/persistence/adapter/SolucionRepositoryAdapter.java
- conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java
- conocimiento/infrastructure/web/SolucionController.java
- conocimiento/infrastructure/web/dto/GuardarSolucionRequest.java
- conocimiento/infrastructure/web/dto/SolucionResponse.java
- seguimiento/application/service/RegistrarEfectividadSolucionService.java
- shared/infrastructure/web/GlobalExceptionHandler.java

Nuevos:

- conocimiento/application/service/AdministrarSolucionService.java
- conocimiento/infrastructure/web/AdminSolucionController.java
- conocimiento/infrastructure/web/dto/CambiarEstadoSolucionRequest.java
- conocimiento/infrastructure/web/dto/CambiarAutoservicioSolucionRequest.java
- conocimiento/domain/exception/UltimaSolucionActivaException.java
- conocimiento/domain/exception/SolucionInactivaException.java
- conocimiento/domain/exception/TituloSolucionInvalidoException.java
- `dima-bug-backend/src/main/resources/db/migration/V20261013_01__ampliar_solucion.sql`

Pruebas Backend, prefijo `dima-bug-backend/src/test/java/cl/casol/backend/`:

Modificadas:

- SolucionSecurityIntegrationTest.java
- conocimiento/application/service/MantenerConocimientoServiceTest.java
- conocimiento/application/service/MantenerSolucionServiceTest.java
- conocimiento/application/service/GestionarAsignacionSolucionServiceTest.java
- seguimiento/application/service/RegistrarEfectividadSolucionServiceTest.java

Nuevas:

- conocimiento/application/service/AdministrarSolucionServiceTest.java
- conocimiento/application/service/SolucionConcurrenciaQa009Test.java
- conocimiento/infrastructure/persistence/adapter/SolucionPersistenciaQa009Test.java
- conocimiento/infrastructure/persistence/adapter/BusquedaSolucionQa009Test.java

Frontend, prefijo `frontend-dimabug/`:

- src/app/core/models/conocimiento.model.ts
- src/app/core/models/catalogo.model.ts
- src/app/core/services/conocimiento.service.ts
- src/app/features/soluciones/soluciones.component.ts
- src/app/features/soluciones/soluciones.component.html
- src/app/features/soluciones/soluciones.component.css
- src/app/features/conocimiento/conocimiento-detalle.component.ts
- src/app/features/conocimiento/conocimiento-detalle.component.html

Pruebas nuevas:

- src/app/core/services/conocimiento-soluciones.service.spec.ts
- src/app/features/soluciones/soluciones.component.spec.ts
- src/app/features/conocimiento/conocimiento-soluciones-detalle.component.spec.ts

Este informe: `frontend-dimabug/docs/QA009_IMPLEMENTACION.md`.

## Validación y alcance de la evidencia

Backend: Java 21, Maven Wrapper offline, `mvnw.cmd -o test` y `mvnw.cmd -o verify`: 542 pruebas, 0 fallos, 0 errores, 0 omitidas. Se incluyen pruebas MVC reales de autenticación/autorización con servicios simulados; pruebas unitarias de reglas; roundtrip de mapper, contratos de adaptadores y anotaciones/bloqueos de persistencia; exclusión SQL de inactivas; dos operaciones concurrentes entre hilos usando un puerto con bloqueo real simulado.

Estas pruebas NO ejecutan SQL contra MySQL ni comprueban locks de InnoDB en una base real. La validación de persistencia y concurrencia física queda pendiente en un entorno aislado autorizado con el esquema ampliado. No hay H2, Flyway, Liquibase ni dependencias nuevas.

Angular: 62 pruebas aprobadas en ChromeHeadless, incluyendo título, roles, PATCH, errores HTTP, refresco de catálogo/detalle y ausencia de ocultamiento local. Se realiza compilación de producción; bundle inicial 1,34 MB. Persisten warnings de presupuesto CSS de Inicio (18,62 kB) y Conocimiento (22,53 kB), contra warning 16 kB. Los tests emiten avisos existentes de disabled en formularios reactivos y el entorno Chrome puede emitir advertencias al cerrar el proceso. No sustituyen UAT visual/manual.

`git diff --check` global señala dos líneas vacías finales preexistentes de los CSS de Hardware/admin-catalogo, que no se tocaron en QA-009. La comprobación de formato se limita adicionalmente a los archivos de QA-009.

Artefactos locales:

- Backend: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/target/backend-0.0.1-SNAPSHOT.jar`
- SHA256 Backend: `40bbec7e4a6bd5d6fc7b4926e4bffafbac5ad88e794eb2eeb49a0cf0fecb436e`
- Frontend: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/dist/buscador-errores/browser/index.html`

El JAR incluye JarLauncher, application.properties, AdminSolucionController y el archivo SQL. Incluir el SQL no lo ejecuta: no se añadió runner de migraciones.

## Pendientes y orden para un despliegue futuro autorizado

1. Revisar esquema y versión MySQL, soporte INSTANT, columnas/FK y respaldo. No ejecutar el script a ciegas ni repetirlo si ya existen columnas.
2. Detectar conocimientos actualmente PUBLICADOS sin soluciones: RN-08 protege operaciones nuevas, no inventa datos para corregir inconsistencias anteriores.
3. Probar migración, persistencia, locks InnoDB e historial sobre un entorno aislado autorizado; ejecutar UAT de administrador/técnico y revisión móvil/teclado.
4. Aplicar la migración aditiva antes del JAR nuevo; los registros existentes quedan activos, con autoservicio=false y título=NULL.
5. Desplegar Backend y Angular de forma coordinada, con pausa temporal de escrituras de soluciones si es necesaria. RN-17 obliga a enviar título: un frontend anterior podrá leer, pero sus POST/PUT sin título fallarán con 400. Forzar actualización de clientes antiguos.
6. Verificar búsquedas, desactivación/última activa, publicación, reactivación con autoservicio e historial desde otra sesión.
7. No volver a un Backend antiguo tras usar desactivaciones sin una contingencia revisada: aquel ignora estado y autoservicio. Mantener columnas; no usar DROP como rollback rutinario.

La autorización de despliegue y ejecución SQL es independiente. No se hizo ningún cambio en STAGING/PRODUCCIÓN.
