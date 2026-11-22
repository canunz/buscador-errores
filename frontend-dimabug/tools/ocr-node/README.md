# Prototipo OCR Node — etapa 1

Prototipo local, sin HTTP, webhooks, WhatsApp, MySQL ni Spring Boot. Utiliza el mismo extractor TypeScript que Angular. Solo la CLI abre archivos: acepta los cuatro fixtures sinteticos existentes por nombre y SHA-256. No admite rutas, URLs ni imagenes reales arbitrarias. La API `reconocerBytes` procesa PNG/JPEG/WebP en memoria.

## Ejecutar en este equipo

Desde PowerShell 7.4+:

```powershell
Set-Location C:\Proyectos\DimaBug-MVP
& .\frontend-dimabug\tools\ocr-node\run.ps1 -Mode Build
& .\frontend-dimabug\tools\ocr-node\run.ps1 -Mode Fixture -Fixture error-legible.png
& .\frontend-dimabug\tools\ocr-node\run.ps1 -Mode Test
```

El lanzador usa exclusivamente Node 22.23.5 portable ya verificado en `qa/runtime/tools/node-v22.23.5-win-x64/node.exe`, sin cambiar PATH o Node global. Limpia el entorno heredado mediante Qa.Common.psm1 y conserva solo variables basicas de Windows. No descarga ni instala automaticamente. La CLI devuelve estado, conteos y confianza; no imprime texto OCR ni nombre del archivo.

Fixtures disponibles: error-legible.png, error-borroso.png, error-ambiguo.png, sin-texto.png. Compilar primero si no existe dist. Cambiar los fixtures requiere una revision expresa de su contenido autorizado y de sus hashes.

## Instalacion reproducible en otro checkout

Requiere las dependencias Angular originales ya instaladas (TypeScript 5.7.3, tipos Node y modelos eng/spa). No ejecutar npm install en el frontend para este prototipo. Instalar este paquete independiente usando Node 22 y su npm, `npm ci --ignore-scripts --no-audit --no-fund`, dentro de `frontend-dimabug/tools/ocr-node`, con configuraciones npm vacias/separadas si se necesita el mismo aislamiento de este equipo. No usar npm global ni reemplazar lockfiles existentes del frontend.

En este equipo el npm portable esta junto a node.exe en node_modules/npm/bin/npm-cli.js. Invocarlo con ese node.exe evita depender del Node global. Se usaron archivos npm vacios en qa/runtime/npm-config-ocr y cache qa/runtime/npm-cache, todos ignorados. En Windows x64 funcionan los binarios precompilados de Sharp sin ejecutar scripts de instalacion; otras plataformas requieren comprobar disponibilidad, no habilitar scripts automaticamente.

Los comandos equivalentes, con Node 22 activo, son `node build.cjs`, `node test/run.cjs` y `node dist/tools/ocr-node/src/cli.js error-legible.png`. No existe servicio que iniciar.

## Dependencias aisladas y limites

- Sharp 0.35.5: decodificacion real, rechazo de datos corruptos, preparacion blanca y reduccion de imagen sin DOM. Limite de entrada de 12 millones de pixeles y failOn=warning.
- Tesseract.js 7.0.0, core 7.0.0 y override wasm-feature-detect 1.8.0: la version 1.10.0 transitiva del frontend contiene import.source que Node 22 no puede analizar. Este ajuste autorizado queda solo en package.json/package-lock.json del prototipo.
- build.cjs copia los modelos YA instalados a dist/idiomas, sin CDN. langPath y workerPath locales explicitos, cacheMethod=none. La ruta Lang[] con datos en memoria no se utiliza porque la inicializacion de Tesseract 7 observada interpreta data como nombre de idioma; no se parchea node_modules.
- Misma configuracion eng+spa, OEM LSTM 1 y diccionarios deshabilitados que el navegador.
- 5 MiB, 12 megapixeles, lado maximo 2400, resultado de hasta 20.000 caracteres, plazo de 30 s. Un proceso hijo por reconocimiento permite terminar preparacion/inicializacion/OCR; no se heredan credenciales, NODE_OPTIONS ni proxies. No se escriben imagenes o texto OCR en disco.
- El preload sin-red se ejecuta antes del worker y tambien se hereda por los worker_threads de Tesseract. Deniega fetch, HTTP/HTTPS, conexiones TCP/TLS y UDP comunes. Es defensa del prototipo, no un sandbox de seguridad del sistema operativo.

El navegador conserva su canvas/createImageBitmap y su ciclo de vida. Sharp y canvas pueden producir pixeles distintos para otros formatos, perfiles de color, EXIF o escalados: igualdad del extractor con la misma entrada normalizada no garantiza igualdad universal de OCR sobre cualquier fotografia. No se modifica ni se normaliza por similitud un codigo ambiguo.

## Pruebas y evidencia

`run.ps1 -Mode Test` compila el nucleo sin tipos Angular/DOM/Node y luego el adaptador Node. Ejecuta 31 pruebas: 18 casos dorados capturados del servicio original, limpieza/limites, rutas rechazadas, corruptos, cuatro imagenes reales sinteticas, JPEG/WebP generados solo en memoria, timeout y bloqueo de red. MockTimers emite una advertencia experimental en Node 22; la prueba sigue siendo satisfactoria y no sustituye un ensayo prolongado de carga.

Informe completo: docs/implementacion/OCR_compartido_etapa1.md desde la raiz del repositorio. No avanzar a WhatsApp ni desplegar a partir de este prototipo.
