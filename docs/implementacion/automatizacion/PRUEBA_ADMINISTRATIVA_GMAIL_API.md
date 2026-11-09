> Informe/guía de su fase de implementación. El [estado consolidado](../../README.md) y las guías posteriores de recepción/portal distinguen las validaciones más recientes. Los defaults versionados no acreditan el estado privado de una instancia.

# Prueba administrativa Gmail API — preparada, sin activar

## Diagnóstico antes de los cambios

El backend utiliza staging,gmail, con OAuth habilitado y cuenta ACTIVA en staging. El transporte permanece SIMULADO y GMAIL_ENVIO_REAL_HABILITADO=false. Estos valores explican por qué los procedimientos siguen registrando simulaciones: conectar OAuth no selecciona el transporte real.

El transporte existente está implementado por CorreoGmailApiAdapter, CorreoSmtpAdapter y CorreoSimuladoAdapter sobre CorreoEvidenciaPort. GmailOAuthService gestiona acceso y renovación; GoogleGmailHttp utiliza Gmail API y el permiso fijo gmail.send. Los tokens siguen cifrados y no se devolvieron ni mostraron durante esta tarea.

Se reutilizan GmailOAuthService, Gmail API y MensajeCorreoMime. La prueba nueva no crea procedimientos, ejecuciones, solicitudes de evidencia ni recordatorios.

## Uso en Administración → Conexión Gmail

La sección Prueba de correo muestra remitente, destinatario único, conexión, transporte efectivo y último resultado. El destinatario es la cuenta corporativa configurada en GMAIL_SENDER_EMAIL, no una dirección libre. No admite CC. El botón permanece deshabilitado si el backend no confirma cuenta conectada y transporte Gmail real habilitado, si hay una operación en curso o si la prueba anterior quedó pendiente, enviada o incierta. La ruta y ambos endpoints requieren administrador.

Pulsar Enviar correo de prueba abre un diálogo de confirmación con destinatario y asunto. Cancelar o abrir/actualizar la pantalla no envía nada. Solo Confirmar envío real realiza el POST.

Asunto fijo: `[DimaBug] Prueba controlada Gmail API`.

Texto fijo: `Mensaje de prueba controlada de DimaBug. Confirma manualmente su recepción. Este envío no modifica procedimientos ni acredita recepción de evidencias.`

## Endpoints y persistencia

- GET /api/admin/gmail/prueba: consulta de estado y resultado, sin efectos externos.
- POST /api/admin/gmail/prueba: requiere UUID de idempotencia, confirmado=true y destinatario igual al remitente corporativo configurado. Permisos administrativos y usuario activo se comprueban en backend.
- gm_prueba_correo: intento lógico, usuario, destinatario, remitente, fechas, estado, identificador Gmail y resultado seguro.
- gm_prueba_evento: auditoría de las transiciones PENDIENTE, ENVIANDO y resultado final.

`10_prueba_gmail_administrativa.sql` crea solo esas dos tablas. Aplicado por el usuario y verificado en staging; cuenta OAuth ACTIVA y cero pruebas registradas durante la implementación. No se modificaron tablas de OAuth ni ejecuciones históricas.

## Duplicados, errores y límites

La clave UUID identifica el intento durable. Una repetición devuelve su resultado; no vuelve a llamar a Gmail. La preparación se serializa por la cuenta y la reserva PENDIENTE → ENVIANDO es atómica. Peticiones concurrentes, incluso con claves diferentes, reutilizan una prueba pendiente/en curso/enviada/incierta para ese destinatario.

Un ENVIADO o INCIERTO cierra esta prueba inicial y bloquea nuevos envíos desde el botón. No hay un reinicio automático ni una función para borrar su historial; otra campaña después de un envío aceptado requerirá una decisión e implementación posterior.

Solo un FALLIDO definitivo permite una nueva confirmación, una clave nueva y referencia explícita al último intento fallido. Una pestaña desactualizada no genera ese reintento. Se admiten como máximo tres intentos preparados por destinatario y hora, incluyendo fallos.

- PENDIENTE: preparado, sin reserva de transporte.
- ENVIANDO: reserva durable; Gmail puede estar procesando la solicitud.
- ENVIADO: Google devolvió identificador; entrega en bandeja de entrada no confirmada.
- FALLIDO: rechazo explícito, fallo de autorización/configuración o fallo antes de invocar messages.send.
- INCIERTO: timeout/error de envío o resultado que no permite confirmar aceptación. No se reintenta automáticamente.

Un ENVIANDO de más de 60 segundos se presenta como INCIERTO al consultarlo, sin alterar el registro. Una caída de proceso o fallo de persistencia después de la aceptación de Google requiere revisión manual; no existe garantía de exactamente una entrega externa. Las llamadas de red se realizan fuera de transacciones SQL. Los errores públicos y de auditoría contienen categorías seguras, no cuerpos de Google ni credenciales.

GoogleGmailHttp distingue rechazo HTTP 4xx de un resultado incierto de transporte/5xx. La prueba utiliza únicamente Gmail API, nunca un fallback a SMTP. La renovación reutiliza el flujo OAuth existente; no se renovaron ni revocaron tokens reales durante esta implementación.

## Primera prueba real: pasos manuales pendientes

1. Obtener la confirmación expresa de la usuaria para activar el transporte y realizar un único envío a la cuenta autorizada. Esta tarea no concede esa confirmación.
2. Revisar la cola y coordinar una ventana sin operaciones de otros técnicos: activar GMAIL_API afecta también los nuevos envíos legítimos de procedimientos, no solo la prueba administrativa.
3. Conservar las variables privadas existentes. Confirmar:

```properties
SPRING_PROFILES_ACTIVE=staging,gmail
GMAIL_OAUTH_HABILITADO=true
# Solo después de autorización expresa:
DIMABUG_AUTOMATIZACION_MODO=GMAIL_API
GMAIL_ENVIO_REAL_HABILITADO=true
```

4. Reiniciar con la configuración local habitual y abrir Administración → Conexión Gmail. Confirmar remitente/destinatario correcto, cuenta conectada, transporte GMAIL_API y envío habilitado.
5. Abrir el diálogo, revisar el Para único y confirmar una sola vez. Ante timeout, usar Actualizar estado antes de cualquier otra acción; no insistir creando procedimientos o ejecuciones.
6. Verificar último resultado, fecha, destinatario, identificador GMAIL-… y eventos en gm_prueba_evento. Estos datos no incluyen credenciales.
7. Comprobar manualmente Recibidos y Spam en Gmail y buscar el asunto `[DimaBug] Prueba controlada Gmail API`. Si la cuenta remitente y destinataria son la misma, revisar también Enviados. El permiso gmail.send no permite a DimaBug consultar el buzón ni confirmar recepción.
8. Desactivar la ventana según lo autorizado: GMAIL_ENVIO_REAL_HABILITADO=false y DIMABUG_AUTOMATIZACION_MODO=SIMULADO; reiniciar. No revocar la cuenta OAuth para desactivar únicamente los envíos.

No se ejecutó ninguno de los cambios de activación anteriores. Los valores privados que deben existir, sin guardarlos en el repositorio, siguen siendo GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GMAIL_SENDER_EMAIL y GMAIL_TOKEN_ENCRYPTION_KEY. Los alias documentados previamente para Client ID/Secret se conservan.

## Procedimientos y recordatorios

La solicitud de fotografías conserva el estado de espera y las condiciones de revisión/aprobación del paso crítico. ENVIAR_CORREO sigue sus reglas de finalización. Los recordatorios utilizan el mismo puerto Gmail API, respetan las reglas y solo corresponden a solicitudes en espera dentro de ejecuciones en curso.

Se conserva la protección que excluye referencias SIMULADO- del seguimiento real y de reservas de envío. Los reintentos de comunicaciones existentes son idempotentes. No se convierten ni reenvían simulaciones históricas al abrir la administración o al activar un transporte. Los estados históricos ERROR de procedimientos se conservan por compatibilidad; la prueba nueva ofrece la clasificación detallada anterior.

## Archivos principales

- Nuevos: PruebaGmailService, PruebaGmailStore, PruebaGmailTransport, PruebaGmailApiTransport, PruebaGmailEntity, PruebaGmailEventoEntity, PruebaGmailStoreJpa y PruebaGmailController.
- Reutilización y errores: MensajeCorreoMime, GoogleGmailHttp y GmailException.
- Angular: GmailAdminService; AdminGmailComponent, HTML, CSS y sus specs.
- QA: GmailOAuthHttpTest, GoogleGmailHttpTest y contratos Angular.
- SQL/helper: migración 10 y modo gmail-test-inspect del helper de solo lectura.

## Resultados QA

1.099 pruebas backend y 424 Angular aprobadas; compilación correcta. Incluyen Gmail API exitoso con mock, token vencido/renovación y errores OAuth, ausencia de conexión, transporte deshabilitado, destinatarios inválidos, confirmación, auditoría, concurrencia, reintentos, resultado incierto, revisión de recordatorios y conservación de procedimientos. La suite conserva las pruebas de JWT/STATELESS y permisos.

No se enviaron correos reales, no se cambió el entorno a REAL/GMAIL_API y no se modificaron tokens o ejecuciones reales. La autorización real permanece conectada. Persisten las advertencias previas del presupuesto del bundle y CSS.

Después de actualizar el backend local: salud directa y proxy HTTP 200, endpoint de prueba sin JWT HTTP 401, modo SIMULADO y Gmail real deshabilitado. La funcionalidad está disponible en la pantalla administrativa; no se invocó el POST de envío real.
