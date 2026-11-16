# Registro universal — implementación local

Estado: candidato local; no desplegado. No se ha utilizado MySQL QA, staging, producción ni OAuth real.

## Comportamiento

- Las cuentas anteriores reciben `ADMINISTRADO` por defecto en la migración. Se conservan identificadores, roles, hashes y estado activo/inactivo.
- Un correo nuevo puede generar una cuenta `AUTO_PENDIENTE`, inactiva, con nombre inicial «Solicitante» y rol `USUARIO` seleccionado por el servidor. No se acepta un rol del cliente ni se llama al emisor de bienvenida.
- Código válido, consumo y transición a `AUTO_CONFIRMADO` activo se confirman en la misma transacción. El controlador genera el JWT después de que esa transacción termine.
- Cinco errores de validación, vencimiento, reenvío mínimo y cuotas por cuenta mantienen el comportamiento anterior. El mensaje público de solicitud no revela si existe o está bloqueada una cuenta.
- Una cuenta administrada inactiva o un autorregistro confirmado bloqueado no se reactiva mediante códigos. Cancelar una cuenta pendiente produce `AUTO_CANCELADO`; se invalidan los códigos al bloquear y la cuenta cancelada no puede activarse administrativamente sin otro proceso explícito.
- El administrador puede modificar el nombre de un autorregistro, pero esta implementación impide cambiar su correo verificado o convertir su rol en privilegiado. No incorpora un procedimiento de promoción.
- El correo verificado demuestra control del buzón; no demuestra empleo en Dimarsa. No vincula automáticamente una ficha laboral ni concede confianza especial a un dominio corporativo.

## Aislamiento de permisos y datos

El JWT conserva su formato. En cada solicitud se consulta el estado y origen actuales en la base de datos, sin reutilizar la caché de identidad de cinco minutos. El servidor construye `SCOPE_TICKETS_PROPIOS`; no procede del JWT ni de Angular.

Para ese alcance se permite exclusivamente `GET /api/auth/me`, `GET /api/health`, crear un ticket propio y listar/contar/consultar los propios. Una lista cerrada rechaza directorios, conocimientos, procedimientos, sugerencias, recursos, adjuntos y modificaciones posteriores. El solicitante se obtiene de la identidad autenticada, no del cuerpo de creación.

Las consultas básicas evitan las proyecciones internas enriquecidas. La respuesta elimina técnico asignado, clasificación, conocimientos vinculados, solución interna, etiquetas, comentarios, historial y adjuntos. Los filtros de técnico, etiquetas y texto interno se rechazan. Los tickets ajenos responden 404. Angular presenta un portal básico sin precargar directorios ni catálogos; los guardias y el menú son secundarios a la autorización del backend.

Los endpoints de autoservicio **publicados previamente para visitantes anónimos** conservan su política pública. Un visitante que cierre sesión sigue siendo un visitante anónimo: no se pretende convertir material expresamente público en información privada. Los datos internos protegidos no se publican mediante este cambio.

## Límites y concurrencia

- Límite HTTP existente por dirección de conexión: 20 solicitudes/minuto; techo de instancia 300/minuto, cuerpo de autenticación máximo 4096 bytes y almacenamiento acotado de direcciones. No se confía en un `X-Forwarded-For` aportado directamente por el cliente. Tras un proxy, debe verificarse la resolución segura de la IP; en ausencia de ella el límite se comparte entre clientes, lo que restringe disponibilidad y no amplía permisos.
- Límites de códigos existentes: 60 segundos entre emisiones, cinco en 15 minutos y 20/día por cuenta; cinco intentos por código y cinco minutos de vigencia predeterminada.
- Nuevas cuentas: 20/hora, 100/día y 200 pendientes como valores iniciales globales persistentes. No hay eliminación ni reciclaje automático de personas. Agotar el cupo exige revisión operativa; no se amplía automáticamente.
- Una fila de rol `USUARIO` bloqueada serializa admisiones nuevas entre instancias. Se vuelve a comprobar el correo después de adquirir el bloqueo. Las operaciones de autenticación utilizan `READ_COMMITTED` y bloqueo de la fila de usuario; consumo y límites se evalúan después del bloqueo.
- Un índice único de `LOWER(TRIM(usuario_email))` constituye la última barrera de duplicados. Una colisión con una creación administrativa concurrente produce rollback, nunca una cuenta duplicada o privilegiada. No hay reintento automático ni envío adicional.
- En varias instancias sigue siendo necesario dimensionar un límite compartido en el borde. Las cuotas persistentes de creación permanecen compartidas; los contadores HTTP existentes son de instancia.

## Configuración preparada, no aplicada

La nueva propiedad `dimabug.auth.registro-universal.habilitado` es **false** por defecto. Solo la habilitación autorizada futura admitirá nuevas cuentas. Deshabilitarla impide admisiones nuevas, pero permite finalizar una verificación pendiente válida; no sustituye el bloqueo de cuentas.

Para una activación futura deben verificarse explícitamente:

```properties
dimabug.auth.registro-universal.habilitado=true
dimabug.auth.registro-universal.maximo-hora=20
dimabug.auth.registro-universal.maximo-dia=100
dimabug.auth.registro-universal.maximo-pendientes=200
dimabug.correo.bienvenida.habilitado=false
dimabug.correo.ticket-critico.habilitado=false
```

Se reutilizan el transporte de autenticación, el cifrado de cola y la custodia Gmail existentes. La preparación local y los ejecutores de pruebas fijan Gmail, despacho y automatizaciones a false; excluyen variables heredadas de DB/JWT/Google. No se generan ni instalan credenciales. No se modifica la configuración remota. La activación futura necesita comprobar también la exclusión de SMTP y los indicadores de automatización existentes.

## Migración y comprobaciones previas

Archivo: `dima-bug-backend/src/main/resources/db/migration/V20261115_01__registro_universal.sql`.

Requiere MySQL **8.0.16 o superior**, respaldo verificado y una ventana de mantenimiento autorizada. No es una migración automática al arrancar. Añade estado y restricciones; no actualiza datos de cuentas existentes. La DDL de MySQL no se revierte con `ROLLBACK`.

Antes de aplicarla, en una copia desechable del esquema y posteriormente en el destino autorizado:

1. Verificar servidor, versión, esquema y respaldo. Confirmar ausencia de la columna y de los nombres de índices nuevos.
2. Contar grupos duplicados por `LOWER(TRIM(usuario_email))`, sin divulgar direcciones. Si existen, detenerse: no fusionar, borrar ni cambiar correos automáticamente.
3. Confirmar un único rol activo con nombre canónico `USUARIO`; no crear ni reutilizar un identificador de rol por suposición.
4. Aplicar el archivo completo una sola vez en la copia, iniciar Hibernate `validate` y probar concurrencia con conexiones MySQL nativas.
5. Comparar usuarios, roles, estados, hashes y privilegios. Solo se admiten los objetos nuevos previstos.

La prueba H2 ejecuta las cláusulas del ALTER por separado y utiliza un modelo equivalente para la clave canónica. Se ejecutó además **el archivo SQL completo en MySQL 8.0.36 nativo**, en un contenedor desechable sin red, puertos publicados ni volúmenes persistentes, utilizando la imagen local fijada por digest que aparece en la evidencia. Pasaron la conservación de cuentas legadas, las restricciones, la unicidad normalizada y un rollback sintético. El contenedor fue eliminado; no se accedió a MySQL QA. Las pruebas de concurrencia completas de la aplicación se ejecutaron en H2; deben repetirse con el controlador JDBC y MySQL nativo antes del despliegue.

## Reversión segura

Antes de cualquier despliegue se deben respaldar JAR, Angular, configuración y MySQL. Se puede detener la admisión nueva con la propiedad false sin retirar el control de alcance.

**No restaurar ciegamente el backend anterior tras admitir autorregistros:** ese backend desconoce el origen y podría conceder a un `USUARIO` confirmado los permisos históricos de consulta interna. Si hay autorregistros, conservar una versión que aplique el alcance básico o mantener mantenimiento hasta aprobar un procedimiento específico de bloqueo de esas cuentas. No borrar la columna, cuentas o tickets para facilitar una reversión. No restaurar automáticamente la base de datos.

## Evidencia y siguientes controles

El candidato se prepara exclusivamente sobre la pareja de fuentes congeladas de staging, superponiendo los archivos de registro universal enumerados en el manifiesto. Se excluyen cambios posteriores de WhatsApp/OCR y los cambios locales no relacionados. No se realizan commits.

Las evidencias sanitizadas, fuentes seleccionadas, artefactos y hashes se conservan en `qa/artifacts/registro-universal-20261115`. Antes de desplegar quedan la comprobación de concurrencia de la aplicación en MySQL, la revisión del inventario y una nueva autorización. La recepción real por Gmail y la navegación de un autorregistro desde el dominio staging requieren una prueba posterior autorizada; no se simulan como verificadas remotamente.
