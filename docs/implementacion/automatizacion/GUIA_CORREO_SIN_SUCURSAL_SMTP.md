# Correo sin sucursal y preparación SMTP

## Resultado y alcance

ENVIAR_CORREO y SOLICITAR_EVIDENCIA permiten elegir destinatarios Para/CC y editar asunto y mensaje sin consultar sucursales ni colaboradores. El usuario confirmó expresamente extender el envío sin sucursal a evidencias. Esta última conserva categorías de fotografías, recepción, revisión y aprobación crítica. Se reutilizan el modal de validación externa, el editor de destinatarios y el endpoint autenticado del paso.

Los clientes anteriores que envían una sucursal en una solicitud de evidencias conservan su validación de directorio y permisos de contenido. El nuevo formulario omite sucursalId. No se modifican los procedimientos publicados, los contratos de ejecución ni las solicitudes históricas; las nuevas solicitudes capturan los destinatarios ingresados. Las acciones AUTOMATICO que validan contexto de sucursal continúan requiriéndolo; no se sustituye por una sucursal ficticia.

La cuenta de Carolina puede ingresarse manualmente; no hay destinatario obligatorio ni dirección personal codificada en la configuración. Continúa la política existente de direcciones corporativas @dimarsa.cl.

## SMTP preparado, sin activar

Se incorporó `spring-boot-starter-mail`, con versión administrada por el parent Spring Boot 4.1.1 del proyecto. `CorreoSmtpAdapter` implementa el puerto existente; el adaptador simulado continúa disponible. No había configuración ni transporte SMTP antes de este cambio.

El perfil opcional `smtp` utiliza `application-smtp.properties`, con valores de credenciales obtenidos exclusivamente de variables de entorno. Las variables se configuran en el entorno privado del proceso de Spring Boot; no se envían al navegador. Un archivo .env no se carga automáticamente por Spring Boot.

| Variable | Valor o uso |
| --- | --- |
| SMTP_HOST | smtp.gmail.com por defecto |
| SMTP_PORT | 587 por defecto |
| SMTP_USERNAME | Cuenta corporativa autorizada, sin valor en el repositorio |
| SMTP_PASSWORD | Credencial SMTP autorizada por Workspace, sin valor en el repositorio |
| SMTP_FROM | Remitente autorizado; si se omite utiliza SMTP_USERNAME |
| SMTP_MAX_INTENTOS_HORA | 20 por defecto |
| SPRING_PROFILES_ACTIVE | Añadir smtp al perfil correspondiente, después de autorización |
| DIMABUG_AUTOMATIZACION_MODO | SMTP únicamente para activar el transporte real autorizado |

El perfil exige STARTTLS y verificación de identidad del servidor, fija timeouts de conexión/lectura/escritura de cinco segundos y desactiva el debug SMTP. No prueba conexiones al arrancar. Sin cuenta/remitente configurado se informa que el servicio no está disponible; no se simula un éxito real.

La autorización de Google Workspace sigue pendiente. Si la organización permite contraseñas de aplicación, se configura una credencial de ese tipo mediante el entorno privado. Esta implementación no obtiene tokens OAuth ni administra su renovación; si la organización exige OAuth2, será necesaria esa integración antes de activar el envío. No utilizar la contraseña habitual de la cuenta como solución a una política corporativa que impida SMTP.

Gmail publica smtp.gmail.com con 587 y STARTTLS en su [documentación oficial](https://developers.google.com/workspace/gmail/imap/imap-smtp). La configuración de JavaMailSender y los timeouts se basan en la [documentación de Spring Boot Mail](https://docs.spring.io/spring-boot/reference/io/email.html). Las opciones permitidas para autenticación deben confirmarse con el administrador de Workspace, siguiendo la [guía oficial para aplicaciones](https://knowledge.workspace.google.com/admin/gmail/send-email-from-a-printer-scanner-or-app).

## Consistencia, resultados y prevención de abuso

- Endpoint protegido por los permisos operacionales existentes y pertenencia de la ejecución. No se añadió un endpoint abierto de correo.
- Máximo de 20 destinatarios, validación corporativa, al menos un Para y rechazo de saltos de línea en asunto. Asunto hasta 200 caracteres y mensaje hasta 2.000.
- El paso debe estar habilitado; las comunicaciones se identifican por ejecución/paso y firma del contenido para evitar duplicados ante el mismo reintento.
- En SMTP, la preparación SQL se confirma antes del transporte. Un evento después del commit reserva el intento en una transacción breve y hace el envío fuera de la transacción SQL. El resultado se registra en otra transacción breve.
- Cuota durable por usuario y hora, incluyendo intentos fallidos y recordatorios. Un bloqueo de la cuenta serializa las reservas entre ejecuciones concurrentes.
- Un fallo o caída deja ERROR/ENVIANDO para revisión. No hay reenvío automático de un resultado incierto. Una referencia SMTP no garantiza deduplicación en el servidor ni entrega en destino.
- Un estado aceptado registra fecha/hora y auditoría. La interfaz indica aceptación por SMTP, no llegada a la bandeja de entrada. Una entrega real requiere comprobación manual posterior.
- Los errores se muestran sin respuestas SMTP crudas, contraseñas, tokens ni causas sensibles. No se registran secretos en el adaptador.

El mecanismo de eventos es síncrono después del commit; no es una nueva cola ni un worker. Una caída entre preparación y despacho puede dejar una comunicación pendiente; se debe revisar su historial antes de recuperar el intento. No se ofrece garantía de exactamente una entrega externa.

## Persistencia

`08_correo_sin_sucursal.sql` permite NULL en sucursal_id, sucursal_codigo, sucursal_nombre y responsable_nombre de se_solicitud_evidencia. No elimina ni actualiza registros. Aplicado por el usuario en staging y verificado por metadatos de solo lectura: las cuatro columnas aceptan NULL. La ampliación a evidencias no requiere otra migración ni volver a aplicar este script.

## Archivos principales

- Frontend: `validacion-externa.component.ts/html`, `automatizacion-ejecucion.service.ts`, `destinatarios-editor.component.ts`, `configuracion-paso.component.html` y pruebas de modal/validación.
- Backend: `AutomatizarEvidenciaService`, `AutomatizacionEjecucionController`, `SolicitudEvidencia`, su entidad y repositorio/adaptador.
- SMTP: `CorreoSmtpAdapter`, `CorreoSimuladoAdapter`, `DespacharCorreoConfirmado`, `CorreoPendiente`, `application-smtp.properties` y `pom.xml`.
- Pruebas nuevas: `CorreoSinSucursalHttpTest` y `CorreoSmtpAdapterTest`.
- Verificación de esquema: `VerifyMailSchema.java` y modo mail-inspect del helper local.

## Prueba manual posterior a autorización

Configurar el entorno privado, confirmar que Workspace autoriza SMTP, activar el perfil/modo explícitamente y reiniciar Spring Boot. Abrir un paso autorizado de Enviar correo e ingresar temporalmente el destinatario acordado, asunto y mensaje. Confirmar el envío una sola vez, revisar fecha/hora y aceptación SMTP en el historial y comprobar la bandeja de entrada por separado. No activar SMTP mientras esa autorización esté pendiente.

Las pruebas automatizadas usan H2 aislado y JavaMailSender mock; nunca Gmail ni destinatarios reales. Verifican Para múltiples/CC, persistencia sin sucursal, ausencia de transacción durante SMTP, autenticación fallida, conexión fallida, idempotencia, límites, inyección de cabeceras, permisos, configuración TLS y flujo previo de evidencias.

Resultado final: 1.055 pruebas backend y 403 Angular aprobadas; compilación Angular correcta con las advertencias previas de presupuesto del bundle y CSS. Se verificaron por HTTP 200 el backend 9090, Angular 4200 y su proxy. Se actualizó únicamente el backend local con perfil staging y modo SIMULADO. No se configuraron credenciales, no se activó el perfil SMTP, no se enviaron mensajes reales y no se crearon ejecuciones de prueba en staging.

El helper local de reinicio utiliza el classpath regenerado para incluir Spring Mail. Se retiró una copia generada obsoleta de application.properties después de mover esta configuración al perfil opcional application-smtp.properties; no se modificaron archivos privados de credenciales.

Ampliación posterior confirmada por el usuario: también se retiró la sucursal de Solicitar evidencias. Resultado actualizado: 1.056 pruebas backend y 404 Angular aprobadas, compilación correcta. Un caso JPA aislado comprueba solicitud sin sucursal, destinatarios Para/CC, reintento idempotente, recepción de tres fotografías y aprobación obligatoria del paso crítico. Angular sirve un bundle sin el selector sucursalCorreo ni el título «Solicitar evidencias a sucursal». No se modifica ninguna solicitud histórica ni se crea otra ejecución en staging.
