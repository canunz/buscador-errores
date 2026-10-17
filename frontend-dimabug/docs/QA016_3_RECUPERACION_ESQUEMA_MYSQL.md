# QA016.3 — Recuperación del esquema MySQL

Fecha: 14-10-2026. Actividad de análisis; no hubo conexiones MySQL, ejecución SQL, exportaciones, creación de contenedores ni cambios de código. Referencias: QA016_PLAN_MYSQL_AISLADO.md y QA016_2_PREPARACION_DOCKER_WSL2.md.

## 1. Resultado y recomendación

No existe un esquema inicial completo en los archivos del proyecto revisados. La fuente recomendada es una **exportación exclusivamente de estructura del esquema de desarrollo confirmado por su propietario**, seguida de revisión y restauración en una instancia Docker independiente. No utilizar STAGING/PRODUCCIÓN ni copiar filas de desarrollo.

MySQL Shell 26.7.0 está disponible; mysql/mysqldump no fueron localizados en PATH en QA016. La vía principal propuesta utiliza `util.dumpSchemas` con `ddlOnly:true` y `util.loadDump` con `loadData:false`. El resultado es un directorio de SQL y metadatos, no un único dump SQL. Es necesario comprobar compatibilidad real de Shell 26.7 con las versiones origen/destino antes de ejecutar; no ignorar errores de versión.

La imagen local mysql:8.0.36 está disponible, pero no se creó una instancia. Las versiones, restricciones, collation y contenido del servidor de desarrollo siguen sin verificarse físicamente.

## 2. Configuración Spring Boot y migraciones

Archivos revisados bajo `C:\Users\carol\OneDrive\Desktop\cata-front\buscador-errores\dima-bug-backend`:

- `pom.xml`: Spring Boot 4.1.1, Java 21, Spring Data JPA y mysql-connector-j. No declara Flyway ni Liquibase ni plugins de migración.
- `src/main/resources/application.properties`: spring.datasource.url=${DB_URL}, username=${DB_USERNAME}, password=${DB_PASSWORD}.
- No se identificó configuración Flyway/Liquibase, schema.sql, data.sql, ddl-auto ni sql.init en los recursos principales revisados. La carpeta db/migration y los nombres V... no implican ejecución automática sin el motor correspondiente.

El destino real de desarrollo no está definido en el archivo compartido: depende del entorno de ejecución. Antes de autorizar una conexión, confirmar host, puerto, nombre de esquema y propietario a partir de la configuración de desarrollo, **sin publicar URL con secretos ni valores de DB_PASSWORD**. No asumir localhost o dima_bug_dev. No iniciar el Backend contra desarrollo para descubrirlo: podría provocar escrituras.

Para QA, configurar externamente DB_URL/DB_USERNAME/DB_PASSWORD, JWT_SECRET/JWT_EXPIRATION y perfil separado; usar explícitamente SPRING_JPA_HIBERNATE_DDL_AUTO=validate y SPRING_SQL_INIT_MODE=never. Estas son propuestas futuras, no cambios aplicados.

Migraciones disponibles en `src/main/resources/db/migration`:

| Archivo | Efecto | Precondición |
| --- | --- | --- |
| V20260925_01__agregar_estado_eliminado_conocimiento.sql | Amplía conocimiento_estado con ELIMINADO | co_conocimiento existente y ENUM revisado |
| V20261013_01__ampliar_solucion.sql | Añade solucion_titulo nullable, solucion_estado=1 y solucion_autoservicio=0 | co_solucion existente; columnas ausentes; ALTER INSTANT compatible |
| V20261014_01__organizacion_persistente.sql | Añade departamento_version/contacto_version y u_organizacion_control, con fila control_id=1 | Tablas base existentes; columnas/tabla ausentes; espera de metadata lock limitada a 5s |

Ninguna reconstruye el esquema base. Tras exportar, comparar si cada cambio ya existe: desarrollo podría estar actualizado parcialmente. No ejecutar de nuevo scripts ya aplicados ni marcar una migración como aplicada solo por el nombre de archivo. Orden futuro: restaurar estructura → revisar estado de conocimiento → QA009 → QA012 → validate y pruebas. Si u_organizacion_control ya viene en el DDL, su fila no vendrá porque la exportación excluye datos; habrá que preparar un seed técnico revisado para control_id=1, sin repetir el CREATE/ALTER. QA012 consta de varias sentencias y puede quedar parcialmente aplicada; no asumir rollback conjunto ni sustituir INSTANT por COPY automáticamente.

## 3. Tablas requeridas por el código

Inventario mínimo confirmado: **25 tablas: 24 declaradas en JPA (incluida la tabla de unión) y una adicional JDBC**. Este recuento corrige la estimación de 26 comunicada durante la revisión. No es inventario físico del servidor.

| Área | Tablas |
| --- | --- |
| Catálogos y asociación | cl_frecuencia, cl_hardware, cl_hardware_sistema, cl_modulo, cl_sistema |
| Conocimiento | co_causa, co_conocimiento, co_conocimiento_prueba, co_material_apoyo, co_prueba, co_sintoma, co_solucion, co_solucion_asignacion |
| Búsqueda JDBC | co_conocimiento_busqueda |
| Procedimientos | pr_paso, pr_procedimiento |
| Ejecución/seguimiento | se_ejecucion, se_ejecucion_paso, se_resultado |
| Identidad/organización | u_departamento, u_departamento_contacto, u_organizacion_control, u_responsable, u_rol, u_usuario |

`BusquedaConocimientoJdbcAdapter.java` utiliza co_conocimiento_busqueda con conocimiento_id y contenido_busqueda, INSERT...ON DUPLICATE KEY UPDATE y MATCH...AGAINST. Debe verificarse su clave sobre conocimiento_id y su índice FULLTEXT: Hibernate validate por sí solo no cubre esta tabla ni certifica todos los índices/FK. Omitirla al generar estructura solo desde JPA dejaría incompleta la aplicación.

Conservar PK, FK, índices, unicidad, ENUM, defaults, nullability, AUTO_INCREMENT, charset/collation, motores y columnas de versión. Las restricciones anotadas JPA no certifican las físicas de MySQL. Exportar la estructura de u_usuario/u_rol es necesario; **exportar sus filas no lo es y está prohibido en este plan**. Revisar objetos adicionales presentes en desarrollo antes de declararlos innecesarios.

## 4. Preparación de la exportación de desarrollo — pendiente de autorización

1. Confirmar endpoint y esquema de desarrollo; documentar solo metadatos no secretos. Verificar versión del servidor y que no corresponde a STAGING/PRODUCCIÓN.
2. Utilizar una cuenta existente autorizada para consultar estructura, con los mínimos permisos necesarios; no crear usuarios ni otorgar privilegios en desarrollo en esta etapa. Si faltan permisos, detenerse y solicitar intervención del propietario.
3. Inventariar tablas, vistas, triggers, rutinas y eventos mediante consultas de metadatos autorizadas. El código revisado no prueba que estos últimos no existan físicamente.
4. Coordinar una ventana sin cambios de DDL. La exportación propuesta consistent:false evita solicitar los bloqueos globales de la opción consistent:true, pero no garantiza una estructura coherente si se altera durante la lectura y puede adquirir bloqueos de metadatos breves. Repetir/revisar si cambia el esquema.
5. Usar una carpeta nueva, privada, fuera de Git y OneDrive. No sobrescribir un dump existente. La ruta ilustrativa es C:/DimaBug-QA/estructura-dev; todavía no existe ni se creó.

### Vía principal: MySQL Shell ya disponible

Todos los comandos siguientes son **propuestos, no ejecutados**. Los marcadores deben sustituirse localmente por valores confirmados; la contraseña se solicita interactivamente, nunca en URI/argumentos.

En PowerShell del PC:

```powershell
& 'C:\Program Files\MySQL\MySQL Shell 26.7\bin\mysqlsh.exe' --mysql --js `
  --host='<HOST_DEV_CONFIRMADO>' --port=<PUERTO_DEV> `
  --user='<CUENTA_ESTRUCTURA_DEV>' --password
```

En la sesión JavaScript conectada **solo a desarrollo**, tras revisar la identidad del destino:

```javascript
util.dumpSchemas(['<ESQUEMA_DEV_CONFIRMADO>'], 'C:/DimaBug-QA/estructura-dev', {
  ddlOnly: true,
  consistent: false,
  routines: false,
  events: false,
  triggers: false
});
```

Este bloque captura tablas/vistas y sus definiciones sin filas, y excluye programas almacenados. **No declarar el resultado completo si el inventario encuentra triggers, rutinas o eventos relevantes:** detener esa variante y aprobar una exportación revisada con esos objetos incluidos. Revisar primero sus cuerpos por secretos embebidos, efectos automáticos y referencias a otros esquemas; no activar eventos en QA por defecto. Las vistas también requieren revisión de DEFINER, SQL SECURITY y referencias calificadas al esquema original.

dumpSchemas no exporta cuentas MySQL, roles ni grants del servidor; no usar dumpInstance ni incluir los esquemas mysql/sys/performance_schema/information_schema. La opción ddlOnly excluye también los registros de u_usuario y sus hashes. Fuente: [utilidades de exportación MySQL Shell](https://dev.mysql.com/doc/mysql-shell/8.0/en/mysql-shell-utilities-dump-instance-schema.html).

### Alternativa si se dispone de mysqldump compatible

No instalarlo todavía. Si ya existe en otro equipo autorizado, proponer una exportación revisable de tablas/vistas:

```powershell
mysqldump --host='<HOST_DEV_CONFIRMADO>' --port=<PUERTO_DEV> `
  --user='<CUENTA_ESTRUCTURA_DEV>' --password --no-data `
  --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF `
  --skip-triggers --skip-add-drop-table --skip-comments `
  --default-character-set=utf8mb4 `
  --result-file='C:\DimaBug-QA\estructura-dev.sql' '<ESQUEMA_DEV_CONFIRMADO>'
```

No usar --all-databases ni --databases: se evita exportar otros esquemas y se reduce el riesgo de CREATE DATABASE/USE de desarrollo. Revisar aun así referencias calificadas. Triggers/rutinas/eventos requieren el mismo inventario y aprobación descritos arriba. Usar --result-file, no redirección PowerShell que puede producir un encoding incompatible. Fuente: [mysqldump](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html).

## 5. Revisión del artefacto antes de importar

- Confirmar ejecución exitosa, estructura íntegra y listado de tablas, incluyendo co_conocimiento_busqueda. Registrar versiones y SHA256 de los archivos.
- No debe haber archivos de datos TSV/comprimidos ni filas exportadas. Revisar SQL por INSERT/REPLACE/LOAD DATA, CREATE USER, ALTER USER, GRANT y contraseñas/hashes. Coincidencias en cuerpos de programas no equivalen automáticamente a datos, pero exigen revisión humana.
- Revisar también DEFAULTs, comentarios, vistas y programas por literales sensibles. ddlOnly no sanea secretos incrustados en DDL.
- Evitar publicar el dump crudo; metadatos pueden revelar nombres de cuentas DEFINER u otros datos internos. Preparar una copia revisada para QA sin modificar el origen y documentar cualquier diferencia funcional.
- Inventariar claves/índices FULLTEXT, collations y características no soportadas por mysql:8.0.36. Si desarrollo usa una versión posterior incompatible, elegir un destino compatible o detenerse; no forzar la importación ignorando versión.

## 6. Importación futura en Docker — no ejecutada

Precondiciones: autorización independiente para crear el contenedor exclusivo MySQL, volumen nuevo y publicación 127.0.0.1:13316; credenciales QA nuevas fuera del repositorio; inventario Docker/puerto reconfirmado; esquema revisado y base dima_bug_qa_integracion ausente. Nunca restaurar a desarrollo ni a un esquema ya existente.

Desde el PC, conectarse al MySQL Docker por su puerto loopback con Shell; no hace falta montar desarrollo ni copiar archivos al contenedor:

```powershell
& 'C:\Program Files\MySQL\MySQL Shell 26.7\bin\mysqlsh.exe' --mysql --js `
  --host=127.0.0.1 --port=13316 --user='<CUENTA_QA_IMPORTACION>' --password
```

En esa sesión QA, con nombre de base y origen de conexión ya confirmados mediante consultas autorizadas:

```javascript
util.loadDump('C:/DimaBug-QA/estructura-dev', {
  schema: 'dima_bug_qa_integracion',
  loadDdl: true,
  loadData: false,
  loadUsers: false,
  dryRun: true
});
```

Revisar el dry run antes de aprobar una segunda ejecución con dryRun:false. No establecer ignoreExistingObjects ni ignoreVersion. El dry run se conecta y realiza consultas aunque no importe; tampoco se ejecutó en esta etapa. El remapeo schema soporta un dump de un solo esquema; las referencias al origen en vistas/programas no se corrigen automáticamente. Resolverlas mediante revisión del artefacto antes de importar. La creación del esquema también es escritura y requiere autorización; no asumir que el loader puede usar uno existente ni que sus permisos alcanzan. Fuente: [carga de dumps MySQL Shell](https://dev.mysql.com/doc/mysql-shell/8.0/en/mysql-shell-utilities-load-dump.html).

Para la alternativa SQL de mysqldump, crear previamente la base QA vacía con charset/collation revisados, conectar Shell en modo SQL a 127.0.0.1:13316 y seleccionar exclusivamente dima_bug_qa_integracion. Ejecutar posteriormente el archivo revisado mediante `\source C:/DimaBug-QA/estructura-dev.sql`. No usar ese comando con el directorio de dumpSchemas: sus archivos requieren util.loadDump.

Después de importar: revisar tablas y restricciones → aplicar solo migraciones pendientes con autorización → añadir datos sintéticos y fila técnica mutex cuando corresponda → iniciar Backend con validate y SQL init never → comprobar búsquedas JDBC, concurrencia y regresión. La exportación vacía no incluye roles de aplicación ni cuentas de prueba: requieren seeds nuevos revisados, no copias de usuarios reales. Un fallo DDL parcial exige inventario del destino y diagnóstico; no borrar recursos automáticamente.

## 7. Decisiones pendientes y autorización

Falta confirmar endpoint/esquema/versión de desarrollo, cuenta existente autorizada, presencia de objetos almacenados, compatibilidad de clientes y fuente del DDL. En esta etapa solo se leyeron archivos y documentación y se consultó ayuda local sin conexión; la única escritura del proyecto es este informe. Se preservan QA009 a QA014.

**Solicitar autorización para la siguiente etapa:** consultar metadatos del esquema de desarrollo confirmado y exportar exclusivamente su estructura a una carpeta privada. La creación de MySQL Docker, la importación y las migraciones siguen requiriendo autorización separada. STAGING/PRODUCCIÓN permanecen fuera del alcance.
