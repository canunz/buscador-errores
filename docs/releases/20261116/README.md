# DimaBug — entrega staging del 16 de noviembre de 2026

Esta entrega publica las fuentes seleccionadas del release `staging-final-20261116`, no todo el checkout de integración. Incluye registro universal, aislamiento de autorregistros, portal responsive Mis tickets, datos del solicitante, fotografías privadas y prioridad MEDIA del servidor. Por decisión explícita se conserva el OCR **ya incluido en el Angular desplegado**; no se incorporan cambios de WhatsApp ni desarrollos posteriores.

## Correspondencia y validación

- Backend desplegado SHA-256: `c09c10adc54828ae407085032ec41e5cfb796564445f01d0afe27fa26ab29b15`.
- Angular ZIP desplegado SHA-256: `f2015cd347f207c6a1ce443e64defa67b931aac1f278c5af26b496421927b806`.
- Las 668 clases del backend recompilado coinciden byte a byte con las clases del JAR desplegado. Los recursos y las fuentes seleccionadas se inventarían en `fuentes-sha256.json`.
- Pruebas críticas repetidas para publicación: 26 backend y 18 Angular, sin fallos. Esto no equivale a ejecutar toda la suite ni todas las pruebas manuales.
- MySQL 8: migración de ocho columnas y concurrencia verificadas previamente en un entorno aislado. No se accedió a bases de datos durante esta publicación.
- En staging se verificaron Hibernate validate, MySQL, Angular HTTPS, prioridad MEDIA, cuatro rechazos por datos obligatorios, creación/fotografía propia y rechazo de ticket/fotografía ajenos. Se conserva un ticket sintético de control como evidencia, sin copiar sus datos a Git.
- Gmail de autenticación y despachador reactivados posteriormente, con recepción real, consumo del código y JWT TECNICO verificados. Bienvenida, ticket crítico, SMTP y automatizaciones permanecen deshabilitados. No se exportan configuración real, tokens ni custodia.

## Compilación

Requisitos: JDK 21, Maven Wrapper y Node compatible con Angular 19. Instalar Angular mediante `npm ci` en `frontend-dimabug`; compilar con `npm run build -- --configuration production`. Production utiliza `/api` en el mismo origen y no contiene credenciales.

Para reproducir la selección de recursos del backend: `./mvnw -f pom-release-final.xml package` desde `dima-bug-backend` (en Windows, `mvnw.cmd`). Este POM incorpora ImageIO WEBP 3.15.0 y excluye `application-staging.properties` del JAR: staging usa configuración **externa**, no el archivo orientativo del repositorio. El artefacto reconstruido puede tener otro hash ZIP por metadatos de empaquetado; las huellas anteriores identifican los originales desplegados.

Pruebas backend focalizadas: `./mvnw -f pom-release-final.xml test -Dtest=DatosSolicitanteTest,CatalogosSolicitudApiTest,SolicitudPortalHttpTest,CrearSolicitudServiceTest,RegistroUniversalSeguridadTest`. En JDK con restricciones de instrumentación debe suministrarse Mockito como javaagent. Angular: `npx ng test --watch=false --browsers=ChromeHeadless` con los includes del portal, auth.guard, datos-solicitante y ticket-solicitud.

## Migraciones: revisar antes de ejecutar

Se conservan todas las migraciones históricas. Sobre el esquema anterior de staging, el orden adicional validado es:

1. `docs/04_Base_Datos/V20261110_01__correo_codigo.sql` (cola de autenticación, si no existe).
2. `dima-bug-backend/src/main/resources/db/migration/V20261115_01__registro_universal.sql`.
3. `dima-bug-backend/src/main/resources/db/migration/V20261116_01__ticket_datos_solicitante.sql`.

Las tres ya existen en el esquema staging publicado. **No repetirlas**. No se activa ningún ejecutor automático de migraciones. Confirmar esquema y recibos, respaldar y aplicar solo lo realmente pendiente; un DDL MySQL parcialmente aplicado exige revisión, no reintento automático. Las ocho columnas del ticket permiten NULL para conservar tickets anteriores; el CHECK exige un snapshot coherente para los nuevos campos.

## Configuración y privacidad

Mantener DB, JWT y OAuth en variables externas protegidas. No publicar `.env`, claves AES, credenciales Google, JWT ni dumps. El perfil `qa-local` es una definición para herramientas locales de QA; requiere el runtime TLS y la custodia externos, y no debe utilizarse directamente como un perfil operativo sin ese entorno.

Para autenticación habitual en staging: `GMAIL_OAUTH_HABILITADO=true`, `GMAIL_ENVIO_REAL_HABILITADO=true`, `DIMABUG_GMAIL_ENVIO_REAL_HABILITADO=true`, `DIMABUG_AUTH_CORREO_HABILITADO=true`, `DIMABUG_AUTH_REGISTRO_UNIVERSAL_HABILITADO=true`. Los emisores `DIMABUG_CORREO_BIENVENIDA_HABILITADO` y `DIMABUG_CORREO_TICKET_CRITICO_HABILITADO` permanecen false; SMTP excluido y automatización deshabilitada. Conservar las cuotas verificadas, vencimiento de cinco minutos, consumo único, límite de intentos y almacenamiento protegido.

El backend efectivo de staging escucha únicamente en loopback:8081, con perfil staging y Hibernate validate. Nginx sirve Angular y redirige `/api/`; el límite HTTP es 32m. Multipart: 10 MB por archivo/32 MB por solicitud, con un límite funcional más estricto de cinco fotos de 5 MB. Conservar el almacenamiento privado de fotos y autorización por propietario.

Verificar el buzón no demuestra vínculo laboral. El servidor asigna exclusivamente USUARIO a autorregistros y permite solo tickets propios y catálogos mínimos; no concede acceso a conocimientos, procedimientos ni directorios internos. No se aceptan solicitante, técnico o prioridad del cliente para el portal básico.

## Reversión conjunta y cuarentena obligatoria

Activar mantenimiento y detener staging. Deshabilitar correos. Antes de arrancar un backend anterior, aplicar la cuarentena transaccional del guion `reversion_cuarentena.sql` mediante un ejecutor que compruebe los resultados y haga COMMIT únicamente si no quedan autorregistros activos ni códigos utilizables; el guion deliberadamente **no incluye COMMIT automático**. Ante error previo confirmar ROLLBACK; ante resultado desconocido no reintentar ni abrir el portal.

Restaurar conjuntamente backend, Angular y configuración desde respaldos verificados. No restaurar automáticamente MySQL ni adjuntos y no eliminar columnas nuevas con datos. Verificar cuarentena, arranque, Hibernate, rutas, roles y correo deshabilitado antes de retirar mantenimiento. Nunca arrancar el backend anterior con autorregistros activos: no entiende su alcance restringido.

## Alcance del commit

Se preservan el README y únicamente la historia auditada de origin/main. La entrega debe crearse como un snapshot cuyo único padre sea main; no fusionar ni heredar integracion-final-uat, carolina, catalina o codex/integracion-mvp, porque conservan credenciales históricas expuestas. Se elimina del árbol entregado un log previamente versionado y un archivo sin extensión llamado Untitled; sus copias locales permanecen intactas. No se incluyen binarios compilados, respaldos, custodia, datos reales, logs ni herramientas remotas. Los documentos de diseño LOCAL se conservan como antecedentes: este documento describe el estado publicado y prevalece sobre sus notas históricas.

Las fuentes se almacenan en Git con LF. El inventario distingue la huella original de las fuentes del release y la huella del contenido normalizado del repositorio; únicamente se normalizan finales CRLF, sin cambios lógicos.
