# Modal OCR del chatbot

Implementación local del 13 de noviembre de 2026. Sin commits ni despliegue.

El botón «Adjuntar imagen» abre un diálogo nativo centrado, fuera del formulario
de consulta escrita. Se reutilizan ChatbotImagenComponent, OcrService,
ExtractorErrorOcrService y ChatbotService. No se agregan dependencias ni se
modifican el motor OCR, el núcleo compartido, el backend o los contratos API.

El diálogo conserva progreso, candidatos, advertencias, original y edición.
Permite cambiar de imagen durante el reconocimiento, cancelando el trabajo
anterior. También permite entrada manual sin reconocimiento o después de un
error. Cancelar y Escape cierran, liberan el motor e invalidan resultados tardíos.
El diálogo nativo impide interacción con el fondo y gestiona el foco; al cancelar
se devuelve al botón de apertura. La búsqueda exige texto no vacío de hasta 300
caracteres y bloquea confirmaciones repetidas.

Al aceptar la consulta, el padre cierra el diálogo e inicia el flujo existente:
mensaje de usuario, búsqueda, resultados y desplazamiento al final. Si falla,
el mensaje de error ofrece «Reintentar búsqueda» conservando la consulta exacta.
El formulario OCR no forma parte del historial. El reintento también está
disponible para las consultas escritas que fallen.

## Archivos

Se modifican exclusivamente los seis archivos de componentes del chatbot
(`chatbot.component` y `chatbot-imagen.component`, TypeScript/HTML/CSS) y sus dos
archivos de pruebas. Se añade este documento. Se conservan los cambios pendientes
anteriores del repositorio.

## Validación

- Chrome Headless 157 con Node portable 22: **60 pruebas correctas**, cero fallos.
- Compilación Angular `--configuration=qa-local --progress=false`: satisfactoria.
- Incluye OCR real con las cuatro imágenes sintéticas locales, extractor,
  equivalencia compartida, selección, edición, cancelación, resultados tardíos,
  errores, consultas tradicionales y materiales públicos.
- Nuevas comprobaciones: apertura modal, foco inicial/restauración, evento de
  cancelación, escritura manual sin texto OCR, confirmación única, cierre al
  aceptar, recuperación de la consulta fallida y reintento con resultado.
- Diálogo abierto en iframes de 360 y 1280 px: sin desbordamiento horizontal.
- El primer intento no pudo iniciar Chrome dentro del aislamiento; la ejecución
  posterior fuera del aislamiento terminó con las 60 pruebas satisfactorias.

Comando Angular ejecutado desde frontend-dimabug con el Node portable existente:

```text
node_modules/@angular/cli/bin/ng.js test --watch=false --browsers=ChromeHeadless --progress=false --include=src/app/core/**/*ocr*.spec.ts --include=src/app/core/ocr-compartido/**/*.spec.ts --include=src/app/features/chatbot/*.spec.ts
```

Pendientes: QA manual en teléfono físico, capturas reales y lectores de pantalla.
Las pruebas responsive automatizadas no sustituyen esas verificaciones. No se
ejecuta la suite completa de otros módulos. No se registra un ticket Jira desde
esta tarea; épica «Evolución inteligente de DimaBug», historia «Mejora UX del
OCR», sprint por definir.
