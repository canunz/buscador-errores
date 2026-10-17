# QA-015 — Auditoría de integración previa al despliegue

Fecha: 2026-10-14, America/Santiago. Análisis exclusivamente local. Se revisaron los seis informes de implementación, código de contratos/configuración, SQL preparado, evidencias previas y artefactos existentes. Solo se creó este documento: **no se modificó código, no se ejecutó SQL, migraciones, pruebas nuevas, builds, commits o despliegue**. STAGING/PRODUCCIÓN no se inspeccionaron ni modificaron.

**Dictamen preliminar: GO condicionado para preparar validación en MySQL aislado; NO-GO para despliegue STAGING hoy.** Las pruebas locales previas son satisfactorias, pero no demuestran esquema, persistencia, locks o restauración reales. La validación MySQL y cualquier ejecución posterior requieren autorización independiente.

## 1. Inventario consolidado

Git: rama `catalina`, 223 entradas locales preexistentes. Un único repositorio padre contiene Backend/Angular; no son dos ramas independientes. El diff incluye cambios anteriores a QA-009 además de QA-009 a QA-014. Sin una referencia verificada del artefacto desplegado no puede atribuirse todo el diff a estos seis QA ni certificar la diferencia exacta frente a STAGING.

| Bloque | Backend | Angular | Dependencias |
|---|---|---|---|
| QA-009 Soluciones | Modelo/DTO/JPA con título, estado, autoservicio; administración; RN-08/15/16/17; coordinación de asignaciones/resultados/publicación; índice | Título/estado/autoservicio, desactivar/reactivar, consulta real y compatibilidad visual de título nulo | SQL co_solucion; frontend debe enviar título; preservación de historia y locks reales. |
| QA-010 Sistemas/Hardware | PUT conserva sistemaIds omitido/null; [] desvincula; validaciones existentes | Menú Sistemas, acceso Hardware, asociación múltiple e inactivo original | AdminCatalogo y seguridad preexistentes; sin SQL nuevo. |
| QA-011 Módulos | Producción sin cambio en este QA; pruebas de reglas existentes | Menú/filtros/combinación/contador/Sistema inactivo original | Sistema/Hardware, módulos y referencias a conocimientos; sin SQL nuevo. |
| QA-012 Departamentos/Contactos | CRUD administrativo, @Version, bloqueos, fila de control, reindexación postcommit | HTTP, estados/padre, respaldo manual legacy | SQL de versiones y u_organizacion_control; Responsables CRUD y validación de asignación pendientes. |
| QA-013 Pruebas/Pasos | CRUD Pruebas y estado; asociación histórica con activa; lock Prueba; reindexación | Catálogo HTTP, respaldo legacy, éxito tras HTTP, Paso posición/orden y errores | Campos existentes co_prueba; no SQL nuevo; mantiene BORRADOR. |
| QA-014 Nuevo Procedimiento | Sin cambios Backend | Validaciones/carga segura, guard/beforeunload, recuperación parcial, GET forzado y advertencia publicación vacía | Contratos Procedimiento/Paso actuales; no SQL nuevo; Backend consolidado aún depende QA-009/12. |

Los manifiestos por QA se revisaron en QA009_IMPLEMENTACION.md, QA010_SISTEMAS_HARDWARE.md, QA011_IMPLEMENTACION_MODULOS.md, QA012_IMPLEMENTACION_DEPARTAMENTOS.md, QA013_IMPLEMENTACION_PRUEBAS_PASOS.md y QA014_IMPLEMENTACION_NUEVO_PROCEDIMIENTO.md. El anexo de este informe inventaría los archivos pendientes reales de Git por capa, incluidos cambios previos que también entrarían en el build.

Familias de producción afectadas:

- Backend conocimiento: Solucion/MantenerSolucion/AdministrarSolucion; MantenerConocimiento/RN-08; GestionarAsignacionSolucion; GestionarPruebasConocimiento/AdministrarPruebas; puertos, adaptadores, entidades, mappers, repositorios y DTO correspondientes; BusquedaConocimientoJdbcAdapter y reindexación de Pruebas.
- Backend catalogo: AdministrarCatalogosService para Hardware; entidades/repositorios/administración de catálogos preexistentes deben formar parte del artefacto, aunque QA-011 solo cambió su interfaz/pruebas.
- Backend identidad: AdministraciónOrganizacion, modelos administrativos, entidades de Departamento/Contacto/control, puertos/adaptadores, controladores/DTO, eventos y reindexación.
- Backend transversal: GlobalExceptionHandler; configuración segura application.properties; seguridad/visibilidad y material/usuarios/procedimientos preexistentes del conjunto local, sujetos a comparación con versión desplegada.
- Angular: modelos conocimiento/catalogo/organizacion-admin; clientes Conocimiento, Clasificacion, Organizacion, AdminCatalogos/AdminOrganizacion/AdminPruebas y Procedimiento; componentes Soluciones/Conocimiento/AdminCatalogo/Hardware/Departamentos/Pruebas/Procedimiento; Shell/menu, rutas y guard de salida; servicios de respaldo local. Otros cambios previos visibles en el anexo no deben excluirse de la revisión por no tener etiqueta QA-009.

### Artefactos y configuración comprobados

Raíz local: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/`.

- Backend `dima-bug-backend/target/backend-0.0.1-SNAPSHOT.jar`: SHA256 `ABC2F5CA513F5791F2D51B3523F785EFA3973733E619CAFB9A75241DAD57E8AC`. Generado durante QA-013; QA-014 no cambió fuentes Backend. No reconstruido en esta auditoría.
- Angular `frontend-dimabug/dist/buscador-errores/browser/index.html`: SHA256 `56087E60FE0EDA195EB1A484C7D2E138E7FF816D40D948C3D62448722FBB0C14`. Build QA-014; el hash del index **no es un manifiesto de toda la distribución**, debe generarse uno del paquete completo en la futura preparación de release.
- Los ZIP `dimabug-frontend.zip` y `dimabug-staging-frontend-20261013.zip` son anteriores a QA-014; no utilizarlos como entrega consolidada.
- Maven: Java21, Spring Boot4.1.1 y spring-boot-maven-plugin. El JAR ejecutable/configuración empaquetada fueron verificados en QA-013; repetir comprobación del artefacto de release antes de transferir.
- Angular19: producción usa environment.prod.ts con apiUrl vacío, requests `/api` al mismo origen; requiere proxy del servidor y fallback SPA. Puerto 9090 del proxy local no prueba puerto STAGING.
- Budgets actuales: initial warning1.5mb/error2mb; CSS16kB/32kB. **optimization=false/sourceMap=true están únicamente en development**. Producción hereda optimization=true/sourceMap=false del schema del builder instalado; no se encontraron .map en dist/browser. No hay hallazgo de optimizaciones desactivadas en producción.
- `application.properties` contiene únicamente placeholders DB_URL/DB_USERNAME/DB_PASSWORD para DataSource, sin credenciales. `/etc/dimabug/staging.env` es una ruta informada por el usuario, no inspeccionada; Spring no consume ese archivo arbitrario si el mecanismo de ejecución no lo incorpora.
- Confirmar fuentes efectivas de SPRING_PROFILES_ACTIVE, jwt.secret, jwt.expiration, puerto y `dimabug.storage.materiales-dir`. El default de uploads es **relativo** `./uploads/materiales`: resolverlo desde WorkingDirectory real, sin moverlo al reemplazar el JAR.
- Confirmar ddl-auto, SQL init y cualquier automatismo externo antes de iniciar contra MySQL. No permitir que update/create sustituya migraciones revisadas. No se añadió Flyway/Liquibase/runner.
- La inspección de nombres versionados no encontró .env/.pem/.key; esto no es un escaneo exhaustivo de secretos ni prueba de configuración segura del servidor. Antes de release revisar el manifiesto completo sin incorporar env, logs, dumps o secretos.

## 2. Migraciones pendientes y dependencias

Prefijo de archivos: `dima-bug-backend/src/main/resources/db/migration/`.

| Orden de revisión/aplicación futura | Archivo / objeto | Efecto y compatibilidad |
|---|---|---|
| 0: comprobar si ya está aplicado | V20260925_01__agregar_estado_eliminado_conocimiento.sql | Amplía enum co_conocimiento a BORRADOR/PUBLICADO/ELIMINADO. No asumir pendiente: inspeccionar definición real y valores antes. El script MODIFY puede afectar datos/definición según esquema actual; no repetir a ciegas. |
| 1 | V20261013_01__ampliar_solucion.sql | co_solucion: titulo VARCHAR150 NULL; estado TINYINT(1) NOT NULL DEFAULT1; autoservicio TINYINT(1) NOT NULL DEFAULT0. Existing rows quedan activos/autoservicio deshabilitado/título nulo. JPA boolean/String compatibles a nivel previsto. |
| 2 | V20261014_01__organizacion_persistente.sql | u_departamento.departamento_version y u_departamento_contacto.contacto_version BIGINT NOT NULL DEFAULT0; crea u_organizacion_control PK control_id y fila1, InnoDB. Necesarios para @Version y bloqueo global. |
| 3 | Backend consolidado → Angular consolidado | Ambos SQL nuevos deben estar completos antes del JAR. QA-010/11/13/14 no agregan SQL. |

QA-009 y QA-012 no tienen dependencia SQL directa entre sus tablas; se propone orden cronológico para trazabilidad. La aplicación consolidada requiere ambos. Si hay columnas ya existentes, verificar tipo/null/default/datos y registro de ejecución, no interpretar existencia como compatibilidad completa ni ejecutar ADD otra vez.

### Riesgos específicos

- **No son scripts idempotentes.** QA-009 tiene un ALTER con tres columnas; QA-012 tiene varios statements: una falla después de la primera columna puede dejar ejecución parcial. MySQL atomic DDL no vuelve transaccional todo el archivo ni permite rollback global de su secuencia. [Manual MySQL: atomic DDL](https://dev.mysql.com/doc/refman/8.0/en/atomic-ddl.html).
- INSTANT debe comprobarse para versión exacta, motor, row format, FULLTEXT, límites de fila/columnas y operaciones anteriores. Los scripts agregan al final, pero “MySQL8” por sí solo no garantiza soporte. No reemplazar automáticamente por COPY/INPLACE. [Manual MySQL: operaciones online DDL](https://dev.mysql.com/doc/refman/8.0/en/innodb-online-ddl-operations.html).
- INSTANT no elimina la necesidad de metadata locks: transacciones largas pueden demorar/bloquear la operación. No hay ETA fiable sin medir en clon representativo. [Manual MySQL: locks de DDL](https://dev.mysql.com/doc/refman/8.0/en/innodb-online-ddl-performance.html).
- QA-012 limita lock_wait_timeout de la sesión a5s y lo restaura al final. Si el cliente aborta antes del final, restaurar/cerrar la sesión según procedimiento revisado. **QA-009 no impone límite de espera propio**: definir control de espera en la sesión de ejecución futura y criterios de abortar antes de autorizarlo; no se modificó el archivo.
- No se añaden índices UNIQUE sobre tablas existentes ni FK nuevas en QA-009/12. El único índice nuevo es PK de una tabla nueva vacía. Los duplicados existentes no bloquean por sí solos estos ADD, pero sí las reglas de aplicación o un índice futuro; no se depuran automáticamente.
- Confirmar UNIQUE/collation actuales de co_prueba, cl_modulo y nombres de catálogos. Un UNIQUE global en módulo puede rechazar nombres iguales entre sistemas, contrario a la regla de aplicación. Departamento unicidad trim/case se protege con servicio/mutex, sin UNIQUE físico nuevo; escritores externos pueden saltar el protocolo.
- Confirmar FK físicas y huérfanos en solución/conocimiento, hardware–sistema, módulo/sistema, contacto/departamento, responsable/departamento, asignaciones, Paso/Procedimiento y ejecución/Paso. No habilitar cascadas, desactivar foreign_key_checks ni reparar filas automáticamente.
- Datos anteriores: localizar PUBLICADOS sin solución activa, asociaciones históricas, títulos nulos, duplicados de nombres/pares/orden, valores enum desconocidos y booleanos fuera de0/1. RN-08 no corrige registros anteriores por sí sola. Versiones iniciales0 no deben sobrescribir versiones ya existentes.

La documentación QA_MVP_ESTABILIZACION.md contiene una sección antigua que dice “No se requiere migración”; **no aplica al conjunto QA-009 a QA-014**. También es insuficiente su rollback genérico anterior. Se mantiene el documento sin cambios; esta auditoría identifica esa incompatibilidad.

## 3. Riesgos por severidad

| ID / severidad | Riesgo | Tratamiento antes de abrir STAGING |
|---|---|---|
| B1 — Crítico/bloqueante | JAR nuevo contra esquema sin columnas/versiones/fila1 | Verificar esquema y completar migraciones autorizadas antes del arranque; puede fallar incluso en lecturas. |
| B2 — Crítico/bloqueante | Sin restauración demostrada y uploads/localización no confirmados | Backup consistente y prueba de restauración aislada; preservar materiales/configuración, no solo JAR. |
| B3 — Alto/bloqueante | Unidades/rutas/puerto/proxy/DB real no comprobados; riesgo de tocar producción compartida | Discovery de solo lectura por operador; no parar MySQL o servicios compartidos de producción. |
| B4 — Alto/bloqueante | Persistencia/concurrencia MySQL nunca probadas | Ejecutar plan sección6 sobre clon aislado con permiso explícito. |
| B5 — Alto/bloqueante | Frontend viejo no envía título; frontend nuevo requiere endpoints nuevos | Ventana coordinada, Backend primero y Angular antes de reabrir escrituras; recarga de clientes. |
| B6 — Alto/bloqueante | Rollback viejo ignora estados/privilegios administrativos persistidos | Probar compatibilidad semántica o preparar hotfix/versión de retorno que mantenga esas protecciones. |
| R1 — Alto, aplazado explícitamente | responsableId activo puede usarse aunque su Departamento esté inactivo; carrera desactivación/asignación pendiente | El usuario decidió dejar ese ajuste pendiente en QA-012. Probar/registrar alcance y criterio de exposición; no marcarlo resuelto ni cambiar código en esta auditoría. |
| R2 — Alto | Espera DDL, aplicación parcial y auto-DDL externo | Controlar session wait, esquema por statement y configuración efectiva; abortar ante método incompatible. |
| R3 — Alto | UNIQUE/collation/FK reales distintos de JPA; datos incompatibles | Preflight de datos/DDL; no introducir UNIQUE o limpieza sin análisis/aprobación aparte. |
| R4 — Medio/alto | Reindexación postcommit QA-012/13 puede fallar sin retry durable | Verificar índice, logs y recuperación; no confundir HTTP200 CRUD con índice actualizado. |
| R5 — Medio | Edición obsoleta/asociaciones completas sobrescritas; QA-014 sin idempotencia Backend | Pruebas multiusuario/respuesta perdida; conservar verificación HTTP y no repetir altas ciegamente. |
| R6 — Medio | Borrador de Procedimiento publicable vacío, luego no ejecutable/inmutable | Comportamiento aprobado, no defecto a cambiar: comprobar aviso y rechazo de ejecución. |
| R7 — Medio | Cachés/flags antiguos, formularios abiertos, pestañas con frontend anterior | Respaldos locales manuales, no importar flags, invalidación/reload de clientes y UAT entre sesiones. |
| R8 — Bajo | CSS Inicio/Conocimiento supera warning; avisos de harness | No bloquean compilación actual; documentar UAT visual. No ampliar budgets para ocultar fallos. |

## 4. Compatibilidad Backend / Angular / MySQL

| Contrato | Verificación local | Incompatibilidad de versiones |
|---|---|---|
| Solución operacional POST/PUT | titulo obligatorio ≤150, descripción; rechaza campos admin; PUT preserva activo/autoservicio | Frontend anterior sin titulo:400. Backend anterior puede ignorar estado/autoservicio. |
| Admin Soluciones | GET `/api/admin/conocimientos/{c}/soluciones`; PATCH `/{s}/estado` con activo; PATCH `/{s}/autoservicio` | Angular nuevo contra Backend anterior: rutas ausentes. Solo ADMINISTRADOR. |
| Catálogos existentes | `/api/admin/catalogos/{hardware\|sistemas\|modulos\|frecuencias}`, GET/POST/PUT/PATCHestado, activo | Reutilizados, no nuevos por QA-010/11. sistemaIds omitido/null conserva; [] desvincula. |
| Departamentos | `/api/admin/departamentos`, GETlista/id, POSTnombre, PUTnombre+version, PATCHactivo+version | API nueva; versión requerida en edición/estado; columnas necesarias. |
| Contactos | Rutas anidadas `/api/admin/departamentos/{d}/contactos`, GET/POST/PUT/PATCH | POST tipo/valor/activo; PUT solo tipo/valor/version; estado activo/version. No mover padre por body. Disponible=estado propio y padre. |
| Pruebas | `/api/admin/pruebas`, GET/POST/PUT/PATCHestado | Usa **activa** en JSON; Angular convierte a activo en vista. Solo admin escribe. GET operativo `/api/pruebas` solo activas. |
| Asociación Pruebas | GET `/api/conocimientos/{c}/pruebas` incluye **activa** aditiva y referencias inactivas; POST/PUT existentes | Cliente anterior puede ignorar activa y presentar inactiva como recomendada. Exige actualización de cliente. |
| Procedimiento/Paso | Endpoints/DTO Backend anteriores conservados; orden entero, BORRADOR, publicación admin | QA-014 modifica cliente/guard/status, no API. 400 estado incompatible;409 orden/integridad, sin falso éxito. |
| Auth/visibilidad | Matcher `/api/admin/**`, guards actuales; GET hijos filtra contenido según rol/estado | Validar config JWT y controles reales del artefacto anterior; no inferir equivalencia por nombres de roles. |

A nivel fuente/DTO no se encontró un desajuste nuevo de nombres de campos entre pantallas implementadas y controladores locales. **No constituye verificación HTTP de un servidor ni equivalencia de schema MySQL.** Los servicios siguen el patrón Controller→Application Service→Port→Adapter; no se agregaron frameworks de migración ni dependencias nuevas en estos QA.

## 5. Matriz de regresión propuesta

Ejecutar en MySQL aislado autorizado y luego smoke/UAT controlado en STAGING, con datos/cuentas de prueba identificados. Evidencia por caso: request/status/estado antes-después, IDs/relaciones, segunda sesión, logs sin secretos. No escribir casos de prueba sobre datos históricos reales sin planificación.

| Área | Positivo | Negativo/permisos | Historia/concurrencia |
|---|---|---|---|
| Soluciones | Alta título, edición activa, desactivar/reactivar, autoservicio admin | RN-08 última activa/publicar sin activas409; inactiva RN-16; técnico PATCH403/inyección400; título blanco/>150400 | NULL legacy visible y obligatorio al editar; asignaciones/resultados conservados; dos desactivaciones/publicación simultáneas; autoservicio retenido al reactivar. |
| Sistemas/Hardware | Menú admin, selección múltiple/precarga; PUT omitido conserva y[]retira | Inexistente/duplicado/inactivo nuevo rechazado; técnico escritura403 | Inactivo original desmarcar/remarcar; conocimientos/módulos conservados; edición concurrente del conjunto; no bloqueo de desvinculación no aprobado. |
| Módulos | Filtros combinados/limpiar; duplicado entre Sistemas distintos permitido | Duplicado mismo Sistema rechazado; traslado con conocimiento incluso ELIMINADO bloqueado; inactivo nuevo rechazado | Desactivación conserva referencias; sistema original inactivo seleccionable; FK/índice físicos compatibles. |
| Departamentos/Contactos | CRUD/versiones, estados individuales; admin lee inactivos | Nombre trim/case duplicado409; versión obsoleta409; padre/hijo ajeno404; contacto activo bajo padre inactivo rechazado; técnico escrituras403 | Desactivar padre no toca hijos; reactivar no activa hijos inactivos; contactos operativos excluidos por padre; dos altas iguales/misma versión; asignaciones intactas. |
| Responsables integrado | Selector solo padre activo y lectura histórica | Probar responsableId solo con padre inactivo: **limitación conocida, no prueba aprobada** | Documentar carrera pendiente y aplazamiento; no crear CRUD Responsable como workaround. |
| Pruebas/Pasos | CRUD Prueba, activa/estado, descripción/resultados; Paso posición/orden | Prueba inactiva nueva asociación404; adminAPI técnico403; orden inválido400/ocupado409; PUBLICADO no editable400 | Prueba compartida inactiva identificable por ID/orden; no borrado; índice activo; respuesta perdida de Paso/verificación; índice postcommit fallido. |
| Nuevo Procedimiento | BORRADOR sin pasos, precarga, PUT, guard y recarga de confirmados | Nombre blanco/>150; ID inválido; GETfallido no habilita PUT; doble envío; publicación técnico403 | Paso intermedio falla y anteriores quedan; GET posterior no repite POST; publicar vacío avisa y permite, ejecutar rechaza; no warning tras guardar, beforeunload con cambios. |
| Autenticación | Login/renovación prevista/config efectiva, admin/técnico/soporte/usuario | Sin token401, inválido/expirado según contrato, rol403, adminGuard/rutas directas y API | Nuevos despliegues no exponen secretos; cookies/tokens de otra versión no amplían permisos; cerrar sesión/recarga con datos pendientes. |
| Conocimientos | Alta/edición, clasificación y componentes asociados, publicación RN-08 | Datos clasificados incompatibles rechazados; USUARIO no ve BORRADOR; ELIMINADO según visibilidad | IDs históricos intactos al cambiar catálogos; índice no incluye soluciones/pruebas inactivas; no alterar resultados/auditoría; edición multiusuario. |
| Uploads/material | Descargar material existente y subida de prueba con autorización | Sin permisos/no existente/tipo/tamaño según reglas actuales | Conteo/checksum/rutas/propietario antes-después; respaldar y preservar, no borrar durante release. |

Los resultados previos QA-014 fueron 645 Backend/174 Angular y producción Angular1,37MB. Se confirmaron esos resúmenes en logs locales; no se reejecutaron aquí. Predomina cobertura con mocks/HttpTestingController y concurrencia simulada, **no persistencia física InnoDB**.

## 6. Plan de validación MySQL real (no ejecutado)

1. Identificar un MySQL aislado de misma versión/configuración representativa, sin conexión a producción. Autorizar por separado restauración y pruebas de escritura. Usar copia anonimizada o datos controlados que representen históricos, NULL legacy, referencias y volumen; no usar producción como laboratorio.
2. Obtener DDL/motor/collation/índices/FK/valores reales. Confirmar base destino, privilegios, versión exacta, isolation/sql_mode, triggers y herramientas. No hay datos del servidor en esta auditoría.
3. Inventariar objetos existentes de migraciones y detectar duplicados/huérfanos/valores inválidos/PUBLICADOS sin soluciones. No corregir automáticamente ni atribuir esas filas a QA-009.
4. Probar respaldo/restauración **antes** de SQL de prueba; acordar cuánto tiempo de bloqueo es aceptable y cómo detener/reanudar sin fallback invasivo.
5. Cuando se autorice, aplicar solo los scripts pendientes compatibles en el orden sección2, registrar cada statement y medir locks/duración. Una ejecución parcial no se reinicia a ciegas; inspeccionar lo aplicado. Si INSTANT no sirve, detener y solicitar diseño/autorización del DDL alternativo.
6. Arrancar el artefacto consolidado con configuración efectiva revisada; no usar auto-DDL para crear objetos por sorpresa. Comprobar mappings/versiones/defaults y fila1.
7. Ejecutar matriz con HTTP real y varias conexiones: persistencia tras reinicio/nueva sesión, rollback, edición obsoleta, bloqueo de padre/Prueba/control, duplicados y relaciones. Medir también reindexación/reintentos después del commit.
8. Comparar conteos/IDs/FK y uploads antes/después; restaurar en otro clon y demostrar recuperación. Aprobar resultados por caso, incluyendo los aplazamientos aceptados y su impacto.

### Consultas de solo lectura para preparar el preflight

**Propuestas para operador autorizado; ninguna se ejecutó aquí.** Usar sesión ya conectada a la base STAGING verificada, sin copiar credenciales al informe.

```sql
SELECT DATABASE(), VERSION(), @@transaction_isolation, @@sql_mode;
SHOW CREATE TABLE co_solucion;
SHOW CREATE TABLE co_conocimiento;
SHOW CREATE TABLE u_departamento;
SHOW CREATE TABLE u_departamento_contacto;
SHOW CREATE TABLE u_responsable;
SHOW CREATE TABLE co_prueba;
SHOW CREATE TABLE cl_modulo;
SHOW CREATE TABLE cl_hardware_sistema;
SHOW CREATE TABLE co_solucion_asignacion;
SHOW CREATE TABLE co_conocimiento_prueba;
SHOW CREATE TABLE pr_procedimiento;
SHOW CREATE TABLE pr_paso;
SHOW CREATE TABLE se_ejecucion_paso;
SHOW INDEX FROM co_prueba;
SHOW INDEX FROM cl_modulo;
SHOW INDEX FROM u_departamento;
```

Revisar todas las FK/dependencias de esos DDL y material/ejecución; la lista no sustituye dump de esquema completo. No ejecutar SHOW de tabla nueva si no existe; buscar primero en information_schema. Consultas de duplicados se parametrizan según columnas/normalización/collation verificadas; COUNT no repara datos. No incluir dumps personales completos en el chat.

## 7. Plan STAGING futuro, diferenciado por lugar

### Información del servidor que falta

Unidad/proceso o contenedor real, JAR y raíz web efectivos, WorkingDirectory, upload path, puerto/proxy, base destino confirmada, usuarios/permisos, fuentes config JWT/DB, backup/PITR, espacio libre, monitorización y alcance compartido con PRODUCCIÓN. No inventar unidad `dimabug-staging` o rutas `/opt/...` como hechos.

Comandos **solo lectura**, para el operador, no ejecutados por el agente:

```bash
hostname
systemctl list-unit-files --type=service --no-pager | grep -Ei 'dima|java|spring'
ss -ltn
df -h
sudo stat /etc/dimabug/staging.env
sudo awk -F= '/^[A-Za-z_][A-Za-z0-9_]*=/{print $1}' /etc/dimabug/staging.env
```

Si está gestionado por systemd, sustituir la unidad por la descubierta:

```bash
systemctl show '<UNIDAD_STAGING_VERIFICADA>' -p FragmentPath -p WorkingDirectory -p User -p Group -p EnvironmentFiles -p ActiveState
```

El operador debe revisar ExecStart/config del servicio **localmente** para identificar JAR/puerto/overrides; no pegar argumentos con secretos. Para proxy y contenedores, inspeccionar en el servidor nginx/Apache o Docker existentes; compartir solo dominio STAGING, root, alias, proxy/fallback y puertos sanitizados. No volcar env, `docker inspect` o configuración completa al chat.

### Secuencia futura tras GO y autorización

| Paso | En PC local | En servidor STAGING / operador |
|---|---|---|
| 1 — Congelar entrega | Registrar manifiesto de fuentes revisado, versiones/hashes. Repetir tests/build/package sobre estado acordado; no usar ZIP viejo ni asumir GitHEAD representa release. | Confirmar identidad/destino y servicio dedicado; impedir cualquier alcance a producción. |
| 2 — Preparar respaldo | Conservar scripts exactos y reporte aprobado, paquete frontend completo y JAR. Secretos fuera de paquetes. | En ventana con pausa de escrituras STAGING, respaldo MySQL mediante procedimiento DBA existente, configuraciones, artefactos anteriores y uploads reales. Validar restauración/checksum/propietario; un dump no probado no basta. |
| 3 — Validar esquema | Revisar resultados del clon y compatibilidad de rollback. | Preflight final de base/objetos/datos/espacio/locks; confirmar ninguna migración parcial. No detener MySQL compartido ni borrar datos. |
| 4 — SQL autorizado | Ninguna ejecución remota automática. | Aplicar pendientes aprobados con control de espera/errores; validar columnas/defaults/versiones/fila1/conteos y FK. Si falla, detener apertura y evaluar estado, no continuar con JAR. |
| 5 — Backend | Transferir JAR verificado de la entrega. | Reemplazar únicamente artefacto STAGING según mecanismo real, conservando env/WorkingDirectory/upload dir y permisos. Reiniciar solo servicio dedicado STAGING; revisar arranque/health/logs. |
| 6 — Angular | Transferir **todo** dist/buscador-errores/browser, no solo index. | Publicar como misma entrega antes de reabrir escrituras. Conservar proxy/fallback existente; no tocar root/servicios de producción. Evitar mezcla de index/chunks y conservar release anterior. |
| 7 — Smoke | Ejecutar clientes con build nuevo/recarga, cuentas autorizadas. | health, DB/config, login, 401/403, GET admin/operativo, lecturas históricas y descarga de upload previo. Escrituras de smoke solo registros de prueba acordados. |
| 8 — Abrir UAT | Matriz/acta de aceptación, errores y hashes de entrega. | Reabrir STAGING únicamente si humo y preservación pasan. Vigilar conflictos, locks, índice, navegación y uploads; registrar ventana y criterio rollback. |

Los futuros comandos de construcción serían `mvnw.cmd verify` en Backend con Java21 y `npm test -- --watch=false --browsers=ChromeHeadless` / `npm run build -- --configuration production` en Angular. No se ejecutaron en QA-015. Las mutaciones del servidor quedan sin comando literal hasta conocer rutas/unidad/config reales.

## 8. Rollback

**No equivale a “copiar JAR viejo” ni a ejecutar DROP.** Mantener las columnas/tablas aditivas evita borrar historia, pero no asegura compatibilidad funcional con software anterior.

- Antes de migrar/desplegar: retener release/config anterior y respaldo restaurable de MySQL+uploads en punto consistente; registrar binlogs/PITR si el procedimiento existente los soporta. Ensayar recuperación en clon.
- Si SQL queda parcial antes del nuevo JAR: mantener STAGING sin escrituras, inspeccionar objetos y restaurar estado operativo con procedimiento aprobado; no intentar ROLLBACK SQL como si todo el archivo fuera una transacción ni repetir ADD indiscriminadamente.
- Si falla arranque antes de usar funcionalidades nuevas: rollback conjunto Backend/Angular puede ser viable tras comprobar mappings/config de la versión anterior contra esquema expandido. Validar siempre en clon; no asumir que validate, índices o enum aceptan cambios.
- **Después de desactivar Soluciones/Pruebas o administrar organización:** Backend anterior puede ignorar estados y controles de campos o mostrar contenido inactivo como operativo; frontend anterior puede recuperar flags locales. No reabrir operaciones con esa pareja sin ensayo y evaluación semántica. Preferir corrección hacia adelante o release de retorno que conserve protecciones; no modificar estados masivamente para “hacer compatible” la reversión.
- Restituir artefactos por el mecanismo verificado, manteniendo env, uploads y dueño/permisos. Nunca reemplazar toda la carpeta de trabajo que contenga materiales por un paquete sin uploads.
- Restaurar una copia de base puede perder operaciones desde el backup: requiere autorización específica, conciliación y punto de recuperación; no es rollback automático. No restaurar producción ni un dump de producción sobre STAGING por conveniencia.
- Criterios de activar contingencia: DataSource/mappings incompatibles, falla auth/roles, pérdida de vínculos/material, RN-08 incumplida bajo concurrencia, errores persistentes de escritura, locks fuera de ventana o release frontend mezclado. Retirar escrituras mientras se investiga; no abrir software con fallas de integridad conocidas.

Smoke de retorno: login/roles, lecturas operacionales/admin según compatibilidad, estados/inactivos, histórico, uploads existentes y datos desde otra sesión. El rollback se declara terminado solo con evidencia, no por process running.

## 9. Bloqueantes y puertas de salida

Para GO de STAGING deben cumplirse todos:

1. Inventario del servidor/DB dedicado y comparación con versión efectivamente desplegada.
2. DDL/motores/columnas/índices/FK/datos y soporte INSTANT confirmados; ninguna discrepancia sin resolver o aceptación documentada compatible.
3. Respaldo y restauración MySQL+uploads probados, rutas reales y mecanismo de reinicio exclusivo STAGING.
4. Matriz en MySQL aislado aprobada, incluyendo persistencia, locks/versiones, historial y efectos del riesgo Responsable aplazado.
5. Manifiesto exacto de release, credenciales fuera, contratos coordinados y clientes anteriores retirados de escritura.
6. Plan de rollback probado compatible con estados nuevos; no basta que columnas extra permitan arrancar.
7. Autorización separada para ejecutar SQL y desplegar, con responsables/ventana/criterios de detener.

Falta evidencia para esas puertas. Compilación/tests locales, mocks y timestamps de artefactos no las sustituyen. Los defectos aceptados/aplazados no se presentan como corregidos; decidir su tratamiento de release respetando decisiones previas del usuario.

## 10. Recomendación preliminar

**GO condicionado para preparar validación MySQL aislada**: código/contratos locales y tests previos permiten iniciar esa fase una vez se autoricen entorno, copia y operaciones. No hace falta rediseñar Backend ni agregar migraciones nuevas a ciegas.

**NO-GO para desplegar el conjunto en STAGING ahora**: esquema y locks reales, restauración/rollback semántico y datos operativos de servidor pendientes. Tras cumplir sección9, emitir un GO específico del paquete validado, sin confundirlo con autorización de producción.

QA-015 se detiene después del informe. No inició validación con DB ni despliegue.

## Anexo — Manifiesto de archivos pendientes por Git

Inventario de fuentes/configuración/tests pendientes, no una atribución automática a QA-009 a QA-014. Incluye trabajos anteriores presentes en el build local; contrastar con el release realmente desplegado antes de preparar el paquete. M=modificado, D=eliminado, ??=nuevo no versionado. Las rutas son relativas a C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/. No incluye dist, ZIP, logs ni credenciales.

### Backend

| Estado | Archivo |
|---|---|
| M | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/FrecuenciaEntity.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/ModuloEntity.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/port/out/ConocimientoPruebaRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/port/out/ConocimientoRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/port/out/PruebaRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/port/out/SolucionRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/GestionarAsignacionSolucionService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/GestionarPruebasConocimientoService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/MantenerConocimientoService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/MantenerSolucionService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/domain/Solucion.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/ConocimientoPruebaRepositoryAdapter.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/ConocimientoRepositoryAdapter.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/PruebaRepositoryAdapter.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/SolucionRepositoryAdapter.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/PruebaEntity.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/SolucionEntity.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/mapper/SolucionMapper.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/repository/ConocimientoJpaRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/repository/ConocimientoPruebaJpaRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/repository/SolucionJpaRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SolucionController.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/dto/ConocimientoPruebaResponse.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/dto/GuardarSolucionRequest.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/dto/SolucionResponse.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/port/out/UsuarioRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/AdministrarUsuariosService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/adapter/UsuarioRepositoryAdapter.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/DepartamentoContactoEntity.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/DepartamentoEntity.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/repository/UsuarioJpaRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/application/port/out/ProcedimientoRepository.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/application/service/MantenerMaterialPasoService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/application/service/MantenerPasoService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/application/service/MantenerProcedimientoService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/application/service/MaterialPasoArchivoService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/persistence/adapter/ProcedimientoRepositoryAdapter.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/ProcedimientoController.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/seguimiento/application/service/RegistrarEfectividadSolucionService.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/shared/infrastructure/security/SecurityConfig.java |
| M | dima-bug-backend/src/main/java/cl/casol/backend/shared/infrastructure/web/GlobalExceptionHandler.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/CatalogoSecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/CatalogosOrganizacionSecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/CausaSecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/EliminarMaterialApoyoTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/Hu01SecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/Hu02SecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/Hu03SecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/Hu04SecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/Hu05SecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/Hu06SecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/Hu07SecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/MaterialApoyoSecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/MaterialArchivoSecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/PruebaSecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/SintomaSecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/SolucionSecurityIntegrationTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/GestionarAsignacionSolucionServiceTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/GestionarPruebasConocimientoServiceTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/MantenerConocimientoServiceTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/MantenerSolucionServiceTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/identidad/application/service/AdministrarUsuariosServiceTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/procedimiento/application/service/MantenerMaterialPasoServiceTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/procedimiento/application/service/MantenerPasoServiceTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/procedimiento/application/service/MantenerProcedimientoServiceTest.java |
| M | dima-bug-backend/src/test/java/cl/casol/backend/seguimiento/application/service/RegistrarEfectividadSolucionServiceTest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/application/port/out/CatalogoAdministrativoRepository.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/application/service/AdministrarCatalogosService.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/domain/CatalogoAdministrativo.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/domain/TipoCatalogo.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/domain/exception/CatalogoConflictoException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/domain/exception/CatalogoInvalidoException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/adapter/CatalogoAdministrativoRepositoryAdapter.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/AdminCatalogoController.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/dto/CambiarEstadoCatalogoRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/dto/CatalogoAdministrativoResponse.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/dto/GuardarCatalogoRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/port/out/PruebaAdministrativaRepository.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/AdministrarPruebasService.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/AdministrarSolucionService.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/PruebaActualizada.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/ReindexarPruebaListener.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/ReindexarPruebaService.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/domain/exception/PruebaConflictoException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/domain/exception/PruebaInvalidaException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/domain/exception/SolucionInactivaException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/domain/exception/TituloSolucionInvalidoException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/domain/exception/UltimaSolucionActivaException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/PruebaAdministrativaRepositoryAdapter.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminPruebaController.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminSolucionController.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/dto/CambiarAutoservicioSolucionRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/dto/CambiarEstadoSolucionRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/dto/EstadoPruebaRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/dto/GuardarPruebaAdministrativaRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/port/out/AdministracionOrganizacionRepository.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/port/out/ReferenciasOrganizacionRepository.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/AdministrarOrganizacionService.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/OrganizacionActualizada.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/ReindexarOrganizacionListener.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/ReindexarOrganizacionService.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/domain/ContactoDepartamentoAdministrativo.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/domain/DepartamentoAdministrativo.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/domain/exception/ContactoDepartamentoNoEncontradoException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/domain/exception/OrganizacionConflictoException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/domain/exception/OrganizacionInvalidaException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/domain/exception/UltimoAdministradorException.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/adapter/AdministracionOrganizacionRepositoryAdapter.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/adapter/ReferenciasOrganizacionJdbcAdapter.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/OrganizacionControlEntity.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoContactoController.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoController.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/dto/ContactoDepartamentoAdminResponse.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/dto/CrearContactoDepartamentoRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/dto/CrearDepartamentoRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/dto/EditarContactoDepartamentoRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/dto/EditarDepartamentoRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/dto/EstadoOrganizacionRequest.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/application/service/EdicionProcedimiento.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/shared/application/service/VisibilidadContenidoService.java |
| ?? | dima-bug-backend/src/main/java/cl/casol/backend/shared/infrastructure/web/VisibilidadContenidoConfig.java |
| ?? | dima-bug-backend/src/main/resources/application.properties |
| ?? | dima-bug-backend/src/main/resources/db/migration/V20261013_01__ampliar_solucion.sql |
| ?? | dima-bug-backend/src/main/resources/db/migration/V20261014_01__organizacion_persistente.sql |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/AdminCatalogoSecurityIntegrationTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/AdminOrganizacionSecurityIntegrationTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/AdminPruebasSecurityIntegrationTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/ContenidoRestringidoSecurityIntegrationTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/catalogo/application/service/AdministrarCatalogosServiceTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/catalogo/application/service/ModulosQa011ServiceTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/catalogo/infrastructure/persistence/adapter/ModuloReferenciasQa011Test.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/AdministrarPruebasServiceTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/AdministrarSolucionServiceTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/SolucionConcurrenciaQa009Test.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaSolucionQa009Test.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/PruebasPersistenciaQa013Test.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/SolucionPersistenciaQa009Test.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/identidad/application/service/AdministrarOrganizacionServiceTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/identidad/application/service/OrganizacionConcurrenciaTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/identidad/application/service/OrganizacionOperacionalQa012Test.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/identidad/application/service/ReindexarOrganizacionTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/identidad/infrastructure/persistence/adapter/AdministracionOrganizacionRepositoryAdapterTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/shared/application/service/VisibilidadContenidoServiceTest.java |
| ?? | dima-bug-backend/src/test/java/cl/casol/backend/shared/infrastructure/config/DataSourceEnvironmentTest.java |

### Angular

| Estado | Archivo |
|---|---|
| M | frontend-dimabug/angular.json |
| M | frontend-dimabug/src/app/app.routes.ts |
| M | frontend-dimabug/src/app/core/models/catalogo.model.ts |
| M | frontend-dimabug/src/app/core/models/conocimiento.model.ts |
| M | frontend-dimabug/src/app/core/models/usuario.model.ts |
| D | frontend-dimabug/src/app/core/services/admin-maestros.service.ts |
| M | frontend-dimabug/src/app/core/services/catalogo.service.ts |
| M | frontend-dimabug/src/app/core/services/clasificacion.service.ts |
| M | frontend-dimabug/src/app/core/services/conocimiento.service.ts |
| M | frontend-dimabug/src/app/core/services/organizacion.service.ts |
| M | frontend-dimabug/src/app/core/services/procedimiento.service.ts |
| M | frontend-dimabug/src/app/core/services/usuario.service.ts |
| M | frontend-dimabug/src/app/features/admin/admin-catalogo.component.css |
| M | frontend-dimabug/src/app/features/admin/admin-catalogo.component.html |
| M | frontend-dimabug/src/app/features/admin/admin-catalogo.component.ts |
| M | frontend-dimabug/src/app/features/admin/admin.component.html |
| M | frontend-dimabug/src/app/features/admin/admin.component.ts |
| M | frontend-dimabug/src/app/features/conocimiento/conocimiento-detalle.component.html |
| M | frontend-dimabug/src/app/features/conocimiento/conocimiento-detalle.component.ts |
| M | frontend-dimabug/src/app/features/conocimiento/conocimiento-form.component.css |
| M | frontend-dimabug/src/app/features/conocimiento/conocimiento-form.component.html |
| M | frontend-dimabug/src/app/features/conocimiento/conocimiento-form.component.ts |
| M | frontend-dimabug/src/app/features/departamentos/departamentos.component.css |
| M | frontend-dimabug/src/app/features/departamentos/departamentos.component.html |
| M | frontend-dimabug/src/app/features/departamentos/departamentos.component.ts |
| M | frontend-dimabug/src/app/features/error-nuevo/error-nuevo.component.html |
| M | frontend-dimabug/src/app/features/error-nuevo/error-nuevo.component.ts |
| M | frontend-dimabug/src/app/features/hardware/hardware.component.css |
| M | frontend-dimabug/src/app/features/hardware/hardware.component.html |
| M | frontend-dimabug/src/app/features/hardware/hardware.component.ts |
| M | frontend-dimabug/src/app/features/procedimiento/procedimiento-detalle.component.html |
| M | frontend-dimabug/src/app/features/procedimiento/procedimiento-detalle.component.ts |
| M | frontend-dimabug/src/app/features/procedimiento/procedimiento-form.component.html |
| M | frontend-dimabug/src/app/features/procedimiento/procedimiento-form.component.ts |
| M | frontend-dimabug/src/app/features/procedimiento/procedimiento-lista.component.html |
| M | frontend-dimabug/src/app/features/procedimiento/procedimiento-lista.component.ts |
| M | frontend-dimabug/src/app/features/pruebas/pruebas.component.css |
| M | frontend-dimabug/src/app/features/pruebas/pruebas.component.html |
| M | frontend-dimabug/src/app/features/pruebas/pruebas.component.ts |
| M | frontend-dimabug/src/app/features/soluciones/soluciones.component.css |
| M | frontend-dimabug/src/app/features/soluciones/soluciones.component.html |
| M | frontend-dimabug/src/app/features/soluciones/soluciones.component.ts |
| M | frontend-dimabug/src/app/features/usuarios/usuarios.component.html |
| M | frontend-dimabug/src/app/features/usuarios/usuarios.component.ts |
| M | frontend-dimabug/src/app/shared/layout/shell-layout.component.html |
| ?? | frontend-dimabug/src/app/core/guards/auth.guard.spec.ts |
| ?? | frontend-dimabug/src/app/core/models/organizacion-admin.model.ts |
| ?? | frontend-dimabug/src/app/core/services/admin-catalogos-api.service.spec.ts |
| ?? | frontend-dimabug/src/app/core/services/admin-catalogos-api.service.ts |
| ?? | frontend-dimabug/src/app/core/services/admin-organizacion-api.service.spec.ts |
| ?? | frontend-dimabug/src/app/core/services/admin-organizacion-api.service.ts |
| ?? | frontend-dimabug/src/app/core/services/admin-pruebas-api.service.spec.ts |
| ?? | frontend-dimabug/src/app/core/services/admin-pruebas-api.service.ts |
| ?? | frontend-dimabug/src/app/core/services/conocimiento-soluciones.service.spec.ts |
| ?? | frontend-dimabug/src/app/core/services/organizacion-pruebas.service.spec.ts |
| ?? | frontend-dimabug/src/app/core/services/procedimiento-recuperacion-qa014.service.spec.ts |
| ?? | frontend-dimabug/src/app/core/services/respaldo-departamentos-local.service.spec.ts |
| ?? | frontend-dimabug/src/app/core/services/respaldo-departamentos-local.service.ts |
| ?? | frontend-dimabug/src/app/core/services/respaldo-pruebas-local.service.ts |
| ?? | frontend-dimabug/src/app/features/admin/admin-catalogo.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/admin/admin-modulos.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/conocimiento/conocimiento-form.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/conocimiento/conocimiento-pruebas-qa013.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/conocimiento/conocimiento-soluciones-detalle.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/departamentos/departamentos.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/hardware/hardware.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/procedimiento/cambios-procedimiento.guard.ts |
| ?? | frontend-dimabug/src/app/features/procedimiento/pasos-qa013.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/procedimiento/procedimiento-form.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/procedimiento/procedimiento-recuperacion-qa014.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/pruebas/pruebas.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/soluciones/soluciones.component.spec.ts |
| ?? | frontend-dimabug/src/app/features/usuarios/usuarios.component.spec.ts |
| ?? | frontend-dimabug/src/app/shared/layout/sistemas-navegacion.component.spec.ts |

### Configuración raíz

| Estado | Archivo |
|---|---|
| M | .gitignore |
