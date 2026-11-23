# OCR compartido — informe tecnico etapa 1

Implementacion local del 12 de noviembre de 2026. Rama integracion-final-uat conservada; sin commits ni despliegues. Alcance: extraer el algoritmo ya existente a TypeScript independiente, mantener Angular y demostrar el uso local desde Node. No se implementaron WhatsApp, webhooks, servicios HTTP, autenticacion, mensajeria ni endpoints. No se modificaron Spring Boot, MySQL, redes o servicios.

## Resultado y arquitectura

```text
Angular: archivo -> preparacion canvas -> worker Tesseract local -> texto
                                                                |
                               nucleo TypeScript compartido <----+
                               normalizar / extraer / validar
                                                                |
Node: fixture autorizado -> Sharp -> proceso Tesseract local -----+
```

El nucleo esta en frontend-dimabug/src/app/core/ocr-compartido. No contiene Angular, decoradores, DOM ni APIs de Node. build.cjs lo compila con solo lib.es2022 y types=[], antes de compilar el adaptador Node.

ExtractorErrorOcr conserva el cuerpo completo del algoritmo original: deteccion, prioridades, deduplicacion, maximo de tres candidatos, alternativas, avisos, lineas y codigos ambiguos. Se comprobaron igualdad textual del cuerpo movido e igualdad de resultados contra 18 casos capturados ANTES de la extraccion.

ExtractorErrorOcrService sigue siendo injectable con el mismo metodo extraer(original) y resultado. Hereda del extractor puro. Los modelos originales reexportan tipos/limites para conservar las rutas publicas. OcrService usa la misma limpieza y limite de texto extraidos; sus mensajes, cancelacion y plazo se mantienen. La funcion prepararImagenOcr del navegador conserva su cuerpo exacto; solo la inspeccion de cabeceras sin DOM se mueve al nucleo.

El prototipo Node esta en frontend-dimabug/tools/ocr-node. Valida bytes/cabeceras antes del proceso hijo, prepara con Sharp y ejecuta Tesseract con modelos instalados localmente. Un proceso por solicitud impone el plazo incluso durante inicializacion; no es un servidor ni un pool. La CLI solo admite cuatro fixtures sinteticos preexistentes verificados por SHA-256, sin leer rutas arbitrarias ni URLs. Imagenes derivadas de pruebas se generan en memoria y no se guardan.

## Inspeccion y proteccion de cambios previos

Se ejecuto git status antes de modificar. Existian cambios pendientes en frontend/backend y archivos OCR sin seguimiento. No se restauro, limpio, cambio de rama ni sobrescribio su contenido con versiones Git. Se guardaron copias de las fuentes OCR y huellas de 296 archivos del frontend bajo qa/runtime/ocr-etapa1-baseline (ignorado).

La comparacion final de huellas identifica exactamente cinco archivos preexistentes modificados, todos OCR. Los archivos de componentes, pruebas previas, worker de navegador, package.json, package-lock.json y angular.json mantienen el contenido anterior a esta etapa, incluidos sus cambios locales previos.

### Archivos preexistentes modificados

Todos bajo frontend-dimabug/src/app/core:

- services/extractor-error-ocr.service.ts: adaptador injectable del extractor puro.
- services/ocr.service.ts: delega limpieza/limite al nucleo, conserva errores y ciclo de vida.
- material/imagen-ocr.ts: importa/reexporta inspeccion de cabeceras; preparacion DOM intacta.
- models/extraccion-error-ocr.model.ts: reexporta los mismos tipos.
- models/ocr.model.ts: reexporta los mismos limites.

### Archivos creados

Nucleo compartido:

- frontend-dimabug/src/app/core/ocr-compartido/extractor-error.ts
- frontend-dimabug/src/app/core/ocr-compartido/extraccion-error.model.ts
- frontend-dimabug/src/app/core/ocr-compartido/imagen-header.ts
- frontend-dimabug/src/app/core/ocr-compartido/limites.ts
- frontend-dimabug/src/app/core/ocr-compartido/texto.ts
- frontend-dimabug/src/app/core/ocr-compartido/equivalencia.spec.ts
- frontend-dimabug/src/app/core/ocr-compartido/pruebas/casos.ts
- frontend-dimabug/src/app/core/ocr-compartido/pruebas/imagenes.ts

Prototipo, todos bajo frontend-dimabug/tools/ocr-node:

- package.json y package-lock.json: dependencias aisladas.
- build.cjs: compilacion sin DOM del nucleo y compilacion del prototipo/modelos locales.
- run.ps1: lanzador con Node 22 y entorno limpio.
- src/adapter.ts, src/worker.ts: limite/proceso y ejecucion OCR.
- src/fixtures.ts: lectura limitada y hash de fixtures autorizados.
- src/cli.ts: entrada local con salida de metadatos sin texto.
- src/sin-red.ts: denegacion de APIs de red en el proceso OCR.
- test/run.cjs y test/ocr.test.cjs: pruebas Node.
- README.md: uso y limitaciones.

Documentacion/evidencia: este informe y docs/07_Pruebas/evidencias/ocr-compartido-etapa1-2026-11-12.json. Las salidas dist, dependencias, runtime portable y logs estan ignorados; no forman parte de un release. El manifiesto QA se actualiza conservando el anterior para identificar el nuevo contenido de trabajo, sin compilar backend ni registrar un artefacto de despliegue.

## Versiones y dependencias

| Elemento | Version verificada | Alcance |
|---|---|---|
| Angular / CLI | 19.2.25 / 19.2.27 | Sin cambios |
| TypeScript | 5.7.3 | Existente, usado para ambos entornos |
| Chrome Headless | 157.0.0.0 | Pruebas Angular |
| Node global | 24.15.0 | No modificado, no utilizado para validar Angular |
| Node portable | 22.23.5 Windows x64 | Aislado en qa/runtime/tools |
| Tesseract.js / core | 7.0.0 / 7.0.0 | Frontend sin cambios, copia aislada Node |
| Sharp | 0.35.5, libvips 8.18.7 | Solo prototipo Node |
| wasm-feature-detect | 1.8.0 | Override autorizado solo Node; frontend conserva 1.10.0 |
| Datos eng/spa | 1.0.0, 4.0.0_best_int | Recursos existentes, copiados en build, no descargados en runtime |

Node 24 no esta en la matriz oficial de Angular 19.2; se solicito y obtuvo autorizacion para Node 22 aislado. Zip oficial node-v22.23.5-win-x64.zip, SHA-256 verificado contra SHASUMS256.txt oficial: 4a191082e9706866830379d577d2cc8a50c776fcd9436a8d061b063ed0d9a0e3. No se cambio PATH global ni se instalo Node en el sistema.

Sharp es necesario en Node para decodificar formatos permitidos, limitar pixeles, aplanar y reducir sin DOM; su engine >=20.9.0 admite Node 22 y se verifico el binario Windows x64. Se instalo con scripts deshabilitados, npm sin configuraciones/credenciales heredadas y lockfile propio. Tesseract 7 declara wasm-feature-detect ^1.8.0, pero la version transitiva 1.10.0 del frontend usa import.source incompatible con Node 22. Se detuvo el trabajo para pedir autorizacion y se fijo 1.8.0 solo en el prototipo, junto con core 7.0.0. No se parchearon node_modules ni dependencias de Angular.

## Pruebas ejecutadas

| Verificacion | Resultado |
|---|---|
| Linea base Angular OCR/chatbot antes de cambios | 36/36 satisfactorias |
| Angular OCR/chatbot mas equivalencia compartida | 55/55 satisfactorias |
| Node, suite final | 31/31 satisfactorias, 0 fallos/omitidas |
| Compilacion del nucleo solo ES2022 sin tipos Angular/DOM/Node | Satisfactoria |
| Compilacion Angular --configuration=qa-local | Satisfactoria, sin iniciar aplicacion |
| CLI Node sobre error-legible.png mediante lanzador limpio | Satisfactoria; 16 caracteres, 1 candidato; contenido no registrado |
| Huellas contra fuentes previas | Solo cinco archivos OCR existentes modificados |

Los 18 casos dorados comparan el objeto completo: original, id, texto, etiqueta, posiciones, avisos, orden de candidatos y hayMas. Cubren rutas incompletas/ambiguas, saltos CRLF, codigos divididos, codigos dudosos sin correccion heuristica, deduplicacion, texto largo, vacio, varias incidencias e instrucciones tratadas como datos.

Comparacion real sobre las mismas imagenes autorizadas, con Tesseract y modelos locales:

| Imagen sintetica | Angular antes | Angular despues | Node |
|---|---|---|---|
| error-legible.png | Error 0x0000011b | Igual | Igual |
| error-borroso.png | Error 0x0000011b | Igual | Igual |
| error-ambiguo.png | Error OX0000011b | Igual | Igual, sin corregir OX a 0x |
| sin-texto.png | Vacio | Vacio | Vacio y sin candidatos |

Tambien se ejecutaron decodificacion/reconocimiento JPEG y WebP generados en memoria, rechazo de PNG corrupto con cabecera plausible, formato suplantado, vacio, >5 MiB, >12 megapixeles, limpieza/limite de texto, timeout con reloj simulado y rechazo de APIs de red sin conectar a destinos. La advertencia ExperimentalWarning de MockTimers en Node 22 no es un fallo.

Logs locales verificables, sin imagenes ni contenido de usuarios: qa/runtime/ocr-etapa1-baseline/angular-baseline.log, angular-after.log, angular-build.log y node-after.log. El JSON de evidencia registra sus hashes y las fuentes cambiadas. Los textos impresos por la prueba OCR real de Angular son exclusivamente fixtures sinteticos preexistentes; la CLI y worker Node no imprimen texto OCR.

## Ejecucion reproducible

Desde PowerShell 7.4 en C:\Proyectos\DimaBug-MVP:

```powershell
& .\frontend-dimabug\tools\ocr-node\run.ps1 -Mode Build
& .\frontend-dimabug\tools\ocr-node\run.ps1 -Mode Fixture -Fixture error-legible.png
& .\frontend-dimabug\tools\ocr-node\run.ps1 -Mode Test
```

Para Angular, usando el node.exe portable y directorio frontend-dimabug:

```text
node_modules/@angular/cli/bin/ng.js test --watch=false --browsers=ChromeHeadless --progress=false --include=src/app/core/**/*ocr*.spec.ts --include=src/app/core/ocr-compartido/**/*.spec.ts --include=src/app/features/chatbot/*.spec.ts
node_modules/@angular/cli/bin/ng.js build --configuration=qa-local --progress=false
```

Esos argumentos se pasan al Node portable con entorno limpio y CHROME_BIN al Chrome local. El builder de pruebas abre solo el servidor local de Karma; no se inicia Angular serve ni Spring. README del prototipo explica la instalacion en otro checkout.

## Riesgos, limites y pendientes

- No se detectaron regresiones en las pruebas ejecutadas. No se ejecuto la suite completa de otros modulos, QA funcional manual, pruebas con fotos reales, carga sostenida, Linux/macOS ni despliegue; no se presentan como aprobados.
- Igualdad de resultados demostrada para 18 entradas sinteticas y cuatro imagenes; no garantiza OCR universalmente identico entre canvas y Sharp, especialmente EXIF, perfiles de color, interpolacion o fotos de baja calidad. Se conserva la revision humana y no se sustituyen codigos dudosos.
- El timeout Node se probo con reloj simulado y proceso real cancelado; no equivale a una prueba prolongada de consumo o agotamiento de memoria. No hay pool ni servicio concurrente autorizado.
- El bloqueo de APIs de red es una defensa del proceso local, no una frontera de seguridad del sistema operativo contra codigo nativo malicioso. Modelos y worker tienen rutas locales explicitas y cache deshabilitada.
- Sharp tiene binarios nativos; otros sistemas requieren validar sus binarios. Las dependencias del prototipo deben revisarse antes de cualquier uso distinto o actualizacion; el override wasm se mantiene deliberadamente fijado.
- Fallos durante desarrollo (incompatibilidad import.source y ruta Lang[] de inicializacion) quedaron resueltos y reprobados satisfactoriamente con la suite final; ninguna prueba obligatoria ejecutada queda fallida. No se declara validacion de las actividades fuera de alcance.
- Metadatos Jira proporcionados: epica Evolucion inteligente de DimaBug, historia Reutilizacion del reconocimiento OCR entre plataformas, sprint por definir. No se creo ni actualizo un ticket en Jira; este informe permite registrar la etapa sin inventar identificadores.

Etapa 1 completada localmente. Se espera una nueva instruccion; no se avanza a WhatsApp.

Fuentes primarias: https://angular.dev/reference/versions ; https://nodejs.org/dist/v22.23.5/SHASUMS256.txt ; https://sharp.pixelplumbing.com/install/ ; https://sharp.pixelplumbing.com/api-constructor/ ; https://github.com/naptha/tesseract.js/blob/master/docs/api.md . La inicializacion de idiomas tambien se contrasto directamente con el codigo instalado de Tesseract 7.0.0.
