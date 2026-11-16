# QA-016 — Etapa de preparación Docker Desktop/WSL2

Fecha: 14-10-2026. Referencias: QA016_PLAN_MYSQL_AISLADO.md y QA016_1_DIAGNOSTICO_DOCKER.md.

## Resultado

Docker Desktop se inició mediante `docker desktop start`, conforme a la autorización de comprobar su arranque. El comando confirmó «Starting Docker Desktop». Posteriormente `docker desktop status` informó running, `docker version` respondió con Client y Server y `docker info` respondió correctamente. No fue necesario modificar configuración, instalar software ni reiniciar Windows.

La causa probable del diagnóstico anterior queda respaldada: el Engine no respondía mientras Desktop estaba detenido, y respondió tras iniciarlo. No se encontraron errores adicionales de arranque en las consultas realizadas; no se efectuó una prueba prolongada de estabilidad.

## Engine e integración WSL2

| Comprobación | Resultado |
| --- | --- |
| Desktop | 4.94.0 (241994), running |
| Engine | 29.8.2, API 1.56, linux/amd64 |
| Contexto utilizado | desktop-linux, explícito en consultas; no se cambió el contexto |
| Kernel | 6.6.87.2-microsoft-standard-WSL2 |
| Distribución docker-desktop | Running, WSL versión 2 |
| Ubuntu | Stopped, WSL versión 2 |
| Almacenamiento Engine | overlayfs; raíz /var/lib/docker |
| Cgroups | Versión 2 |
| Recursos visibles al Engine | 10 CPU lógicas y 16.518.959.104 bytes de RAM (aproximadamente 15,4 GiB) |

Se confirma que el Engine utiliza WSL2. No se habilitó ni se probó la integración de comandos Docker dentro de Ubuntu: para ejecutar las consultas desde PowerShell no fue necesario iniciar esa distribución. No confundir el backend WSL2 funcionando con integración habilitada para todas las distribuciones.

## Configuración e inventario existentes

Se inspeccionó selectivamente `C:\Users\carol\AppData\Roaming\Docker\settings-store.json`, sin publicar el archivo completo ni secretos. `AutoStart=false`. Los campos consultados de backend, integración de distribuciones y límites CPU/RAM no estaban definidos en ese archivo; su ausencia no demuestra una opción deshabilitada. Se utilizó el estado efectivo del Engine para comprobar backend y recursos. No se modificó el archivo.

Inventario después del arranque:

- Contenedores: 0; ejecutándose: 0.
- Volúmenes: ninguno listado.
- Imágenes existentes: mysql:8.0.36 y testcontainers/ryuk:0.14.0. Ambas se conservaron.
- Digest informado para MySQL: `mysql@sha256:a532724022429812ec797c285c1b540a644c15e248579c6bfdf12a8fbaab4964`.
- Puertos propuestos 13316 y 19090: sin listeners durante la consulta; no se reservaron.

La imagen local es candidata para QA, pero todavía no se ejecutó para confirmar `SELECT VERSION()`, salud, inicialización ni compatibilidad con las migraciones. No se descargaron imágenes ni se crearon recursos. El espacio reportado por Docker para esa imagen no constituye un presupuesto de disco de la futura base.

## Servicios necesarios para DimaBug

| Componente | Necesidad y propuesta |
| --- | --- |
| MySQL 8 | Único servicio externo identificado como necesario para esta validación de persistencia. Futuro contenedor dedicado, volumen independiente, publicación 127.0.0.1:13316. |
| Backend Spring Boot | Aplicación Java 21 existente; puede ejecutarse localmente sin contenerizar. Proponer puerto 19090, bind loopback, ddl-auto=validate y SQL init deshabilitado, mediante configuración externa autorizada. |
| Angular | Aplicación existente; servir localmente para UAT con destino API de QA explícito. No necesita un contenedor adicional para validar MySQL. |
| Materiales/uploads | Almacenamiento filesystem, no un servicio adicional. El Backend usa por defecto ./uploads/materiales; QA necesita un directorio independiente para evitar escribir uploads existentes. |
| Autenticación | JWT en el Backend, no un servidor de identidad adicional identificado. Requiere jwt.secret y jwt.expiration externos, además de cuentas sintéticas controladas. |

El POM incluye mysql-connector-j y las propiedades datasource usan DB_URL, DB_USERNAME y DB_PASSWORD. No se identificaron dependencias configuradas de Redis, RabbitMQ, Kafka, MongoDB o SMTP en los archivos revisados. Ryuk no es un servicio funcional del proyecto ni se necesita iniciarlo para un MySQL manual; su imagen existente se conserva.

No se encontró Dockerfile ni Compose del proyecto. Crear uno no es necesario para completar este diagnóstico y no se hizo. Tampoco se inició Backend o Angular contra una base.

## Esquema SQL completo

La nueva búsqueda en los dos proyectos no encontró DDL completo, schema, dump, backup, SQL comprimido ni archivo DDL inicial. Solo existen las tres migraciones incrementales ya documentadas, en:

`C:\Users\carol\OneDrive\Desktop\cata-front\buscador-errores\dima-bug-backend\src\main\resources\db\migration`

1. V20260925_01__agregar_estado_eliminado_conocimiento.sql.
2. V20261013_01__ampliar_solucion.sql (QA-009).
3. V20261014_01__organizacion_persistente.sql (QA-012).

Estas migraciones requieren tablas preexistentes; no permiten reconstruir por sí solas DimaBug. La ausencia corresponde al ámbito del proyecto revisado, no a todos los discos o respaldos externos. No se accedió a STAGING ni PRODUCCIÓN para buscar estructura.

## Problemas y pendientes

1. **Bloqueante de esquema:** falta una estructura base revisada. Obtener una exportación de estructura autorizada, sin datos personales, o preparar un DDL sintético revisado contra JPA y SQL. Una estructura generada solo desde JPA no prueba equivalencia física con STAGING.
2. **MySQL aún no operativo:** disponer de imagen no equivale a tener una instancia validada. Base `dima_bug_qa_integracion` todavía no creada y su existencia no comprobada mediante SQL.
3. **Configuración QA pendiente:** secretos nuevos fuera de Git, usuario específico, uploads independientes, URL y perfil QA, límites de recursos y política de respaldo. No modificar los valores actuales para otros entornos.
4. **Pruebas pendientes:** compatibilidad MySQL real, ALTER INSTANT, versionado/concurrencia, preservación histórica y regresión QA-009 a QA-014.
5. **Integración Ubuntu:** no necesaria para este flujo PowerShell; requeriría revisión y autorización antes de cualquier cambio si posteriormente se decide usarla.

## Próximos pasos recomendados — requieren autorización

1. Revisar y aprobar la fuente del esquema base antes de restaurar o ejecutar SQL.
2. Aprobar creación exclusiva de MySQL QA utilizando la imagen local fijada por digest, contenedor dimabug-qa016-mysql, volumen dimabug-qa016-mysql-data y puerto loopback 13316. El inventario actual no contiene esos recursos, pero debe volver a comprobarse inmediatamente antes de crearlos.
3. Definir secretos y almacenamiento independientes. No incorporar credenciales en archivos versionados ni importar datos locales Angular automáticamente.
4. Autorizar por separado comprobación del nombre de base, creación/restauración y ejecución ordenada de migraciones después del preflight. No reutilizar ni borrar una base encontrada con el nombre propuesto.
5. Validar Backend con ddl-auto=validate, ejecutar la matriz QA-016 y UAT Angular contra el entorno aislado; evaluar después el dictamen de despliegue.

**Estado:** GO para preparar el siguiente paso local con autorización; validación MySQL y STAGING siguen pendientes. Se mantiene el NO-GO para desplegar a STAGING.

## Acciones y preservación

Se inició Docker Desktop y su backend WSL2; se deja ejecutándose. Se realizaron consultas de versión, estado, configuración selectiva, inventario, imagen, puertos, dependencias y archivos SQL. No se eliminaron contenedores, imágenes o volúmenes, no se crearon contenedores/bases, no se ejecutó SQL, no se modificó código fuente ni se realizaron operaciones Git de escritura. La única escritura explícita en el proyecto fue este informe; Docker puede actualizar su estado y logs internos durante su arranque normal.

No se ejecutaron pruebas Backend/Angular: esta etapa comprobó infraestructura local, sin una base preparada. Se detiene aquí antes de cualquier cambio adicional.
