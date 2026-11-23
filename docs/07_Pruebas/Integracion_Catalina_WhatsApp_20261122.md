# Integración local Catalina + WhatsApp/OCR — 22-11-2026

## Procedencia y estado

- Base WhatsApp publicada: `b689c34`, rama `feature/whatsapp-autoatencion-do`.
- Catalina actualizada desde origin: `db9888f` (panel Tickets por usuario), junto con `1cb5892` (release/autoservicio) y `09b402c` (acceso unificado y bloqueo progresivo).
- Rama de integración: `codex/integracion-catalina-whatsapp`, en worktree independiente.
- Integración sin conflictos, revisada y autorizada para commit y publicación exclusivamente en `codex/integracion-catalina-whatsapp`. Sin despliegue ni cambios en servidores o bases existentes.
- El proyecto original conserva su rama y sus 83 archivos modificados. No se hizo stash, reset ni sustitución de sus archivos.
- Los cambios locales originales se contrastaron con Catalina: 21 archivos idénticos y 62 variantes. No se aplicó esa copia local encima de Catalina en bloque: habría eliminado mejoras como bloqueo progresivo, último ingreso y protección de reportes.

## Funciones preservadas y verificadas

| Área | Preservación y evidencia |
|---|---|
| Dashboard de Catalina | Panel Tickets por usuario conectado al inicio; ranking, activos/resueltos, porcentajes, expansión y reintento cubiertos por dos regresiones Angular nuevas |
| Acceso al ranking | Caso de uso restringido a administrador; tres regresiones nuevas comprueban resultados de administrador y rechazos de soporte/usuario sin consultar el ranking |
| Autenticación | Conservados acceso unificado por código, bloqueo progresivo, registro de último ingreso y su conservación al editar usuarios; pruebas de identidad y seguridad correctas |
| Usuarios y reportes | Conservados último ingreso y guard de reportes por rol de Catalina |
| Autoservicio | Conservados nuevo diseño del centro de ayuda, pasos de solución y galerías públicas por tipo de material, sin reemplazarlos por las pantallas antiguas |
| Hardware/sistemas/módulos | Conservadas acciones y navegación por ID, retorno al hardware actualizado y restricciones de rol; 15 pruebas específicas correctas |
| OCR del chatbot | Conservados modal, edición/confirmación, materiales y conversación; batería Angular completa correcta |
| OCR Node | Núcleo compartido, imágenes sintéticas autorizadas, PNG/JPEG/WebP, límites y prohibición de red; 31 pruebas reales con Node 22.23.5 correctas |
| WhatsApp | Conservados recepción, HMAC, autorización, deduplicación, persistencia, respuestas y creación de tickets; batería específica revalidada |
| Arquitectura | WhatsApp accede a tickets por `tickets.api.TicketsFachada`; no importa sus puertos ni dominio internos. Las reglas siguen prohibiendo acceso a dominio/aplicación/infraestructura desde otros módulos |
| Búsqueda pública | Conservadas normalización y relevancia de errores y filtros de publicación/autoservicio; HTTP y pruebas de búsqueda correctas |

## Resolución de conflictos y regresiones

Se revisaron 25 archivos con conflictos (38 bloques). Las diferencias de comentarios se
combinaron con las implementaciones funcionales; no se seleccionó un árbol completo con
ours/theirs. Se conservaron las nuevas pantallas de Catalina y la búsqueda/OCR de WhatsApp.
La combinación dejó dos funciones SQL idénticas duplicadas: se eliminó la duplicación,
sin quitar el escape LIKE ni la normalización.

La prueba de navegación seleccionaba cualquier `.modal`, incluido el modal de carga.
Ahora localiza el formulario por su título y actualiza el DOM al abrirlo, manteniendo las
comprobaciones de IDs, sistema seleccionado, regreso al equipo y actualización del nombre.
No se cambió la navegación de producción para satisfacer la prueba.

La batería ampliada de arquitectura detectó la dependencia previa de WhatsApp del interior
de tickets. Se añadió una fachada pública mínima, que delega al mismo CrearTicketUseCase,
conserva prioridad MEDIA, el actor autenticado y sus rechazos. El adaptador mantiene su
idempotencia y formato de referencia. Dos pruebas nuevas verifican argumentos y propagación
de rechazos; las pruebas existentes de concurrencia y reintento también pasan.

## Validación

- Angular completo: **555 correctas**, sin fallos.
- Backend: **518 casos en informes finales; 505 ejecutados correctamente, 13 omitidos**, sin errores ni fallos. La batería inicial de identidad/tickets/WhatsApp se complementó con la repetición de la frontera modular y flujos afectados tras corregirlos.
- OCR Node 22.23.5: **31 correctas**, incluyendo OCR real de imágenes sintéticas.
- Frontend producción: correcto; permanecen advertencias de tamaño y CommonJS de Tesseract.
- No se usaron credenciales Meta ni se enviaron mensajes. Las pruebas de persistencia usaron H2/fixtures aislados; los casos omitidos no se consideran aprobados.

Evidencias locales ignoradas por Git, en `qa/runtime/` del worktree:
`integracion-angular-build.log`, `integracion-angular-test-final.log`,
`integracion-backend.log`, `integracion-backend-fronteras.log`,
`integracion-ocr-node.log`, `integracion-validacion/resultados.json` y los informes
JUnit bajo `dima-bug-backend/target/surefire-reports/`.

## Contraste final de preservación

El diff del índice respecto a `origin/catalina` no muestra cambios en el módulo de
identidad, las pantallas de acceso, el dashboard de inicio ni la implementación del
panel Tickets por usuario. Estas funciones conservan íntegro el contenido de Catalina.
WhatsApp/OCR y la fachada pública de tickets quedan añadidos sin reemplazarlas.
La revisión de formato de nuestros cambios frente a Catalina no presenta incidencias;
no se normalizó masivamente el formato heredado de su rama.

## Límites y revisión pendiente

Las pruebas no equivalen a una UAT visual con personas ni a una prueba Meta/DigitalOcean.
Revisar manualmente ranking en administrador, acceso por código/bloqueo, galerías y flujo
foto–consulta–solución. No se certifica la ausencia absoluta de regresiones ni se infiere
que los 13 casos omitidos se hayan ejecutado. La publicación autorizada se limita a la rama de integración. No se modifica `main`,
la rama original ni DigitalOcean; la UAT manual continúa pendiente.

## Comprobación previa al commit autorizado

- Los 83 archivos originales conservan exactamente sus SHA-256 de la referencia guardada.
- Autenticación, dashboard, ranking y galerías coinciden con los blobs de Catalina `db9888f`.
- `qa/whatsapp/ubuntu` coincide íntegramente con la base WhatsApp `b689c34`.
- Los únicos cambios de producción en WhatsApp frente a esa base son la configuración
  y el adaptador que usan la nueva API pública de tickets; las regresiones revalidadas pasan.
- El índice coincide con los archivos validados y no tiene conflictos pendientes.
- Se revisaron los 788 archivos añadidos/modificados del merge: sin runtime privado,
  binarios generados, claves privadas, tokens de los formatos revisados ni identificadores
  personales conocidos. El único campo de configuración señalado corresponde al marcador
  de integridad de un truststore de CA pública, no a una credencial de autenticación.
- No se fuerza ningún push, no se eliminan ramas y no se ejecutan acciones de despliegue.
