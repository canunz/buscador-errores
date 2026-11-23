# WhatsApp simulado — etapa 1

Implementación local del 13 de noviembre de 2026. Sin conexión a Meta, tokens,
mensajes reales, endpoints nuevos, migraciones, commits ni despliegue.
No se modifica Angular, autenticación, MySQL ni contratos públicos existentes.

## Inspección y decisiones

Se revisó Git antes de trabajar: existían numerosos cambios locales de búsqueda,
OCR, interfaz, correo y autenticación. Se conservaron. Esta etapa añade archivos
bajo el módulo backend `whatsapp`, sus pruebas y `qa/whatsapp`; no modifica fuentes
preexistentes. Se revisaron los servicios ConsultarChatbotService,
BuscarConocimientoService, ConsultaError, el adaptador JDBC de autoservicio,
AutoservicioPublicoController y las pruebas de búsqueda y privacidad.

El backend organiza módulos en domain/application/infrastructure. Se conserva
esa organización; no se agrega un framework ni arquitectura alternativa.
El dominio nuevo solo usa Java. Los adaptadores dependen de puertos de aplicación.
No hay referencias a objetos Meta o Node dentro del dominio.

Se evaluaron tres comunicaciones con OCR: HTTP privado, proceso local y ejecución
en navegador. Para esta etapa se elige **proceso local y pipes**, porque no requiere
infraestructura ni puertos, permite terminar el árbol de procesos y reutiliza el
prototipo existente. HTTP, contenedores y colas quedan fuera del alcance.

## Flujo

```text
JSON QA con estructura de webhook Cloud API
        |
WebhookSimuladoAdapter (validación completa del lote)
        |
ProcesarMensajeService (duplicados / estado / confirmación)
        |                                  |
        | imagen                           | texto o confirmación
        v                                  v
ReconocerImagenPort                 BuscarSolucionesCanalPort
        |                                  |
OcrNodeProcesoAdapter               BusquedaPublicaCanalAdapter
        |                                  |
pipe a qa/whatsapp/ocr-bridge.cjs    ConsultarChatbotService existente
        |                                  |
reconocerBytes + extractor          filtros / ranking existentes
compartido existentes                       |
        |                                  |
propuesta sin búsqueda --------confirmación-+
                                           |
                                RespuestaCanal estructurada
                                           |
                                SalidaSimuladaAdapter
                                JSON solo en memoria, sin envío
```

La búsqueda devuelve exclusivamente ID de solución, título e instrucciones
públicas. Omite título/ID del conocimiento, síntomas, campos internos y materiales.
No se duplica normalización/ranking: el servicio existente aplica ConsultaError.
No se usa el endpoint autenticado ni se otorgan roles. Se preservan las condiciones
PUBLICADO, solución activa y autoservicio del mecanismo público actual.

## Entrada y salida QA

`WebhookSimuladoAdapter.recibir(byte[])` se invoca desde Java/JUnit. **No es un
controlador HTTP**. Solo se registra como bean mediante el perfil explícito
`qa-whatsapp-simulado`, que requiere `dimabug.whatsapp.qa.raiz` (ruta local de este
checkout). No está disponible por defecto. No se modifica SecurityConfig.
El perfil no configura una base de datos: para ejecutar la simulación usar las
pruebas H2 incluidas, no iniciar el backend contra una base existente.

Se conserva la envoltura representativa `object`, `entry[].id`,
`changes[].field`, `value.messaging_product`, `metadata.phone_number_id` y
`messages[]` (`from`, `id`, `timestamp`, `type`, `text.body` o `image.id/mime_type`).
Referencia oficial de estructura:
https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/components
(HTTP 429 durante la consulta). Se contrastó además el recorrido de eventos del
ejemplo oficial: https://github.com/WhatsApp/WhatsApp-Flows-Tools/blob/main/articles/creating-surveys/main.py .
La forma usada es un subconjunto de simulación, no una certificación de compatibilidad
con todas las variantes actuales de Meta.

Los IDs son deliberadamente ficticios y no son valores aceptables para Meta:
`qa-cuenta-*`, `qa-canal-*`, `qa-usuario-*`, `wamid.qa-*`, `qa-media-*`.
No se aceptan teléfonos como remitentes. Los fixtures JSON están en
`qa/whatsapp/fixtures`; repetir el mismo evento simula un duplicado.

Se limita cada carga a 16 KiB y diez mensajes; se valida todo el lote antes de
procesar el primero. Se rechazan JSON malformado, claves duplicadas, contenido
posterior al JSON, estructuras incompletas, tamaños excesivos y MIME no admitido
por esta simulación. Tipos distintos de texto/imagen producen NO_COMPATIBLE.
No se descarga ningún medio a partir del payload.

La salida incluye estado, mensaje, referencia, soluciones públicas, candidatos y
advertencias. El objeto se devuelve directamente; SalidaSimuladaAdapter permite
serializarlo a bytes **sin escribir archivos ni logs**. Los toString de mensaje y
respuesta omiten su contenido. No se proporciona ningún adaptador de envío real.

## OCR y conversación

`ocr-bridge.cjs` carga el adaptador compilado existente desde
`frontend-dimabug/tools/ocr-node/dist`. No instala ni duplica Tesseract, Sharp,
modelos o algoritmos. Solo admite cuatro fixtures sintéticos ya autorizados y
verificados por hash. Añade un caso corrupto generado en memoria. No lee URLs,
archivos arbitrarios, nombres recibidos ni credenciales.

Se preservan los controles existentes de 5 MiB, 12 MP, formatos, preparación a
2400 px, OCR español/inglés, límite de texto y plazo interno de 30 segundos.
El puente QA admite referencias PNG; el prototipo existente conserva JPEG/WebP.
Spring impone 35 segundos al proceso, descarta stderr y limita la respuesta a
64 KiB. Los pipes de stdout son un protocolo privado capturado en memoria, no logs.
El entorno del proceso hijo contiene únicamente variables mínimas de Windows y
temporales locales. El preload existente bloquea APIs de red de Node. El proceso
y sus descendientes se terminan al finalizar, fallar o vencer el plazo.

Toda imagen produce revisión humana, incluso cuando parece legible. Hasta tres
candidatos mantienen las advertencias del extractor. Sin candidatos se permite
corregir manualmente. No se modifica O/0 ni l/1 y no se inventan soluciones.

Comandos de simulación (la referencia aparece en la respuesta):

- `CONFIRMAR <referencia>`: consulta la propuesta si tiene 1–300 caracteres.
- `CORREGIR <referencia> <texto>`: sustituye la propuesta; exige confirmar de nuevo.
- `ELEGIR <referencia> <1-3>`: selecciona un candidato; exige confirmar de nuevo.
- `CANCELAR <referencia>`: elimina la propuesta sin consultar.

Cada corrección/selección genera referencia nueva; una imagen nueva invalida la
anterior. Texto suelto durante una propuesta no confirma ni busca. Las propuestas
caducan a los quince minutos, sin renovar plazo al corregir. Las conversaciones se
identifican por cuenta, canal y usuario. La sincronización evita carreras y limita
a un trabajo OCR activo por instancia. No hay cola de servicio HTTP.

Máximos: 100 propuestas y 1000 IDs de mensajes durante una hora. La limpieza es
perezosa al procesar eventos; la caducidad invalida uso, no promete borrado físico
exactamente al minuto quince en una instancia inactiva. Se rechaza saturación sin
desalojar IDs aún vigentes. Reiniciar pierde el estado y la deduplicación.
Un duplicado devuelve DUPLICADO sin repetir búsqueda ni OCR; los reintentos tras
fallo deben llevar un ID nuevo. Un fallo del buscador conserva la propuesta hasta
su vencimiento; los errores internos no se incluyen en respuestas.

## Ejecución

Requisitos locales ya existentes: Java 21, Maven 3.9.16 y dependencias en caché,
Node portable 22 y prototipo OCR compilado. No se descargan dependencias nuevas.
Desde la raíz, en PowerShell 7.4:

```powershell
# Preparar/revalidar el prototipo existente, si fuera necesario:
& ./frontend-dimabug/tools/ocr-node/run.ps1 -Mode Build
& ./frontend-dimabug/tools/ocr-node/run.ps1 -Mode Test
# Pruebas Java de simulación, H2 y regresión pública:
& ./qa/whatsapp/Run-Pruebas.ps1
```

El lanzador limpia el entorno, usa Maven offline y carga el agente Mockito local
explícitamente para evitar auto-attach bloqueado en este entorno. No inicia una
API escuchando en red. Las bases H2 se crean y eliminan dentro de las pruebas.
Las pruebas no imprimen imágenes, consultas ni respuestas OCR de usuarios.
La evidencia técnica queda en `qa/runtime/whatsapp-simulado/maven-test.log` y
los XML de Surefire; no contiene datos reales. Resumen verificable en
`docs/07_Pruebas/evidencias/whatsapp-simulado-etapa1.json`.

## Límites y siguiente etapa

No se valida firma de Meta porque no existe webhook real. No se prueban recepción
externa, descarga de medios, envío, acuses, ventana de conversación ni plantillas.
La forma JSON es representativa; los IDs QA y comandos no constituyen un contrato
listo para producción. El bloqueo empresarial 131031 no se intenta resolver.

Antes de conectar Meta se requieren autorización independiente, resolver el estado
de la cuenta, validar firma/autenticidad, gestión de secretos y permisos mínimos,
medios autenticados, políticas de retención del proveedor, colas/estado durable,
backpressure y límites por usuario, recuperación tras reinicios y tratamiento de
envíos inciertos. La memoria no garantiza deduplicación durable ni exactamente una
vez. La búsqueda es síncrona y usa los plazos propios del backend; no se añade un
nuevo timeout de JDBC. No hay límite de memoria/CPU del sistema operativo: sería
necesario aislar y medir el proceso antes de producción. Windows es el único
entorno de proceso probado; falta carga sostenida y validación en otros sistemas.

No se ha registrado Jira. Épica: Integración de canales de atención DimaBug;
historia: Integración simulada de WhatsApp con la Base de Conocimiento;
sprint por definir. No se inventa un identificador de ticket.

## Resultados finales

| Grupo | Resultado ejecutado |
| --- | --- |
| WhatsApp: estado, entrada, arquitectura, OCR real y búsqueda H2 | 42/42 correctas |
| Regresión de búsqueda pública, visibilidad, materiales y seguridad | 44/44 correctas |
| Total Maven (compilación incluida) | 86/86, sin fallos, errores ni omitidas |
| Prototipo OCR Node existente | 31/31, sin fallos ni omitidas |

Los casos cubren los veinte escenarios solicitados: consultas válidas/vacías/sin
resultados, contenido restringido/borrador, imagen válida/corrupta/vacía/ambigua,
confirmación/corrección/cancelación, duplicados, eventos incompletos y no
compatibles, concurrencia, expiración, errores de OCR/buscador y timeout real.
También se comprueban selección de candidatos, referencias antiguas, saturación,
privacidad de salida, claves JSON duplicadas y rechazo de JSON concatenado.

Durante desarrollo hubo un error de importación de Jackson 3 y dos errores en la
preparación de pruebas; se corrigieron y la suite final pasó completa. Los primeros
intentos dentro del aislamiento fallaron por auto-attach de Mockito y por loopback
interno del HttpClient de Java. Se usó agente explícito y ejecución autorizada fuera
del aislamiento; no se habilitaron conexiones externas de la simulación.
Node conserva el aviso ExperimentalWarning de MockTimers. Maven muestra avisos
preexistentes de APIs obsoletas/no comprobadas del módulo correo.

No se ejecutan suites completas de Angular ni de otros módulos backend, porque no
se modifican. No quedan pruebas ejecutadas fallidas en la validación final.

## Inventario de archivos creados

No se modifican archivos fuente preexistentes en esta etapa. Se crean:

- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/application/port/out/BuscarSolucionesCanalPort.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/application/port/out/ReconocerImagenPort.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/application/service/ProcesarMensajeService.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/domain/MensajeCanal.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/domain/RespuestaCanal.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/infrastructure/busqueda/BusquedaPublicaCanalAdapter.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/infrastructure/ocr/OcrNodeProcesoAdapter.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/infrastructure/simulado/SalidaSimuladaAdapter.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/infrastructure/simulado/WebhookSimuladoAdapter.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/whatsapp/infrastructure/simulado/WhatsAppQaConfig.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/whatsapp/BusquedaPublicaCanalTest.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/whatsapp/OcrNodeProcesoTest.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/whatsapp/WebhookSimuladoTest.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/whatsapp/WhatsAppArquitecturaTest.java`
- `qa/whatsapp/fixtures/imagen.json`
- `qa/whatsapp/fixtures/incompleto.json`
- `qa/whatsapp/fixtures/no-compatible.json`
- `qa/whatsapp/fixtures/texto.json`
- `qa/whatsapp/ocr-bridge.cjs`
- `qa/whatsapp/Run-Pruebas.ps1`
- `docs/implementacion/WhatsApp_simulado_etapa1.md`
- `docs/07_Pruebas/evidencias/whatsapp-simulado-etapa1.json`

No se crea una instalación adicional de Node, dependencias npm/Maven ni modelos.
Los builds y reportes generados permanecen en directorios de trabajo ignorados.
