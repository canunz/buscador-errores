# OCR local del chatbot DimaBug

## Estado y arquitectura

IMPLEMENTADO localmente el 9 de noviembre de 2026, con autorización posterior al análisis. No se ha publicado en staging ni GitHub. Backend, MySQL, JWT, roles, rutas NFC/QR y contratos API permanecen sin cambios.

El frontend conserva sus componentes standalone, servicios y modelos. `ChatbotImagenComponent` gestiona la selección, estados y revisión; `OcrService` valida/prepara la imagen y gestiona el trabajo, cancelación y plazo; `ocr.worker.ts` coordina Tesseract fuera del hilo de interfaz. Hay un único motor reutilizable por componente, sin pool: el coordinador tiene un worker interno de Tesseract. Terminar el coordinador cancela también sus workers descendientes, incluso durante la inicialización.

El texto confirmado reutiliza `ChatbotService.consultar()` y `POST /api/publico/chatbot/consultas`. El backend usa `ConsultarChatbotService`, `BuscarConocimientoService` y el puerto de búsqueda existente. Solo devuelve conocimientos publicados y soluciones activas habilitadas para autoservicio. Se conservan los materiales públicos en las tarjetas y los guards de tickets.

## Uso

1. Abrir `/chatbot` y pulsar **Adjuntar imagen**.
2. Elegir una captura o fotografía PNG, JPEG o WebP estática.
3. Esperar el reconocimiento; se puede cancelar. La conversación escrita sigue disponible.
4. Elegir uno de los mensajes de error propuestos y corregirlo si corresponde. **Ver texto OCR original** conserva la salida completa; si no hay candidatos, copiar un fragmento o escribir el mensaje. Si excede 300 caracteres, dejar únicamente el mensaje relevante. No se recorta ni se envía automáticamente. Véase [extracción de errores](Extraccion_errores_OCR.md).
5. Pulsar **Buscar soluciones**. Se utiliza el texto corregido y se muestran las soluciones reales del catálogo.
6. Si no hay resultados, se ofrece el flujo de soporte existente: login para visitantes, creación de tickets para usuarios autorizados o bandeja para administradores. No se crean tickets automáticamente.

La imagen no se sube al servidor, no se guarda en MySQL ni almacenamiento del navegador. Solo se envía la consulta de texto después de confirmarla. El modelo permanece en memoria para reutilización; **Quitar imagen**, cancelar un trabajo o salir libera el motor. Los recursos estáticos de idiomas pueden almacenarse en la caché HTTP del navegador; no son imágenes del usuario.

## Seguridad y límites

- Máximo 5 MiB y 12 millones de píxeles; PNG/JPEG/WebP por MIME, firma y dimensiones de cabecera, además de decodificación real. No se confía en extensión ni `accept` únicamente.
- JPEG se inspecciona por segmentos SOF; WebP admite VP8, VP8L y VP8X estático. Se rechaza WebP animado. SVG, PDF, GIF y HEIC no se admiten en este flujo.
- Las dimensiones se validan antes de decodificar y se vuelven a comprobar después. Se reduce el lado mayor a 2400 px y se aplana sobre blanco sin EXIF antes del OCR.
- Plazo máximo 30 segundos para preparación/inicialización/reconocimiento. Al agotarlo se termina el trabajo y se puede reintentar. No se permite lanzar otro desde el botón mientras hay uno activo.
- Máximo 20.000 caracteres reconocidos para revisión y 300 para consultar el contrato existente. Resultado vacío, corrupto, formato inválido y fallo del motor tienen mensajes diferenciados.
- El texto es dato de búsqueda, nunca HTML ejecutable ni instrucciones de un sistema generativo. No se registran fotos, nombres de archivo o texto real en logs. La única salida `OCR_QA_MUESTRAS_SINTETICAS` pertenece a una prueba y contiene exclusivamente fixtures artificiales.
- Validaciones del navegador protegen recursos locales; no sustituyen permisos del servidor. Los endpoints públicos mantienen sus filtros y el límite de consultas existente.

## Dependencias y distribución local

Versiones fijadas:

| Paquete | Versión | Uso |
| --- | --- | --- |
| `tesseract.js` | 7.0.0 | Motor, dependencia de producción; Apache-2.0 |
| `@tesseract.js-data/eng` | 1.0.0 | Datos ingleses de compilación, variante `4.0.0_best_int`; MIT según paquete |
| `@tesseract.js-data/spa` | 1.0.0 | Datos españoles de compilación, misma variante; MIT según paquete |

El core WebAssembly queda fijado por `package-lock.json`. No se actualizaron versiones de paquetes previamente instalados; se agregaron 15 paquetes. No se añadió una biblioteca visual.

`angular.json` copia desde `node_modules` a `ocr/v7/` el worker, los tres builds LSTM (estándar, SIMD y relaxed SIMD, con sus archivos WASM) y los dos idiomas. Se selecciona `eng+spa`, sin diccionarios de palabras para evitar favorecer vocabulario frente a tokens técnicos. No se corrigen códigos heurísticamente.

Los nueve recursos OCR distribuidos suman **25.441.321 bytes** (aprox. 24,3 MiB sin compresión). No se descargan todos los builds al reconocer: Tesseract selecciona el adecuado. El motor/coordinador se emite en un archivo separado y solo se inicia al seleccionar una imagen. La primera carga puede ser lenta en teléfonos o redes limitadas; las mediciones locales no son una certificación de rendimiento móvil físico.

No se usan CDN en la configuración. `workerPath`, `corePath` y `langPath` son del mismo origen y respetan el `base href`. El despliegue debe incluir todo el directorio `ocr/v7`, servir `.wasm` como `application/wasm` y JavaScript como script. Si existe CSP restrictiva, validar `worker-src 'self'` y la política que permita compilar WebAssembly (por ejemplo `wasm-unsafe-eval` cuando el navegador la soporte); no se cambió Nginx ni CSP de staging. No es necesario habilitar `blob:` para el worker de Tesseract, que se configura con `workerBlobURL:false`.

Referencias: [Tesseract.js](https://github.com/naptha/tesseract.js), [recursos locales](https://github.com/naptha/tesseract.js/blob/master/docs/local-installation.md), [rendimiento](https://github.com/naptha/tesseract.js/blob/master/docs/performance.md).

## Ejecución y pruebas

Desde `C:/Proyectos/DimaBug-MVP/frontend-dimabug`:

```powershell
npm ci
npm start
# Abrir http://localhost:4200/chatbot; el buscador necesita el backend habitual.
npx ng test --watch=false --browsers=ChromeHeadless
npx ng test --watch=false --browsers=ChromeHeadless --include=src/app/core/services/ocr-real.spec.ts
npm run build
```

Las imágenes sintéticas están en `src/test-assets/ocr` y se copian únicamente a las pruebas, no al build de producción. La prueba real carga los modelos locales y no depende de un backend o servicios OCR externos. Al cambiar motor o modelo deben revalidarse los resultados, no ampliar expectativas para ocultar errores.

## QA y evidencia

- **502 pruebas Angular aprobadas**, suite completa incluyendo motor real, validaciones, errores, cancelación, timeout, corrección manual, conversación, materiales públicos y rutas NFC/QR.
- **20 pruebas backend aprobadas**: `ConsultarChatbotServiceTest` (3), `AutoservicioPublicoHttpTest` (9), `ChatbotSecurityIntegrationTest` (8), con H2/mocks. No se envían correos reales.
- Build de producción correcto. Advertencias: presupuesto inicial y CSS de componentes existentes, y CommonJS de Tesseract dentro del worker. No se elevaron presupuestos para ocultarlas.
- Se detectó una expectativa preexistente del login que seguía buscando **Enviar código de acceso**, aunque HEAD ya muestra **Continuar**. Solo se ajustó esa expectativa del test; no se modificó la autenticación.
- Auditoría runtime: 7 hallazgos de Angular (4 altos, 3 moderados), mismos que la línea base. Auditoría completa incluyendo herramientas: 54 (4 críticos, 20 altos, 28 moderados, 2 bajos); ninguno afecta a las rutas de paquetes recién añadidas. Son pendientes de mantenimiento independientes del OCR; no se ejecutó `npm audit fix` ni cambios de versiones ajenos.

| Caso | Comprobación |
| --- | --- |
| CP-OCR-001 | Motor real reconoce `Error 0x0000011b` en muestra monoespaciada |
| CP-OCR-002 | Imagen blanca devuelve texto vacío y la UI informa ausencia |
| CP-OCR-003 | Muestra con desenfoque procesada; revisión editable y advertencia de ambigüedad |
| CP-OCR-004 | MIME/firmas incorrectos, PDF/SVG y WebP animado rechazados |
| CP-OCR-005 | Límite de bytes y píxeles antes de iniciar motor |
| CP-OCR-006 | Consulta confirmada muestra solución y materiales mediante mocks; búsqueda y privacidad backend con H2 |
| CP-OCR-007 | Sin resultados ofrece soporte según sesión/rol, sin inventar soluciones |
| CP-OCR-008 | Solo se emite texto corregido tras confirmación; límite 300 sin recorte silencioso |
| CP-OCR-009 | Error, timeout, cancelación y recuperación sin bloquear el chat |
| CP-OCR-010 | Suite completa del frontend y casos backend de chatbot/autoservicio |

Resultados reales de los fixtures: legible `Error 0x0000011b`, vacía sin texto, borrosa `Error 0x0000011b`, ambigua en Arial `Error OX0000011b`. Esta última se conserva como limitación explícita: no se transforma `OX` a `0x` automáticamente ni se presenta confianza como garantía.

Evidencia de Chrome aislado sobre el build de producción: [escritorio](../07_Pruebas/evidencias/ocr/chatbot-ocr-escritorio.png), [móvil emulado](../07_Pruebas/evidencias/ocr/chatbot-ocr-movil.png), [resultado de inspección](../07_Pruebas/evidencias/ocr/resultado.json). Son imágenes sintéticas sin información interna. La inspección CDP incluyó el coordinador y los workers descendientes: registró 0 peticiones externas, 0 peticiones a `/api/` antes de confirmar y la carga local de `worker.min.js`, el core relaxed SIMD LSTM y los idiomas `eng`/`spa`.

PENDIENTE: prueba UAT tras despliegue autorizado, teléfono físico y capturas reales autorizadas; revisar CSP/MIME del servidor y atender la deuda de dependencias por separado. No hay cambios publicados ni nuevas migraciones.

## Archivos

Modificados: `frontend-dimabug/package.json`, `package-lock.json`, `angular.json`; `src/app/features/chatbot/chatbot.component.ts`, `.html`, `.css`, `.spec.ts`; y únicamente la expectativa textual en `src/app/features/auth/login/login.component.spec.ts`.

Nuevos: `tsconfig.worker.json`; `src/app/core/models/ocr.model.ts`; `src/app/core/material/imagen-ocr.ts` y su spec; `src/app/core/services/ocr.service.ts`, su spec, `ocr.worker.ts` y `ocr-real.spec.ts`; `src/app/features/chatbot/chatbot-imagen.component.ts`, `.html`, `.css`, `.spec.ts`; cuatro fixtures PNG en `src/test-assets/ocr`; este documento y la evidencia QA. Backend sin modificaciones.

## Propuesta Jira (no registrada externamente)

- Épica: **Evolución inteligente de DimaBug**.
- Historia: Como técnico de soporte, quiero adjuntar una fotografía de un mensaje de error al chatbot para reconocer automáticamente su contenido y encontrar soluciones sin transcribirlo manualmente.
- Sprint: por definir.
- Subtareas: análisis de arquitectura; evaluación del motor; validación y procesamiento; revisión y confirmación en chat; reutilización del buscador; pruebas y responsive; documentación y UAT.
- DoD local: implementación, arquitectura y pruebas completas verificadas; documentación y límites explícitos. El cierre UAT requiere validar el entorno desplegado y el teléfono físico.
