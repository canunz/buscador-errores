# WhatsApp: persistencia y procesamiento durable (QA local)

## Alcance

Se integra el webhook HTTP local con las tres tablas del SQL validado en
`qa/whatsapp/mysql-stage22a/schema.sql`. No se modifica ese esquema ni se aplican
migraciones a bases existentes. Se conserva la modalidad síncrona de QA cuando
no está activo el perfil durable. No hay conexión con Meta ni salida de mensajes
reales: la salida termina en `SIMULADA` y elimina su contenido.

### Arquitectura

- Dominio: `MensajeCanal`, `EstadoConversacion`, `RespuestaCanal`, sin frameworks.
- Aplicación: `AlmacenWhatsAppPort` describe recepción atómica, reservas,
  finalización conjunta y outbox. `ProcesarReservaWhatsAppService` invoca las
  reglas existentes de `ProcesarMensajeService`, restaurando/exportando una
  instantánea. No copia el extractor OCR ni el normalizador del buscador.
- Infraestructura: entidades y repositorios JPA de conversación, evento y
  respuesta; `AlmacenWhatsAppJpaAdapter` controla transacciones cortas mediante
  `TransactionTemplate`. OCR y búsqueda se ejecutan fuera de esas transacciones.
- Registro JPA explícito mediante `META-INF/whatsapp.orm.xml`. Las entidades no
  tienen `@Entity`: así las configuraciones existentes no empiezan a validar o
  crear las nuevas tablas. Sus anotaciones de columnas se aplican al registrarlas
  desde XML. Los repositorios y adaptadores también requieren el perfil durable.

## Flujo

1. Se conserva GET de verificación, HMAC del cuerpo original, límites, validación
   completa del lote, cadena de seguridad y restricción loopback existentes.
2. El adaptador traduce mensajes y conserva su fecha de emisión. En modo durable
   confirma una única transacción para todo el lote antes de devolver 200. El ACK
   contiene contadores y una lista de respuestas vacía; no espera OCR/búsqueda.
3. La identidad del evento utiliza HMAC con ámbito de cuenta/canal. Un duplicado
   idéntico no crea otra fila. Mismo ID con contenido diferente se rechaza sin
   sobrescribir; el lote se revierte. Se acotan reintentos de colisiones/deadlocks.
4. Un trabajador reserva la cabecera de una conversación mediante bloqueo JPA
   pesimista y libera la transacción. Conversación y evento comparten token; el
   evento incrementa su contador de cercado. Otro trabajador no avanza esa misma
   conversación mientras la reserva está vigente.
5. Se ejecutan las reglas OCR/confirmación/corrección/cancelación existentes. Una
   instancia nueva del procesador restaura la propuesta cifrada. La finalización
   guarda conversación, resultado del evento y una única respuesta conjuntamente.
   Token, cercado y plazo impiden confirmar resultados de reservas antiguas.
6. La preparación relee cada solución con `AutoservicioPublicoService.publica`;
   mantiene publicado/activo/autoservicio y proyecta solo ID, título de solución
   e instrucciones. Una revocación omite esa solución. No adjunta materiales ni
   títulos/descripciones internos. Los fallos técnicos permiten reintentar.
7. La salida se registra como simulada. No existe cliente de envío ni descarga
   de medios de Meta. El OCR Node local sigue aceptando exclusivamente fixtures QA.

Los estados de entrega recibidos se validan/contabilizan como en la etapa HTTP;
no se persisten ni se correlacionan con proveedor en esta entrega sin envío real.

## Recursos y recuperación

- Dos tareas de texto, una OCR y una preparación; ejecutores sin cola de espera.
- Sondeo 1 s; reserva 120 s; renovación 10 s; límite de procesamiento 45 s;
  recuperación cada 15 s. Se conservan los límites del OCR existente.
- Reintento con espera 5, 15, 45, 135, hasta 300 s y variación 0–3 s. Máximo
  cinco intentos; tiempo agotado, dos. La recuperación respeta la cabecera de cada
  conversación. Un fallo no bloquea conversaciones diferentes.
- Las reservas vencidas se recuperan. `ENVIANDO` vencido queda `INCIERTA`, nunca
  se reenvía automáticamente. El modo actual no genera estados `ENVIANDO`.
- La salida respeta el orden por conversación, incluidos los reintentos. Una
  salida incierta requiere resolución antes de avanzar esa conversación.
- Un contenido cifrado ilegible también consume intentos: no revierte el contador
  ni bloquea indefinidamente la cabecera. El evento se conserva cifrado hasta su
  vencimiento para permitir corregir una clave mal configurada; no se registra el
  contenido ni la excepción criptográfica. El plan de respuesta fallido se elimina
  al agotar los cinco intentos.
- Imagen: vigencia 15 minutos. Texto: 24 horas. Propuesta: 15 minutos. Fechas
  antiguas no disparan OCR/búsqueda y fechas futuras >5 minutos se rechazan.
- Una nueva imagen invalida propuestas anteriores y resultados OCR pendientes;
  se descartan imágenes anteriores recibidas fuera de orden por fecha de emisión.
- Límite por conversación: 100 mensajes por minuto. Lotes hasta 10 mensajes y
  cuerpo HTTP hasta 16 KiB. El contenido cifrado tiene tamaño máximo acotado.
- El reloj operativo se obtiene de la BD como epoch, evitando interpretar
  `DATETIME` usando la zona horaria de la JVM. JDBC debe operar en UTC.
- Al terminar se elimina el contenido del evento; al consumir/caducar una
  propuesta se elimina su estado. La simulación elimina el plan de respuesta.
  El mantenimiento retira cargas caducadas y purga registros terminales después
  de 30 días, en lotes de hasta 100 y respetando las FK (respuesta antes de evento).
  No se borran eventos pendientes para liberar espacio.

No se promete ejecución física exactamente una vez: un proceso que perdió su
reserva puede seguir calculando si su dependencia ignora interrupciones. El
cercado impide que confirme. Los ejecutores limitan los cálculos simultáneos.

La inspección operativa se realiza mediante estados, contadores y códigos seguros
de las tablas (`ultimo_error_codigo`, intentos, reservas y fechas). No se añade un
endpoint público de administración ni se escriben cuerpos o credenciales en logs.

## Configuración explícita

Activar conjuntamente `qa-whatsapp-http-local,qa-whatsapp-durable`. El guardián
rechaza staging/prod/production, MySQL fuera de `127.0.0.1`, bases que no sean
`wa_durable_<identificador hexadecimal>` y DDL distinto de `validate`. H2 se
admite exclusivamente en memoria con nombre `wa_*` para pruebas.

Suministrar externamente:

- URL/usuario/contraseña del MySQL exclusivo, usuario con SELECT/INSERT/UPDATE/DELETE.
- `dimabug.whatsapp.http-local.verify-token` y `.app-secret` sintéticos de QA.
- `dimabug.whatsapp.qa.raiz`: raíz del repositorio para el OCR existente.
- `dimabug.whatsapp.durable.clave-cifrado`: Base64 de 32 bytes aleatorios.
- `dimabug.whatsapp.durable.clave-hmac`: otros 32 bytes aleatorios diferentes.

No hay claves predeterminadas en el perfil. Deben mantenerse entre reinicios;
perderlas impide leer propuestas/identificar duplicados. AES-256-GCM usa nonce
aleatorio y contexto autenticado por registro; la huella HMAC usa otra clave y
campos delimitados por longitud. El sobre lleva versión 1. La rotación con varias
versiones de claves es trabajo posterior; no reemplazar claves con eventos vivos.

`dimabug.whatsapp.durable.trabajadores=false` permite revisar persistencia sin
activar trabajadores. No cambiar los perfiles de entornos existentes. Las pruebas
integrales usan DDL efímero H2; esa excepción no se permite para MySQL.

## Validación y siguientes etapas

`qa/whatsapp/Run-PruebasDurables.ps1` ejecuta JUnit offline con ambiente limpio y
agente Mockito local. Admite selección de pruebas y carpeta de evidencias.
`qa/whatsapp/Run-PruebasPersistenciaMySql.py` utiliza la imagen MySQL local fijada,
crea solo tres tablas, usa credenciales sintéticas por fichero con ACL restringida,
usuario DML y puerto exclusivamente loopback. Ejecuta JPA, termina la JVM con una
reserva pendiente, reinicia MySQL y comprueba la recuperación desde otra JVM.
Detiene su contenedor al terminar y conserva el volumen/evidencias. Se puede
reanudar solo una identidad QA creada por el propio lanzador pasando su sufijo.

Antes de habilitar otra base o Meta se requieren aprobación de migración, gestión
de claves/rotación, capacidad operativa y alarmas, descarga segura de medios,
correlación durable de estados de entrega y un adaptador de envío con tratamiento
explícito de resultados inciertos. Esta entrega no autoriza despliegue ni publica
endpoints adicionales. No incorpora dependencias Maven ni servicios nuevos.
