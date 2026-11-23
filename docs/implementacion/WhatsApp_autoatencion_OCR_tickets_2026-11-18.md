# WhatsApp — Autoatención con OCR y tickets DimaBug (18-11-2026)

## Flujo implementado

```
Usuario: Hola
DimaBug: ¡Hola! 👋 Soy DimaBug, el asistente de Soporte TI de Dimarsa.
         ¿En qué puedo ayudarte? Puedes describir el problema o enviarme una foto del error 📷.
Usuario: [foto del error del POS]
DimaBug: Revisé tu foto y detecté este error: _Error POS-1043: sin conexión con caja_
         ✅ Solución sugerida: Reiniciar el POS de venta
         1. Cierra la aplicación de caja. 2. Apaga y enciende el POS.
         ¿Se solucionó tu problema? Responde SÍ o NO.
Usuario: No
DimaBug: Lamento que la solución no haya funcionado.
         🎫 Levanté el ticket TK-2026-0123 en DimaBug. Soporte TI revisará tu caso y te contactará.
```

| Situación | Respuesta | Ticket |
|---|---|---|
| Saludo (hola, buenas tardes, menú…) | "¿En qué puedo ayudarte?" | No |
| Foto legible o texto con solución | Solución + "¿Se solucionó? SÍ/NO" (vigente 30 min) | No |
| Responde SÍ | Cierre amable | No |
| Responde NO | Confirmación con código | **Sí**, con el error leído y las soluciones que no funcionaron |
| Sin solución en la base de conocimiento | Aviso con código | **Sí**, de inmediato |
| Foto ilegible | Pide foto más nítida o descripción | No |
| Audio, documento, etc. | Pide texto o foto | No |

La solución sale de la base de conocimiento pública (misma búsqueda del chatbot). Para que el ejemplo
del POS funcione debe existir un conocimiento **público** cuyo error/palabras clave coincidan con el texto
que muestra la pantalla del POS y cuya solución sea "Reiniciar el POS de venta".

## Componentes

| Capa | Archivo | Rol |
|---|---|---|
| Aplicación | `AutoatencionWhatsAppService` | Reglas de la conversación (saludo, SÍ/NO, ticket) |
| Aplicación | `CrearTicketCanalPort`, `EnviarMensajeCanalPort`, `ConversacionCanal` | Puertos nuevos |
| Infraestructura | `GraphMetaCliente` | Descarga de fotos y envío de texto por WhatsApp Cloud API |
| Infraestructura | `ReconocerImagenMetaAdapter` + `OcrNodeProcesoAdapter.reconocerBytes` | Foto real → OCR local (por stdin) |
| Infraestructura | `EnvioMetaGraphAdapter`, `FormatoWhatsApp` | Respuesta real solo a participantes autorizados |
| Infraestructura | `TicketWhatsAppAdapter` | Usa `CrearTicketUseCase` existente |
| QA | `qa/whatsapp/ocr-bridge.cjs` | Nuevo modo `--stdin` (PNG/JPEG/WEBP, máx. 5 MB) |

El flujo QA anterior (CONFIRMAR/CORREGIR/ELEGIR) se mantiene intacto cuando la autoatención está desactivada.
No hay cambios de esquema MySQL: el envío real usa los estados ya previstos `ACEPTADA` y `proveedor_clave_hmac`.

## Activación (todas las banderas están en `false` por defecto)

Orden recomendado, coherente con `falta.md`: **primero confirmar la recepción real** (objetivos 1–3).

```properties
dimabug.whatsapp.meta-qa.recepcion=true
dimabug.whatsapp.meta-qa.procesamiento=true
dimabug.whatsapp.meta-qa.medios=true          # procesa fotos
dimabug.whatsapp.meta-qa.envio=true           # despacha respuestas
dimabug.whatsapp.autoatencion.habilitada=true
dimabug.whatsapp.autoatencion.ticket-actor-email=<usuario de servicio Soporte>
dimabug.whatsapp.meta-qa.conexion-meta=true   # descarga fotos reales desde Meta
dimabug.whatsapp.meta-qa.envio-real=true      # responde realmente por WhatsApp
dimabug.whatsapp.meta-qa.graph-token=<token>  # externo, nunca en Git
dimabug.whatsapp.meta-qa.graph-version=v23.0
```

Requisitos:

1. **Token Graph** de usuario del sistema con permiso `whatsapp_business_messaging`, asociado a la WABA QA.
2. **Usuario de servicio** activo en DimaBug con rol Soporte (no Administrador ni acceso básico); será el
   solicitante registrado del ticket.
3. **Participantes**: el envío real solo responde a alias con entrada `tel:` en
   `dimabug.whatsapp.meta-qa.participantes` (por ejemplo `tel:569XXXXXXXX=qa-carolina`).
4. **Ventana de 24 h** de WhatsApp: las respuestas son de texto libre porque siempre contestan a un
   mensaje del usuario.

El guardián de arranque (`WhatsAppDurablePerfilGuard`) rechaza `conexion-meta` o `envio-real` si falta
alguno de: autoatención habilitada, `graph-token` o `ticket-actor-email`.

## Pruebas

* `AutoatencionWhatsAppTest` (12): saludo, foto → solución, SÍ, NO → ticket, sin solución → ticket,
  foto ilegible, reintento de ticket, vencimiento, consulta nueva, tipo no compatible.
* `GraphMetaClienteTest` (5): envío, errores 401/429/503, origen del medio, configuración, lista de participantes.
* `MetaQaConfiguracionTest.envioRealSoloConAutoatencionAutorizada`.

Ejecutar: `cd dima-bug-backend && .\mvnw.cmd test -Dtest="AutoatencionWhatsAppTest,GraphMetaClienteTest,MetaQaConfiguracionTest,WebhookSimuladoTest,WhatsAppArquitecturaTest"`

## Riesgos conocidos

* Duplicación de tickets por reintento: **controlada** (18-11-2026). `TicketWhatsAppAdapter` usa el identificador del mensaje
  de WhatsApp como clave de idempotencia durante 24 h y bloquea la clave entre trabajadores concurrentes
  (`TicketWhatsAppAdapterTest`, 7 pruebas). Límite: la memoria es del proceso; si el backend se reinicia entre el ticket y el
  reintento, puede duplicarse. Para revisarlo, cada ticket lleva `Referencia WhatsApp: <huella>`.
* Si Meta acepta el mensaje y la reserva vence antes de registrar `ACEPTADA` (>120 s), podría reenviarse.
* La foto no se adjunta al ticket (solo el texto leído por OCR); queda como mejora.
