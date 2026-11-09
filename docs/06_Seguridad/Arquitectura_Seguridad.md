# Arquitectura de seguridad

## Privado y público

SecurityConfig mantiene JWT/roles y distingue consultas públicas de mutaciones administrativas. El portal solo devuelve DTO mínimos de soluciones activas con autoservicio y conocimiento publicado. Cada material requiere permiso propio de publicación. Detalle por ID y descarga revalidan esas condiciones; no basta filtrar el listado.

Las instrucciones se interpolan como texto. Los enlaces externos se validan, no usan credenciales ni esquemas ejecutables y se abren con noopener/noreferrer. No se entregan referencias internas de almacenamiento. El nombre de una etiqueta NFC no puede conceder acceso: al abrir su URL se comprueba publicación nuevamente.

## OAuth, correo y secretos

OAuth Gmail usa state/cookie efímeros, PKCE S256, scope gmail.send y cifrado AES-256-GCM con clave externa. Se preserva JWT de DimaBug. Los envíos tienen registro de actor, fechas, destinatarios y resultado; los inciertos no se reintentan ciegamente. Aceptación por Gmail no prueba entrega ni recepción de fotografías.

Client ID/Secret, claves AES/JWT, credenciales MySQL y tokens reales no se versionan. Los perfiles públicos contienen placeholders; .env, configuración local, logs, uploads y almacenes de claves se excluyen. Los fixtures sintéticos no autorizan usar sus claves fuera de tests.

## Archivos y evidencias

Descarga privada comprueba acceso al padre y pertenencia. Upload valida tamaño/contenido/tipo; las fotografías se normalizan y eliminan metadatos de imagen. La recepción manual requiere usuario autorizado; no afirma identidad Google verificada. Rondas/decisiones conservan historial y un paso crítico no se completa sin aprobación explícita.

## Límites

La sesión JWT continúa en localStorage, con los riesgos de XSS correspondientes. El límite público es por instancia/dirección remota, no distribuido. Publicar contenido requiere revisión humana. No se acredita pentest ni certificación de producción con la suite local.

[Índice vigente](../README.md).
