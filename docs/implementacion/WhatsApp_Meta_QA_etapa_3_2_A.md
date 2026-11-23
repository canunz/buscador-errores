# Etapa 3.2-A — Preparación local para Meta

## Alcance y límites

El perfil nuevo `qa-whatsapp-meta` permite ejercitar localmente el contrato de
recepción de Meta con eventos sintéticos. No contiene cliente HTTP de Meta,
descargador de medios, access token, túnel ni adaptador de envío real.

La recepción exige `AlmacenWhatsAppPort` al arrancar. No existe alternativa en
memoria en este perfil: si no hay adaptador, tablas o claves, el arranque falla.
El ACK se construye únicamente después de retornar la transacción del lote.
Los perfiles anteriores mantienen sus contratos y sus límites originales.

No se modificó el esquema SQL ni se aplicaron migraciones a bases existentes.
Se reutilizan las tres tablas y el registro JPA XML existentes. El guardián
permite únicamente H2 `mem:wa_*` para pruebas o MySQL loopback con nombre
`wa_durable_<hexadecimal>` y `ddl-auto=validate`.

## Configuración

Activar solamente `qa-whatsapp-meta`; no mezclarlo con los perfiles WhatsApp
anteriores ni con staging/prod/production. El perfil fija `server.address` en
`127.0.0.1` y deshabilita el procesamiento de cabeceras Forwarded. El guardián
rechaza sobrescribir esas dos condiciones.

Configurar externamente, exclusivamente con valores sintéticos en esta etapa:

| Propiedad | Uso |
|---|---|
| `dimabug.whatsapp.meta-qa.waba` | Única WABA autorizada |
| `dimabug.whatsapp.meta-qa.numero` | Único Phone Number ID autorizado para esa WABA |
| `dimabug.whatsapp.meta-qa.participantes` | Alias explícitos y sus identidades canónicas de QA |
| `dimabug.whatsapp.meta-qa.verify-token` | Verificación GET local |
| `dimabug.whatsapp.meta-qa.app-secret` | Firma HMAC de los eventos sintéticos |
| `dimabug.whatsapp.durable.clave-cifrado` | Base64, 32 bytes |
| `dimabug.whatsapp.durable.clave-hmac` | Otros 32 bytes, diferentes |
| `dimabug.whatsapp.qa.raiz` | Recursos OCR locales ya instalados |
| `spring.datasource.*` | Base exclusiva de pruebas |

Ejemplo de participantes ficticios:

```properties
dimabug.whatsapp.meta-qa.participantes=tel:100000000000001=qa-a,bsuid:US.qa32a=qa-a
```

Los alias `tel:` y `bsuid:` del ejemplo son ficticios y pertenecen a la misma
identidad canónica. Así la conversación puede pasar de teléfono a BSUID sin
duplicarse. No se infiere equivalencia entre identificadores ni se vinculan con
cuentas internas de DimaBug. Cuando el evento contiene ambos identificadores,
ambos deben estar autorizados y mapear a la misma identidad. No hay comodines.

Las propiedades `dimabug.whatsapp.meta-qa.envio-real` y
`dimabug.whatsapp.meta-qa.conexion-meta` deben permanecer falsas. El guardián
rechaza un intento de activarlas. No se solicitaron ni utilizaron credenciales
reales y no se cambió ninguna configuración de Meta.

## Contrato de recepción

Ruta local: GET/POST `/webhooks/whatsapp`. La cadena de seguridad se comparte
con el webhook HTTP anterior: solo ruta exacta, métodos permitidos, loopback,
sin JWT para el webhook y sin confiar en X-Forwarded-For del solicitante.

GET conserva `hub.mode`, `hub.verify_token` y el challenge exacto como texto.
POST verifica una única firma HMAC-SHA256 sobre el cuerpo original, antes del
parser, de los controles de alcance y de cualquier efecto persistente.

El normalizador del perfil nuevo admite:

- Texto con `from`, BSUID en `from_user_id`, o ambos según la lista autorizada.
- Estados con `recipient_id`, `recipient_user_id`, o ambos, con la misma política.
- Marcas de tiempo como cadena decimal o número entero, con validación de fecha.
- Imágenes PNG/JPEG/WebP como referencia de medio; nunca descarga la URL recibida.
- Mensajes no soportados como `NO_COMPATIBLE`, sin interpretar contenido adjunto.
- Lotes mixtos de mensajes y estados en una única transacción.

Se limita a 3 MiB de cuerpo, profundidad JSON 20 y 1.000 notificaciones por lote.
Se rechazan claves JSON duplicadas y contenido JSON adicional. Estos son límites
explícitos de esta preparación local, no una promesa de aceptar cualquier evento
o extensión futura del proveedor. Deben probarse carga y latencia antes de exponer
un callback. Los textos de transporte admiten hasta 4.096 caracteres; las consultas
siguen limitadas a 300 por las reglas de aplicación. Un texto largo se recibe
durablemente y obtiene una respuesta de validación, sin ejecutar una búsqueda.

WABA, número receptor y participantes se comprueban en todo el lote antes de llamar
al almacén. Un fallo de alcance rechaza el lote sin persistencia parcial. Los
conflictos durante la persistencia revierten conjuntamente mensajes y estados.

Respuestas: 200 tras commit, 403 para firma/alcance inválidos, 400 para formato
inválido, 413 para tamaño y 503 para pausa o fallo del almacén. Los cuerpos de
error son genéricos. Los campos ajenos a `messages` y los avisos `errors` sin
mensajes se contabilizan como ignorados; no se conservan sus cuerpos.

## Estados de entrega

Se almacenan como eventos `ESTADO`, sin conversación ni secuencia, con estado
terminal `IGNORADO` y código `ESTADO_SIN_CORRELACION`. La clave HMAC incluye
cuenta, canal, identificador del mensaje del proveedor, estado y fecha. La carga
normalizada mínima queda cifrada hasta su vencimiento de 24 horas; el registro
de deduplicación se retiene 30 días. No se conservan perfiles, precios ni errores
de texto del proveedor. La limpieza contempla explícitamente conversación nula.

No entran al trabajador de consultas, no ejecutan OCR y no crean respuestas.
La correlación con envíos y la aplicación de estados a una respuesta permanecen
pendientes: esta etapa no envía mensajes reales. `ACEPTADA`, entrega y lectura
no se consideran demostradas por conservar una notificación sintética.

## Cuatro interruptores independientes

Todos son falsos al arrancar:

| Sufijo de `dimabug.whatsapp.meta-qa.` | Comportamiento |
|---|---|
| `recepcion` | Permite persistir POST; apagado devuelve 503 con firma válida. GET sigue disponible. |
| `procesamiento` | Permite reservar nuevas consultas. No activa por sí mismo medios o salida. |
| `medios` | Permite reservar imágenes si también está habilitado procesamiento. Solo OCR local de fixtures. |
| `envio` | Permite consumir respuestas mediante la salida **SIMULADA** existente. Nunca realiza un envío real. |

Se configuran al arrancar y pueden cambiarse independientemente mediante la API
Java local de `ControlesMetaQa`. No se añadió endpoint HTTP de administración,
JMX remoto, interfaz pública ni modificación de autenticación. El estado de los
controles es local y no se persiste: al reiniciar se utiliza la configuración.

El mantenimiento de reservas y retención continúa aunque todos estén apagados.
En Meta QA, la propiedad histórica `durable.trabajadores=false` no elimina este
mantenimiento: para pausar se usan los cuatro controles nuevos.

Las operaciones ya iniciadas pueden terminar dentro de sus límites. Pausar no
congela los vencimientos: texto 24 horas, imagen/propuesta 15 minutos. Las filas
permanecen y luego caducan según esa política. Una imagen pausada conserva el
orden y bloquea los mensajes posteriores de su propia conversación, no otras.
Si se pausa la salida durante la preparación, su reserva se recupera sin marcarla
como simulada. No se promete una pausa instantánea de una operación ya iniciada.

El seguimiento de tareas activas distingue evento, token y cercado, para que un
trabajador antiguo no retire el seguimiento de una reserva posterior del mismo
evento.

## Métricas y registros

`ObservabilidadMetaQa.instantanea()` ofrece una instantánea Java inmutable de
contadores acumulados: aceptados, duplicados, estados observados, ignorados,
rechazos por firma/tamaño/formato/alcance, pausas, fallos del almacén, ejecución,
recuperación, preparación de salida, saturación y respuestas simuladas. También
incluye solicitudes y duración acumulada en nanosegundos del adaptador.

Las etiquetas son enums cerrados. Los logs contienen únicamente
`wa_meta_qa codigo=<CODIGO>`: no contienen excepciones, cuerpos, identificadores,
teléfonos, firma, tokens, claves ni URLs. Los errores se muestrean en potencias
de dos para evitar una línea por cada rechazo; las operaciones de control se
registran siempre. Los contadores no se muestrean y se reinician con la JVM.
No se añadió un endpoint público de métricas ni exportador externo.

Los estados observados cuentan notificaciones, incluidas reentregas; los
contadores de aceptados y duplicados se obtienen del resultado durable. Los
intentos que ni llegan al adaptador —por ejemplo un método denegado por la cadena
de seguridad— no forman parte de su contador de solicitudes.

## Archivos y componentes

- Nuevo paquete `whatsapp/infrastructure/meta`: perfil, política de activos y
  participantes, normalizador, controlador, controles y observabilidad.
- Nuevo perfil `application-qa-whatsapp-meta.properties`.
- Dominio/puertos: `EstadoEntregaCanal`, `ControlProcesamientoWhatsAppPort` y
  `AlmacenWhatsAppPort.recibirLote`.
- Persistencia: perfil durable compartido, recepción atómica de estados y
  mantenimiento compatible con eventos sin conversación; sin cambios SQL.
- Infraestructura HTTP: reutilización de HMAC con límite configurable y de la
  cadena loopback. El modo HTTP previo conserva su límite de 16 KiB.
- Trabajadores: controles independientes, métricas y seguimiento por reserva.
- Pruebas nuevas: normalización Meta, integración HTTP/JPA, configuración segura
  y controles de trabajadores; ampliación de las pruebas de persistencia.

Se reutilizan `ProcesarMensajeService`, extracción OCR compartida,
`ReconocerImagenPort`, preparación de respuesta pública y búsqueda autorizada.
No se cambió el núcleo OCR, el frontend, el buscador ni sus reglas de publicación.

## Pendiente de autorización posterior

Descarga de medios reales, destino/correlación para respuestas reales, estados
del proveedor aplicados a envíos, cliente Cloud API, ventanas de atención,
plantillas, publicación HTTPS, carga y monitoreo externo. Ninguno se habilita
mediante esta entrega ni mediante el interruptor de salida simulada.
