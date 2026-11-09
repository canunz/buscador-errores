# CI de pruebas del backend

Estado al 2026-10-09: **workflow implementado; ejecución real en GitHub Actions
pendiente**. La validación local no acredita CI exitoso ni autoriza producción.

## Activación y jobs

El [workflow](../.github/workflows/backend-tests.yml) se activa en Pull Request
(apertura, actualización y reapertura con los tipos predeterminados de GitHub) y
en push a `main`, rama principal confirmada mediante el remoto. No usa filtros
por rutas: también ejecuta las suites cuando cambia la documentación.

Los jobs son independientes y usan runners hospedados `ubuntu-24.04`. Un fallo de
una suite no evita que se intente la otra; no hay `needs` entre ellos.

| Job | Comando desde la raíz del repositorio | Reportes | Límite |
| --- | --- | --- | --- |
| Pruebas habituales (`habitual`) | `./mvnw -B test` | `target/surefire-reports/` | 20 minutos |
| Integración MySQL temporal (`integration-mysql`) | `./mvnw -B -Pintegration-mysql verify` | `target/failsafe-reports/` | 30 minutos |

Java 21 Temurin se configura mediante `setup-java`, con caché del repositorio
local Maven cuya clave depende de `pom.xml`. No se cachean `target`, archivos de
materiales ni volúmenes Docker. El wrapper 3.3.4 descarga Maven 3.9.16 según
`.mvn/wrapper/maven-wrapper.properties`; CI no usa el Maven global del runner.
Como `mvnw` está registrado con modo Git `100644`, cada job ejecuta `chmod +x mvnw`
solo en su checkout temporal. El POM y el wrapper no se modificaron.

El perfil `integration-mysql` omite Surefire y ejecuta mediante Failsafe las cinco
clases explícitas del POM: `MySqlReferentialIntegrityIT`, `ConocimientoJpaIT`,
`AutenticacionHttpIT`, `MaterialesArchivoIT` y `ProcedimientosEjecucionesResultadosIT`.
`failIfNoTests=true` evita un éxito sin ninguna integración. Agregar otra clase IT
requiere revisar esa lista; el workflow no amplía por sí solo la selección.

## Docker y aislamiento

El runner necesita Docker Engine Linux accesible en `/var/run/docker.sock`, puertos
locales disponibles y acceso de red para descargar Maven, dependencias, acciones,
la imagen MySQL y el contenedor de limpieza de Testcontainers. La imagen del runner
[incluye Docker](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md);
el job verifica el socket y la versión del servidor antes de ejecutar integración.
No instala Docker Desktop, WSL2 ni Docker Compose.

Se reutiliza
[`MySqlIntegrationSupport`](../src/test/java/cl/casol/backend/integration/MySqlIntegrationSupport.java):

- Imagen fija `mysql:8.0.36`, esquema exclusivo `dimabug_integration`, contraseña
  aleatoria generada para el contenedor, sin credenciales externas ni fallback.
- `withReuse(false)` y tmpfs en `/var/lib/mysql`; datos sin volumen persistente.
  CI deshabilita reutilización y mantiene habilitado Ryuk.
- Validación del host loopback, contenedor activo, URL/puerto y catálogo antes de
  abrir conexiones. El datasource Spring proviene de este soporte.
- Importación nativa del esquema saneado, diagnóstico, migración de cinco FK y
  snapshot RN-PROC-01 solo dentro del contenedor; comprobación de restricciones.
- Contextos Spring con `integration-mysql.properties`, `ddl-auto=none` y
  inicialización SQL automática deshabilitada. Materiales usan directorios temporales.

No hay servicio MySQL externo ni servicio MySQL duplicado en el workflow. `DOCKER_HOST`
se fija al socket local. No se suministran `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
configuraciones DEV, secretos de producción ni rutas reales de almacenamiento.
Si Docker o MySQL temporal fallan, el job debe fallar: no se omiten esas pruebas.
El cierre normal detiene contenedores; Ryuk ayuda a limpiar recursos y el runner
hospedado es desechable. Esto no garantiza ejecución de pasos finales ante una
terminación forzada del runner.

## Reportes y resultado

Cada job publica su directorio de reportes con `if: always()`, incluso después de
un fallo de pruebas, como artefactos `surefire-reports` y `failsafe-reports`, con
retención configurada de 14 días. Se incluyen XML y archivos diagnósticos generados
por Maven, no el directorio completo del proyecto ni un dump de datos.

Si la compilación, Docker o la preparación fallan antes de crear reportes, la acción
advierte que no hay archivos: no puede publicar reportes inexistentes. Una cancelación
o caída del runner también puede impedir la carga. No se usa `continue-on-error`,
`|| true` ni opciones para ignorar fallos: Maven conserva su código de salida y
Failsafe `verify` hace fallar el job ante pruebas fallidas. Publicar artefactos no
convierte un job fallido en exitoso.

## Seguridad

Permiso declarado: `contents: read`; los restantes permisos de `GITHUB_TOKEN` no
se conceden. Checkout usa `persist-credentials: false`. Las tres acciones oficiales
están fijadas por SHA completo, correspondientes a checkout 7.0.1, setup-java 6.0.1
y upload-artifact 7.0.2. Sus actualizaciones requieren revisión del SHA y release.
No se solicita acceso a environments, OIDC, paquetes, escritura de PR ni despliegue.

Se utiliza `pull_request`, no `pull_request_target`: no hay referencias a `secrets`
ni variables del repositorio en comandos. GitHub limita el token y los secretos de
[PR de forks](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows).
La autorización de ejecución para colaboradores externos depende de las políticas
del repositorio; no se modificaron dichas políticas. No cambiar este diseño a un
runner compartido con acceso a DEV o producción para ejecutar código de PR externos.

No se imprimen variables de entorno, tokens ni contraseñas desde el workflow.
Las pruebas usan datos sintéticos; los reportes/logs pueden contener errores SQL y
datos sintéticos. Revisar cualquier nueva fixture o logging antes de publicarlos:
este repositorio es público según la consulta remota y CI no es un sanitizador de
secretos. No introducir credenciales en caché, código de pruebas ni artefactos.

## Validación y evidencia

Revisión local: POM/perfil, wrapper, soporte MySQL, configuración de integración,
rutas y permisos. YAML analizado con PyYAML disponible en el equipo; comprobaciones
estructurales de eventos, jobs, comandos, SHA, artefactos y propagación de fallos.
Esto no sustituye la validación del motor de GitHub Actions ni un ensayo Linux.
No se repitieron pruebas Java: el cambio solo añade CI y documentación.

La consulta autenticada al remoto confirmó `main` y acceso de lectura. Para el commit
local `19663636994b7b534b605b51a76bad7ba868146d`, el conector devolvió cero ejecuciones
asociadas de tipo Pull Request; ese método solo consulta PR y una primera página,
por lo que no prueba ausencia de cualquier otro CI. El nuevo workflow está local,
sin commit/push de esta tarea: **no hay URL, ID ni resultado de ejecución remota
que acrediten su funcionamiento**. Los 45/474 PASS previos no son resultados de CI.

Para obtener evidencia real, publicar los cambios revisados junto con la
infraestructura de pruebas requerida por el checkout remoto mediante el proceso
normal del equipo. Abrir/actualizar el PR o hacer push autorizado a `main`, y revisar
ambos jobs en la pestaña Actions. Registrar URL del run, SHA probado, evento, tiempos,
conteos PASS/FAIL/ERROR/SKIPPED y disponibilidad de ambos artefactos. No se publicó
el árbol local ni se configuraron reglas remotas en esta tarea.

## Ante fallos y pendientes

1. Identificar el primer paso fallido y descargar los reportes disponibles del job.
2. Separar fallo de prueba de fallo de preparación: JDK/Maven, descarga, caché,
   Docker/socket, imagen o importación del esquema temporal.
3. Reproducir con el comando correspondiente y un entorno aislado. En Windows usar
   `mvnw.cmd`; para integración, mantener Docker Linux y el soporte sin fallback.
4. Conservar la aserción y evidencia; corregir bajo el alcance autorizado y ejecutar
   de nuevo. No usar credenciales DEV ni desactivar restricciones para pasar CI.
5. Si falló caché/servicio externo, revisar logs y reintentar tras resolver la causa;
   un reintento exitoso no elimina la evidencia del fallo anterior.

Pendientes: primera ejecución remota de ambas suites, comprobación del comportamiento
Linux de archivos/concurrencia, políticas de Actions y checks requeridos para merge.
Un job fallido produce CI fallido, pero bloquear el merge exige configuración de
protección/rulesets por el equipo. Skill 4 mantiene CIERRE CON CONDICIONES y el
riesgo de recuperación de archivos sigue pendiente de aceptación formal.

Referencias de acciones: [checkout](https://github.com/actions/checkout/releases/tag/v7.0.1),
[setup-java](https://github.com/actions/setup-java/releases/tag/v6.0.1) y
[upload-artifact](https://github.com/actions/upload-artifact/releases/tag/v7.0.2).
