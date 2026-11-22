# WhatsApp — webhook HTTP local, etapa 1

Implementación local autorizada el 14 de noviembre de 2026. No se inicia un
servidor escuchando, no se configura infraestructura, no se conecta con Meta y no
se utiliza tráfico real. La validación HTTP se ejecuta con MockMvc. Sin commits,
despliegues, migraciones ni modificaciones de MySQL, Angular o núcleo OCR.

## Arquitectura

```text
HTTP local, exclusivamente loopback
        |
SecurityFilterChain específica /webhooks/whatsapp
  perfil QA + restricción IP local + métodos + CSRF de alcance acotado
        |
WebhookWhatsAppController
  lectura acotada / parámetros GET / traducción de errores HTTP
        |
ValidacionWebhookLocal + EntradaWebhookHttpAdapter
  token GET / HMAC sobre bytes / JSON / validación completa del lote
        |
ProcesarMensajeService existente (síncrono, estado temporal)
        |
        +--> BuscarSolucionesCanalPort --> BusquedaPublicaCanalAdapter
        |                                   --> ConsultarChatbotService existente
        |
        +--> ReconocerImagenPort --> OcrNodeProcesoAdapter existente
                                      --> puente QA + OCR compartido local
        |
Respuesta estructurada exclusivamente simulada en el HTTP local
```

Los nuevos componentes están íntegramente en infraestructura HTTP. El dominio,
el caso de uso, los puertos, el buscador, la simulación anterior y el núcleo OCR no
se modifican. Se reutilizan los filtros públicos del buscador y la proyección que
omite títulos internos. No se agregan dependencias npm/Maven, colas ni procesos
asíncronos. El OCR reutiliza el proceso local ya existente; no es una nueva cola
ni un nuevo motor de reconocimiento.

## Activación y aislamiento

Controlador y configuración requieren el perfil **qa-whatsapp-http-local** y se
excluyen cuando también está activo staging, prod o production. Por defecto no
se registran. Las solicitudes procedentes de direcciones distintas de loopback
se rechazan incluso con una firma correcta; no se confía en X-Forwarded-For.
Esto no autoriza un túnel/proxy: un proxy local podría presentar tráfico externo
como loopback. No publicar ni reenviar este endpoint.

Propiedades externas obligatorias, sin valores por defecto ni secretos nuevos
en archivos de aplicación:

| Propiedad | Uso |
| --- | --- |
| dimabug.whatsapp.http-local.verify-token | Verificación GET; 16–256 caracteres |
| dimabug.whatsapp.http-local.app-secret | Clave HMAC; 16–256 caracteres |
| dimabug.whatsapp.qa.raiz | Raíz local para el adaptador OCR QA existente |

Las pruebas suministran únicamente secretos e identificadores sintéticos. No se
inicia el backend con la configuración habitual, que podría seleccionar una base
externa. No se modifican application.properties ni perfiles existentes.

## GET /webhooks/whatsapp

Valida exactamente un valor por parámetro requerido:

- hub.mode debe ser subscribe.
- hub.verify_token debe coincidir con la configuración externa.
- hub.challenge debe existir, no estar vacío, no contener controles y tener hasta
  1024 caracteres.

Token comparado mediante MessageDigest.isEqual, sin normalizar ni recortar.
En caso correcto devuelve 200, text/plain UTF-8 y el challenge exacto: preserva
ceros iniciales, espacios y caracteres; no agrega comillas ni salto de línea.
Parámetros inválidos, ausentes o repetidos producen 403 con error genérico.
Las respuestas usan Cache-Control: no-store. No se registra el token.

## POST /webhooks/whatsapp

Acepta application/json. El controlador inspecciona Content-Length cuando existe
y lee como máximo **16 KiB + 1 byte**; rechaza exceso con 413 incluso sin longitud
declarada. No usa conversión automática de todo el cuerpo a un DTO sin límite.

Se exige una sola cabecera X-Hub-Signature-256 de formato sha256= y 64 dígitos
hexadecimales. Se calcula HmacSHA256 con el App Secret sobre los bytes originales
leídos y se comparan los 32 bytes con MessageDigest.isEqual. No se reserializa JSON,
no se modifica codificación ni espacios y no se usa el verify-token para firmar.
Firma inválida o ausente: 403. La firma se verifica antes del parser y del caso de
uso. Las cargas rechazadas nunca consultan conocimiento ni invocan OCR.

El parser rechaza claves duplicadas, JSON concatenado, profundidad superior a 20,
estructuras incompletas y límites de arrays. Valida el lote completo antes del
primer efecto. Admite hasta cinco entradas, cinco cambios por entrada y diez
notificaciones en total (mensajes, estados o eventos ignorados). Identificadores
acotados a 128 caracteres; tipo a 30; texto a 450 para permitir comandos con
referencia. El caso de uso conserva el límite de consulta de 300 caracteres.

| Evento | Comportamiento local |
| --- | --- |
| Texto | Traducción a MensajeCanal y caso de uso existente |
| Imagen PNG/JPEG/WebP | Referencia a ReconocerImagenPort; nunca descarga medios |
| Estado sent/delivered/read/failed/deleted | Conteo técnico, sin búsqueda ni OCR |
| Estado desconocido o campo futuro | Conteo de ignorados, sin búsqueda ni OCR |
| Tipo de mensaje no soportado | Respuesta NO_COMPATIBLE del caso de uso |
| Notificación de errors sin messages/statuses | Conteo de ignorados, sin procesar contenido |
| JSON/estructura inválidos | HTTP 400 genérico |

La forma conserva object=whatsapp_business_account,
entry[].changes[].value y metadata.phone_number_id. Los IDs recibidos pueden tener
forma de IDs de Meta; las pruebas usan exclusivamente valores ficticios.
No se conecta ni se certifica compatibilidad con todas las variantes de Meta.

Los medios numéricos sin fixture autorizado producen ERROR_OCR de forma
controlada: no se intentan descargar ni resolver en red. En QA la referencia
qa-media-legible permite recorrer el OCR Node real sobre el fixture existente.
No se altera su lista de medios permitidos, su límite de 5 MiB/12 MP/30 segundos
ni la extracción compartida. La simulación anterior permanece independiente.

Una recepción completada devuelve 200 con respuestas, estados e ignorados. Las
respuestas de error de negocio del caso de uso se incluyen en ese resultado local
(ERROR_OCR/ERROR_BUSCADOR/etc.). Una excepción inesperada del procesamiento se
traduce a 503 genérico; errores de lectura a 400. Ninguna excepción incluye secretos,
cuerpo del evento ni diagnóstico interno en la respuesta.

## Seguridad Spring

Se incorpora una cadena @Order(1) para la ruta exacta y únicamente bajo el perfil
QA. Solo GET y POST se permiten sin JWT; otros métodos se deniegan. Subrutas y la
variante con barra final no reciben la excepción. No se agrega el filtro JWT a
esta cadena. Las demás rutas continúan en la cadena de seguridad existente,
incluidos sus endpoints públicos preexistentes y permisos por rol.

**Estado preexistente importante:** SecurityConfig ya desactiva CSRF globalmente
en la cadena general. Esta tarea conserva ese archivo íntegramente; no introduce
otra desactivación global ni pretende corregir autenticación ajena al alcance.
La nueva cadena conserva CsrfFilter y omite su comprobación solo para el POST
exacto del webhook: es una llamada sin sesión/cookies autenticada con HMAC. No
excluye /webhooks/** ni otras rutas y no desactiva Spring Security.

No hay logs propios de cuerpos, imágenes, teléfonos, firmas, tokens o secretos.
Se prueba la ausencia de marcadores sensibles también con DispatcherServlet en
DEBUG y query string GET real, manteniendo el enmascaramiento de detalles de
Spring. No habilitar request-details/TRACE indiscriminadamente ni access logs con
query strings o captura de cuerpos. Esos sistemas externos no se configuran aquí.

## Ejecución y evidencias

Desde la raíz en PowerShell 7.4, usando las dependencias locales existentes:

```powershell
& ./qa/whatsapp/Run-PruebasHttpLocal.ps1
```

El lanzador usa Java 21, Maven offline, entorno limpio y el agente Mockito local.
MockMvc no abre un puerto HTTP. La regresión H2 usa bases efímeras independientes;
no conecta MySQL ni activa workers de correo/seguimiento. El OCR real usa los
recursos locales ya compilados del prototipo. No se ejecuta un servidor público.

Log: qa/runtime/whatsapp-http-local/maven-test.log.
Reportes JUnit XML: dima-bug-backend/target/surefire-reports.
Evidencia con resultados y SHA-256:
docs/07_Pruebas/evidencias/whatsapp-webhook-http-local-etapa1.json.
La línea base de protección está en qa/runtime/whatsapp-http-local/baseline.json.

## Riesgos conservados y etapa 2

**No preparado para producción. No conectar Meta ni usar tráfico real.**

- Procesamiento síncrono: el bloqueo global del caso de uso y un OCR de hasta
  35 segundos externos pueden retrasar acuses, bloquear usuarios y causar reintentos.
- Estado/deduplicación en memoria: se pierden al reiniciar y no se comparten entre
  instancias. Se conservan los límites y la limpieza perezosa de la etapa anterior.
- El caso de uso marca visto antes de procesar. Un fallo posterior puede impedir
  reprocesar el mismo ID; no se promete recuperación ni procesamiento exactamente
  una vez. Un fallo parcial de lote también puede dejar efectos previos.
- Las respuestas locales no se envían a WhatsApp. No hay descarga real de imágenes,
  estados de salida, plantillas, permisos Meta, recepción durable ni outbox.
- No se validan cuotas de memoria/CPU del sistema operativo, carga sostenida,
  HTTPS, TLS, proxies o tráfico de un servidor real.

La etapa 2 requiere autorización independiente para recepción durable, aceptación
atómica/deduplicación, procesamiento desacoplado, recuperación tras reinicios,
backpressure, límites por remitente y política de reintentos. Posteriormente se
requerirá aprobar publicación HTTPS limitada, secretos reales y conexiones a Meta.
No se incorpora ninguna de esas capacidades automáticamente.

## Jira

No se crea ni actualiza un ticket externo. Épica: Integración de canales de atención
DimaBug. Historia: Recepción segura de eventos WhatsApp mediante webhook.
Sprint según planificación vigente. Este informe cubre las subtareas autorizadas.

## Resultado final de pruebas

| Grupo | Pruebas correctas |
| --- | ---: |
| WebhookHttpLocalTest: HTTP, seguridad, parser, OCR real, logs | 33 |
| ValidacionWebhookLocalTest: vector HMAC independiente y límites | 3 |
| WebhookHttpPerfilTest: desactivación y exclusión de perfiles | 2 |
| Regresión previa de WhatsApp, OCR, búsqueda pública, materiales y seguridad | 86 |
| **Total, compilación incluida** | **124** |

Cero fallos, errores u omitidas en la ejecución final. Los 17 escenarios solicitados
están cubiertos: GET válido/incorrecto/incompleto/challenge exacto; POST firmado,
sin firma, firma incorrecta y cuerpo alterado; JSON inválido; texto, imagen,
estados y eventos desconocidos; límites; acceso sin JWT solo a la nueva ruta;
protección de rutas internas y ausencia de información sensible en logs.
Se añaden pruebas de bytes UTF-8/espacios sin reserialización, cabeceras repetidas,
lotes inválidos sin efectos parciales, duplicados, confirmación, filtro CSRF y
rechazo de acceso remoto aunque X-Forwarded-For señale loopback.

El primer intento dentro del aislamiento pasó las pruebas HTTP nuevas, pero falló
al iniciar los contextos H2 completos: el HttpClient de Java usado por una clase
preexistente de correo necesitaba loopback interno. La ejecución autorizada fuera
del aislamiento resolvió esa limitación y la suite final pasó íntegra, sin
conexiones a Meta/MySQL. Persisten avisos preexistentes de APIs obsoletas del correo;
no se modificaron para esta tarea. No se ejecutaron pruebas completas de Angular
ni de módulos backend ajenos. Tampoco se realizó una prueba de servidor TCP real.

## Archivos creados y estado de Git

No se modificó ningún archivo preexistente: la comparación SHA-256 contra la línea
base de 1366 archivos resultó idéntica. Incluye SecurityConfig, dominio/aplicación,
puertos existentes, Angular, configuraciones y OCR. Se crearon:

- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/infrastructure/http/EntradaWebhookHttpAdapter.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/infrastructure/http/ValidacionWebhookLocal.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/infrastructure/http/WebhookHttpLocalConfig.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/infrastructure/http/WebhookWhatsAppController.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/whatsapp/ValidacionWebhookLocalTest.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/whatsapp/WebhookHttpLocalTest.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/whatsapp/WebhookHttpPerfilTest.java`
- `qa/whatsapp/Run-PruebasHttpLocal.ps1`
- `docs/implementacion/WhatsApp_webhook_http_local_etapa1.md`
- `docs/07_Pruebas/evidencias/whatsapp-webhook-http-local-etapa1.json`

Git final: rama integracion-final-uat; 55 entradas modificadas previas y 70 entradas
sin seguimiento agrupadas por Git. Los archivos nuevos de esta etapa permanecen
sin seguimiento; no se agregaron al índice, no hay commits y no se cambió de rama.
El detalle se conserva en qa/runtime/whatsapp-http-local/git-after.txt. La cantidad
de entradas agrupadas no equivale al número de archivos individuales. Los reportes
y artefactos de compilación se generan en directorios de trabajo ignorados.
