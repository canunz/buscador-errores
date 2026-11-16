# Gmail API con OAuth 2.0 en DimaBug

## Estado entregado

IMPLEMENTADO y validado con pruebas aisladas. La configuración versionada deja autorización/envío real deshabilitados por defecto. En tareas funcionales posteriores el usuario confirmó la autorización de una cuenta y recepción real de mensajes; esa confirmación manual no se confunde con los tests, que no envían correo real. La consolidación Git no modifica la cuenta ni su modo de envío.

JWT y la política STATELESS permanecen como autenticación de DimaBug. No se agregó oauth2Login ni se utilizaron permisos de identidad para iniciar sesión. El cliente oficial spring-security-oauth2-client utiliza la versión administrada por Spring Boot 4.1.1; sus clientes de authorization_code y refresh_token intercambian las credenciales exclusivamente en el backend.

El permiso se fija en el código a `https://www.googleapis.com/auth/gmail.send`. No se solicitan openid, email, profile, lectura de buzón, modificación, contactos ni acceso completo mail.google.com. La solicitud incluye access_type=offline, PKCE S256 y consentimiento explícito, sin acumular scopes anteriores. El backend rechaza un grant con permisos adicionales.

## Rutas y seguridad

| Ruta | Uso y protección |
| --- | --- |
| GET /api/admin/gmail | Estado sin tokens; JWT de administrador activo |
| POST /api/admin/gmail/autorizacion | Inicia vinculación; JWT de administrador activo |
| GET /login/oauth2/code/google | Callback exacto registrado en Google Cloud; estado de un uso, cookie y PKCE |
| POST /api/admin/gmail/revocar | Deshabilita localmente y revoca en Google; JWT de administrador activo |
| /admin/gmail en Angular | Pantalla protegida con adminGuard |

La autorización se inicia desde Administración → Conexión Gmail. El backend devuelve solo la URL de consentimiento, con Client ID público y estado efímero; nunca Client Secret ni tokens. Una cookie HttpOnly, SameSite=Lax y duración de cinco minutos vincula el callback al navegador. Es Secure bajo HTTPS; se permite HTTP únicamente en localhost para desarrollo. No se crea una sesión JSESSIONID.

Se almacena el hash de estado y navegador, junto con el verificador PKCE cifrado. El callback consume el estado de forma transaccional y comprueba caducidad, cookie, administrador aún activo y versión de la autorización. Los replays y los intentos anteriores a una revocación se rechazan. Hay un máximo de tres inicios pendientes por administrador durante los cinco minutos de vigencia.

El callback no sustituye la identidad JWT ni devuelve un token a Angular. Tras completar la vinculación redirige a la pantalla administrativa sin parámetros de tokens ni códigos. Las respuestas de OAuth/estado llevan Cache-Control: no-store. Solo se permite cambiar de cuenta después de revocar la conexión existente.

## Configuración local por entorno

1. En Google Cloud: cliente tipo Aplicación web, Gmail API habilitada y URI de redireccionamiento **exactamente** `http://localhost:9090/login/oauth2/code/google`, sin barra final.
2. Confirmar con el administrador de Google Workspace la autorización del cliente y el consentimiento para gmail.send. Usar una aplicación interna de la organización cuando corresponda.
3. Configurar las variables en el entorno privado de ejecución de Spring Boot. No añadirlas como argumentos de línea de comandos ni guardarlas en el repositorio.
4. Añadir `gmail` al perfil local: `SPRING_PROFILES_ACTIVE=staging,gmail`. Mantener `DIMABUG_AUTOMATIZACION_MODO=SIMULADO` y `GMAIL_ENVIO_REAL_HABILITADO=false`.
5. Reiniciar mediante la configuración local habitual y abrir `http://localhost:4200/admin/gmail` con una sesión administrativa de DimaBug.
6. Seleccionar Autorizar cuenta Gmail y elegir la misma cuenta corporativa configurada como remitente. Revisar que el consentimiento solicite únicamente enviar correos. Conectar no envía un mensaje.

| Variable | Uso |
| --- | --- |
| GOOGLE_CLIENT_ID | Client ID del cliente web |
| GOOGLE_CLIENT_SECRET | Client Secret; solo entorno privado del backend |
| GMAIL_TOKEN_ENCRYPTION_KEY | Clave AES de 32 bytes codificada en Base64, estable entre reinicios |
| GMAIL_SENDER_EMAIL | Cuenta corporativa autorizada como remitente |
| GMAIL_OAUTH_HABILITADO | true permite iniciar OAuth, sin activar envíos |
| GMAIL_ENVIO_REAL_HABILITADO | Mantener false hasta confirmación del usuario |
| GMAIL_OAUTH_REDIRECT_URI | Opcional; por defecto el callback localhost:9090 indicado |
| GMAIL_FRONTEND_URL | Opcional; por defecto http://localhost:4200/admin/gmail |

También se admiten como alias las variables `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` y `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` indicadas previamente. Las variables GOOGLE_CLIENT_ID/SECRET tienen prioridad.

Para generar una clave local sin imprimirla, desde PowerShell privado:

```powershell
$gmailKeyBytes = New-Object byte[] 32
$gmailRng = [Security.Cryptography.RandomNumberGenerator]::Create()
$gmailRng.GetBytes($gmailKeyBytes)
$env:GMAIL_TOKEN_ENCRYPTION_KEY = [Convert]::ToBase64String($gmailKeyBytes)
$gmailRng.Dispose()
[Array]::Clear($gmailKeyBytes, 0, $gmailKeyBytes.Length)
```

La variable queda solo en ese proceso y sus hijos. Guardar la clave en el gestor privado de secretos o la configuración privada de ejecución para reutilizarla; no volver a generarla en cada arranque. Spring Boot no carga archivos .env automáticamente. No mostrar variables, exportarlas a logs ni pegar Client Secret o tokens en este chat.

Usar **localhost tanto en Angular como en el callback**. Abrir Angular mediante 127.0.0.1 mientras el callback usa localhost impediría recibir la cookie vinculada al navegador. Mantener los logs HTTP/OAuth sin DEBUG/TRACE; el perfil gmail fija los loggers de transporte y OAuth en INFO.

## Cifrado y ciclo de vida

Access token, refresh token y verificador PKCE se cifran con AES-256-GCM, nonce aleatorio de 12 bytes y contexto autenticado por tipo de dato. La clave se proporciona por entorno; no se persiste junto a los tokens. La autorización almacena versión para impedir que una renovación concurrente restaure credenciales revocadas.

El token se renueva en el backend antes del envío si vence en menos de 60 segundos. Si Google no rota el refresh token, se conserva el anterior cifrado. invalid_grant invalida localmente las credenciales y exige otra autorización. Los tokens jamás aparecen en los DTO de estado, mensajes públicos ni auditoría.

Revocar primero elimina la capacidad local de utilizar los tokens. Si Google no confirma su revocación, la credencial de revocación permanece cifrada y deshabilitada para poder reintentar. No se puede conectar otra cuenta mientras exista revocación pendiente. Una petición que ya estuviera en tránsito podría haber sido aceptada antes de revocar; la revocación no deshace un mensaje enviado.

## Envíos, recordatorios y compatibilidad

Gmail API implementa el puerto de correo existente. Utiliza messages.send con un mensaje MIME en Base64URL, destinatarios Para/CC, asunto y texto. Comparte la construcción MIME con SMTP. El modo SIMULADO y SMTP siguen disponibles.

La autorización por sí sola no activa el transporte. Después de una futura confirmación explícita del usuario será necesario **seleccionar GMAIL_API como DIMABUG_AUTOMATIZACION_MODO y establecer GMAIL_ENVIO_REAL_HABILITADO=true**. Esos valores no fueron activados en este trabajo.

Se reutilizan preparación SQL, reserva durable del intento y transporte posterior al commit. Se conserva la cuota por usuario/hora y el límite de veinte destinatarios. Gmail no se invoca dentro de una transacción SQL. Las consultas de estado no envían ni crean comunicaciones.

El resultado registra fecha/hora, destinatarios del snapshot y eventos GMAIL_INICIADO/GMAIL_ACEPTADO o GMAIL_NO_CONFIRMADO. Los recordatorios tienen sus propios eventos. El identificador devuelto por Gmail se registra para el envío principal. Los eventos anteriores de SMTP/simulación y las ejecuciones históricas se conservan.

Un timeout o fallo incierto no provoca reenvío automático ni se presenta como entrega confirmada. No existe garantía de exactamente una entrega externa: Gmail API no aporta una clave de idempotencia para messages.send. Ante una caída entre preparación/reserva/envío, el historial debe revisarse antes de recuperar el intento.

gmail.send permite enviar, pero no leer respuestas ni verificar llegada a la bandeja de entrada. No se implementa recepción automática de fotografías desde Gmail. Las reglas existentes de revisión/aprobación de evidencias permanecen; esta integración cubre únicamente comunicaciones salientes.

El parámetro hd=dimarsa.cl es una indicación para Google, no una prueba criptográfica del dominio. Sin agregar scopes de identidad/lectura no se verifica la dirección real de la cuenta mediante userinfo. Por ello, la política de aplicación interna de Workspace y la elección administrativa de la cuenta configurada son pasos manuales necesarios. La aceptación de un mensaje tampoco acredita entrega en destino.

## Migración y archivos

`09_gmail_oauth_cifrado.sql` crea gm_cuenta_oauth, gm_estado_oauth y gm_auditoria_oauth. Aplicado por el usuario sobre staging; verificación de solo lectura correcta y estado inicial DESCONECTADA. No altera procedimientos ni comunicaciones históricas.

- Backend nuevo: `correo/application/port/out/{GmailTokenStore,GoogleGmailPort,CifradoGmailPort,GmailSettings}`, `correo/application/service/{GmailOAuthService,GmailException}`, `correo/infrastructure/gmail/{GmailConfig,CifradoGmailAes,GoogleGmailHttp}`, `correo/infrastructure/persistence/*` y `correo/infrastructure/web/GmailOAuthController`.
- Transporte: `CorreoGmailApiAdapter`, `MensajeCorreoMime`; ajustes en `CorreoSmtpAdapter`, `CorreoSimuladoAdapter`, `CorreoEvidenciaPort`, `AutomatizarEvidenciaService` y el adaptador de solicitudes para auditoría/cuotas.
- Configuración: `pom.xml`, `SecurityConfig`, `GlobalExceptionHandler`, `application-gmail.properties`.
- Frontend: `GmailAdminService`, `AdminGmailComponent`, ruta administrativa, enlace de administración y mensajes genéricos de aceptación del servicio de correo.
- Pruebas: `CifradoGmailTest`, `GoogleGmailHttpTest`, `GmailOAuthHttpTest`, specs de componente y servicio Angular.
- Helper de verificación: modo gmail-inspect y `VerifyGmailSchema.java`.

## Pruebas

1.086 pruebas backend y 412 Angular aprobadas; compilación Angular correcta con las advertencias de presupuesto del bundle y CSS ya existentes. Incluyen JWT y roles, callback sin sesión, cookie/estado/PKCE, expiración/replay, scopes adicionales, cifrado/manipulación, renovación, revocación, conservación del refresh, rechazo de renovaciones antiguas, Para/CC, mensajes MIME, recordatorios, errores y ausencia de envíos duplicados. Un fallo temporal de renovación conserva las credenciales; invalid_grant exige reautorización. Google y SMTP se sustituyeron por mocks o servidores HTTP en memoria; no hubo envíos reales.

No se comprobó una autorización real en Google Workspace porque faltan configuración privada y aprobación corporativa. No se activó el transporte real.

Verificación local posterior al despliegue: salud directa y proxy HTTP 200, endpoints administrativos sin JWT HTTP 401 y callback sin autorización válida HTTP 400. El helper de reinicio conserva staging, fuerza SIMULADO y deshabilita Gmail real. La autorización inicial en Google y la prueba de entrega quedan como pasos manuales posteriores; no se crearon nuevas ejecuciones ni comunicaciones de prueba en staging.

Referencias oficiales: [OAuth para aplicaciones web de Google](https://developers.google.com/identity/protocols/oauth2/web-server), [permisos de Gmail](https://developers.google.com/workspace/gmail/api/auth/scopes), [envío MIME con Gmail API](https://developers.google.com/workspace/gmail/api/guides/sending) y [clientes OAuth de Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html).
