# Variables y configuración sin secretos

Referencia 2026-11-09. Las variables las hereda el proceso desde el entorno/IDE/servicio. No se presupone carga automática de .env. `application.properties` privado está ignorado; los perfiles versionados solo contienen placeholders y defaults públicos.

| Variable/configuración | Uso |
|---|---|
| SPRING_PROFILES_ACTIVE | staging; añadir gmail o smtp cuando corresponda |
| DB_URL / DB_USERNAME / DB_PASSWORD | Datasource del perfil staging; valores externos, mínimo privilegio |
| JWT_SECRET / JWT_EXPIRATION | Firma HMAC (mínimo 32 bytes UTF-8) y vigencia en milisegundos |
| SPRING_JPA_HIBERNATE_DDL_AUTO=none | Evitar DDL automático en una base existente |
| SPRING_SQL_INIT_MODE=never | Evitar inicialización SQL automática |
| DIMABUG_STORAGE_MATERIALES_DIR / DIMABUG_STORAGE_TICKETS_DIR | Directorios privados; los defaults relativos dependen del working directory |
| DIMABUG_AUTOMATIZACION_MODO | SIMULADO por defecto; SMTP/GMAIL_API solo si se habilitan explícitamente |
| GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET | Cliente OAuth externo al repositorio y al frontend |
| GMAIL_TOKEN_ENCRYPTION_KEY | 32 bytes codificados Base64, estable entre reinicios; nunca publicar |
| GMAIL_SENDER_EMAIL | Cuenta autorizada para correo |
| GMAIL_OAUTH_HABILITADO | Permite vinculación, no activa envío por sí solo |
| GMAIL_ENVIO_REAL_HABILITADO | false por defecto; real requiere habilitación y transporte compatible |
| GMAIL_OAUTH_REDIRECT_URI | Callback exacto registrado; default local http://localhost:9090/login/oauth2/code/google |
| GMAIL_FRONTEND_URL | Destino administrativo tras OAuth; no incluir tokens |
| SMTP_HOST / SMTP_PORT / SMTP_USERNAME / SMTP_PASSWORD | Perfil SMTP opcional; nombres exactos/defaults en application-smtp.properties |
| SMTP_FROM | Remitente del perfil SMTP; verificar el placeholder del perfil antes de configurarlo |
| publicPortalUrl (Angular) | Base pública para enlaces NFC/QR, sin secretos; vacío hasta despliegue |

Los alias estándar SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID/CLIENT_SECRET se aceptan como fallback por el perfil Gmail. No equivale a activar oauth2Login.

Consultar los placeholders exactos de application-staging.properties, application-gmail.properties y application-smtp.properties. No pegar valores reales en comandos compartidos, issues, capturas o logs. No versionar tokens cifrados ni la clave que los descifra.

Guía específica: [Gmail y OAuth](../implementacion/automatizacion/GUIA_GMAIL_API_OAUTH.md).

[Índice vigente](../README.md).
