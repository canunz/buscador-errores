# Extracción local de errores del OCR

## Diagnóstico y alcance

La salida del OCR se enviaba completa como una consulta. Importes, números de botones y fragmentos de rutas entraban en la normalización y puntuación del buscador; por ejemplo, el ruido del caso de voucher añadía 33, 050 y D50 como términos numéricos. El mensaje limpio sí recuperaba la solución. El problema era la construcción de la consulta, no el reconocimiento de la frase de error.

IMPLEMENTADO localmente el 10 de noviembre de 2026. Sin cambios backend, dependencias nuevas, migraciones, despliegue ni acceso a staging en esta tarea. Los cambios anteriores del buscador y OCR siguen independientes en el árbol de trabajo.

## Comportamiento

`ExtractorErrorOcrService` procesa una copia por líneas y devuelve hasta tres candidatos con procedencia y advertencias. Reconoce señales genéricas de error en español/inglés y códigos técnicos (hexadecimal, ORA, SQLSTATE, HRESULT, ERR, HTTP). Prioriza códigos y mensajes distintos antes de alternativas sin ruta. No utiliza campos internos ni consultas adicionales al servidor.

Une únicamente continuaciones adyacentes con señales estructurales (guion conservado, separador de ruta, código fragmentado) y muestra advertencias. No sustituye O/0 o l/1. Las rutas dudosas se omiten de la propuesta cuando existe una frase de error suficiente. En el ejemplo `voucher-v` seguido de `S| enta…`, propone **Error al escribir el archivo**: no inventa `voucher-venta`.

El componente conserva el original sin modificar en memoria, visible bajo un desplegable. Cada propuesta es seleccionable y editable; ninguna búsqueda ocurre hasta pulsar **Buscar soluciones**. Cada confirmación reutiliza una sola consulta existente, sin reintentos por candidatos. Sin señal suficiente, el formulario permite una consulta manual. Los mensajes mayores de 300 caracteres no se recortan: requieren edición explícita. Cancelar o quitar limpia los candidatos; se ignoran resultados tardíos.

La búsqueda escrita no cambia. Se reutilizan `ChatbotService` y el endpoint público; no se altera el filtrado de conocimientos publicados, soluciones activas y autorizadas para autoservicio. El original no se registra ni se guarda en almacenamiento del navegador. La visualización usa interpolación de texto, sin HTML dinámico.

## Archivos de esta corrección

- `frontend-dimabug/src/app/core/models/extraccion-error-ocr.model.ts`
- `frontend-dimabug/src/app/core/services/extractor-error-ocr.service.ts` y `.spec.ts`
- `frontend-dimabug/src/app/features/chatbot/chatbot-imagen.component.ts`, `.html`, `.css`, `.spec.ts`
- Este documento y `OCR_chatbot.md`.

## Validación y límites

Las pruebas cubren mensaje limpio, ruido de pantalla, palabras/rutas divididas, códigos, varios errores, ausencia de señal y caracteres incorrectos; también confirmación única, edición manual, original intacto, límites y cancelación. La suite Angular incluye OCR real con fixtures sintéticos y regresiones de búsqueda manual/materiales.

La extracción es heurística: un texto sin señales reconocibles o un error muy dañado puede requerir selección manual. Las uniones no aseguran reconstrucción exacta; por eso conservan advertencias y confirmación. No se eliminan los controles del buscador para compensar el ruido. No se garantiza recuperación para toda captura ni se han validado todos los formatos de errores de proveedores.

VALIDADO: Angular ChromeHeadless, 512 pruebas correctas; compilación production correcta. Backend, 27 pruebas correctas en ChatbotBusquedaErroresHttpTest, BusquedaAutoservicioTest y ConsultarChatbotServiceTest, con H2 aislada. Incluyen rechazo de soluciones privadas/inactivas y conocimientos borradores. Sin correos reales ni operaciones en staging. La compilación conserva avisos de presupuesto del bundle/estilos y CommonJS de Tesseract; no impiden compilar. Pendiente: QA manual con capturas reales del entorno UAT.
