# QA-014 — Implementación de Nuevo Procedimiento

Fecha: 2026-10-14, America/Santiago. Referencia: QA014_DIAGNOSTICO_NUEVO_PROCEDIMIENTO.md. Implementación exclusivamente local.

## 1. Resultado y archivos

Se mejoraron validaciones, precarga, descarte de cambios, recuperación de guardado incremental y advertencias de publicación en Angular. Se reutilizan las pantallas y servicios de Procedimientos. **No se modificó ninguna fuente Backend, contrato REST, dependencia o transición de estado.**

Se revisó Git al comenzar: rama `catalina`, 217 entradas locales preexistentes. No se sobrescribieron/revirtieron trabajos anteriores ni se hicieron commits, resets, clean o stash. No hubo SQL, acceso a STAGING/PRODUCCIÓN ni despliegue.

Raíz de las rutas siguientes: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/`.

| Archivo | Cambio |
|---|---|
| `src/app/features/procedimiento/procedimiento-form.component.ts` | Validaciones, precarga segura/reintento, detección de cambios, beforeunload, recuperación de navegación y alta incierta. |
| `src/app/features/procedimiento/procedimiento-form.component.html` | Mensajes específicos, bloqueo hasta carga válida, Continuar sobre ID confirmado, campos/clases coherentes con CSS existente. |
| `src/app/features/procedimiento/procedimiento-detalle.component.ts` | Cambios pendientes del Paso, confirmación de descarte, identificación de registros confirmados, verificación antes de reintentar, recarga separada y advertencia de publicación. |
| `src/app/features/procedimiento/procedimiento-detalle.component.html` | Error dentro del modal, Verificar en servidor, reintento de consulta y aviso informativo de publicación vacía. |
| `src/app/features/procedimiento/procedimiento-lista.component.ts` | Consulta informativa de pasos antes de publicar, sin bloquear si está vacío o falla esa consulta. |
| `src/app/features/procedimiento/procedimiento-lista.component.html` | Presentación de advertencia de publicación. |
| `src/app/core/services/procedimiento.service.ts` | Conserva status HTTP en Error; consulta forzada de Pasos que evita reutilizar una petición iniciada antes del guardado. |
| `src/app/features/procedimiento/cambios-procedimiento.guard.ts` | Nuevo CanDeactivate para los editores de Procedimiento. |
| `src/app/app.routes.ts` | Guard de salida solo en Nuevo, Editar y Detalle de Procedimiento; permisos de entrada intactos. |
| `src/app/features/procedimiento/procedimiento-form.component.spec.ts` | Nuevo: validaciones, carga, guardado, navegación, incertidumbre y guard. |
| `src/app/features/procedimiento/procedimiento-recuperacion-qa014.component.spec.ts` | Nuevo: fallo parcial, recuperación, no reenvío confirmado, publicación y descarte de Paso. |
| `src/app/core/services/procedimiento-recuperacion-qa014.service.spec.ts` | Nuevo: GET forzado, status y ausencia de retry automático. |
| `src/app/features/procedimiento/pasos-qa013.component.spec.ts` | Ajuste de expectativa: recarga posterior al guardado solicita GET forzado; se conservan sus casos originales. |
| `docs/QA014_IMPLEMENTACION_NUEVO_PROCEDIMIENTO.md` | Este informe. |

Se mantuvo el CSS existente y se usaron sus clases reales en el formulario: pf, franja, tarjeta, campo, control y botones. No se crearon un wizard, un CRUD duplicado o endpoints nuevos.

## 2. Validaciones y precarga

- Nombre obligatorio, no exclusivamente espacios, máximo 150 tanto en FormControl como en HTML; mensajes diferenciados de vacío y longitud.
- Descripción opcional, sin máximo arbitrario: Backend no declara un límite DTO para ese campo.
- IDs de ruta de edición/detalle deben ser enteros positivos compatibles con Integer. No se envía GET/PUT de la cabecera con identificador inválido.
- Paso conserva entero positivo/instrucción obligatoria y restricciones QA-013. Orden se limita al rango positivo Integer; no se obliga a consecutividad ni se renumeran registros.
- Guardando/verificando impide repetir solicitudes desde la misma instancia. Se valida identificador del padre y Paso antes de escribir.
- Precarga: el formulario queda deshabilitado y no se permite guardar hasta obtener el registro correcto. Error muestra mensaje y Reintentar carga; no se presentan valores vacíos como sustituto editable del registro ausente.
- Recuperación GET correcta precarga/reset del formulario y solo habilita BORRADOR. PUBLICADO sigue deshabilitado. Respuesta con ID distinto tampoco habilita edición.

El guardado de cabecera confirma éxito después de HTTP. Se conserva el ID recibido, se actualiza la base de comparación y se marca el formulario limpio. Si falla/cancela la navegación, se quita Guardando y aparece **Continuar en procedimiento {id}**; no se vuelve a crear la cabecera. Un guardado posterior desde esa pantalla usa PUT sobre el ID confirmado.

## 3. Guardado parcial y reintentos

Se conserva el diseño actual: POST cabecera BORRADOR, luego POST/PUT independientes por Paso. No existe transacción distribuida ni rollback global entre solicitudes. No se elimina el padre o pasos anteriores si falla un Paso.

| Escenario | Comportamiento |
|---|---|
| Cabecera rechazada con error conocido de validación/autorización | Datos permanecen en formulario; no hay confirmación; se permite corregir/reintentar. |
| Alta de cabecera con red/5xx/error sin status y resultado incierto | Conserva datos, bloquea otro POST automático o repetido desde el formulario y pide revisar el listado antes de crear otro registro. No presume fracaso ni busca coincidencias por nombre como si fueran identidad. |
| Paso intermedio rechazado | Muestra ID del BORRADOR, cantidad de pasos previamente confirmados en la pantalla y orden pendiente. Conserva texto/orden/crítico del formulario. Los pasos anteriores no se recrean. |
| Paso confirmado, luego GET de recarga fallido | Se integra inmediatamente la respuesta del Paso con ID/orden en la lista local; se identifica el guardado confirmado y se informa fallo de actualización. **Actualizar listado de pasos confirmados** repite solo GET, nunca POST. |
| POST/PUT de Paso con resultado incierto o 409 | Se conserva una copia del intento y se ofrece Verificar en servidor. Los campos se bloquean mientras se verifica ese intento, para no confundirlo con cambios posteriores. |
| Verificación encuentra mismo orden/ID y datos del intento | Identifica el registro existente y no reenvía POST/PUT. No afirma autoría del registro: informa coincidencia en el servidor. |
| Verificación encuentra ese orden con otros datos | Mantiene formulario y pide revisar/corregir, sin sobreescribir ni reenviar automáticamente. |
| Verificación no confirma los datos | Mantiene formulario; habilita reintento explícito sobre el mismo BORRADOR. No guarda por sí sola. |
| Verificación falla | Conserva intento y bloquea reenvío; permite volver a consultar. |

Las verificaciones y recargas posteriores a un guardado usan GET forzado de Pasos. No reutilizan un GET que hubiera comenzado antes de la escritura ni hacen fallback a localStorage para certificar el resultado. La deduplicación normal de GET de otras lecturas permanece disponible.

Un formulario de Paso pendiente no puede sustituirse al abrir otro Paso. Una operación ya confirmada limpia su estado pendiente y no dispara aviso de cambios sin guardar. El fallo de recarga de datos confirmados se distingue de un Paso no guardado.

## 4. Cambios sin guardar

Se usa CanDeactivate de Angular en `/procedimientos/nuevo`, `/:id/editar` y `/:id`. No se agregó el guard a otros módulos.

- Cabecera: compara valores con la última carga/confirmación. No pregunta si está limpio o si el cambio se revirtió; sí si hay edición pendiente o un alta sin verificar.
- Paso: compara orden, instrucción y crítico con la apertura del editor; incluye el intento pendiente de verificar. Cancelar/cerrar modal pide confirmación antes de descartarlo.
- Navegación mientras hay escritura/verificación en curso se bloquea hasta terminar, evitando abandonar una operación sin conocer su resultado. No muestra una confirmación de descarte durante esa escritura.
- beforeunload solicita el aviso nativo al cerrar/recargar con cambios u operación pendiente. El navegador controla el texto y puede no mostrarlo, especialmente sin interacción previa, en móvil o ante cierre forzado.
- Después de guardar, la base de comparación/reset es actualizada; las confirmaciones de salida no tratan registros persistidos como datos pendientes.

Los datos pendientes permanecen en memoria de la pantalla. Si se acepta el descarte o se fuerza cierre, no hay autosave/importación local ni recuperación garantizada del texto después del cierre. Esto evita incorporar persistencia local nueva sin acuerdo y debe comprobarse en UAT. La protección de cambios cubre cabecera y editor de Paso de este alcance.

## 5. Publicación y QA-013

Reglas Backend conservadas sin modificaciones:

- Se permite publicar sin pasos.
- Ejecutar requiere PUBLICADO y al menos un Paso.
- Crear/editar Pasos requiere BORRADOR.
- Solo ADMINISTRADOR publica; los técnicos conservan permisos actuales de creación/edición de BORRADOR.
- No hay vuelta a BORRADOR o nuevas transiciones, eliminación física ni cambio del histórico.

Detalle muestra advertencia informativa si está vacío y mantiene el botón Publicar. Listado consulta Pasos antes de enviar PATCH para advertir si está vacío; si esa consulta informativa falla, advierte que no se pudo comprobar y **no bloquea la publicación permitida**. Ninguna comprobación de mínimo se añadió al Backend.

El aviso explica que, al publicar vacío, no podrá ejecutarse y el estado PUBLICADO no admite edición de Pasos. No se promete poder añadir pasos después de publicar. Un Paso pendiente del formulario debe guardarse o descartarse antes de publicar; esto evita perder edición local, no establece una cantidad mínima para publicación.

Se conservan posición vs orden, saltos permitidos, indicación crítica, errores HTTP y los casos BORRADOR de QA-013. Se reusan los mismos métodos de guardado; no se duplica lógica Backend.

## 6. Pruebas ejecutadas

| Validación local | Resultado final |
|---|---|
| Backend, Java21: `mvnw.cmd -o test` | **645 pruebas; 0 fallos, 0 errores, 0 omitidas. BUILD SUCCESS.** |
| Angular: `npm test -- --watch=false --browsers=ChromeHeadless` | **174 pruebas satisfactorias**, salida 0. |
| Angular: `npm run build -- --configuration production` | Correcto, salida 0. Bundle inicial **1,37 MB**. |
| `git diff --check` sobre archivos relacionados | Sin defectos de whitespace; avisos Git de normalización futura LF→CRLF. |
| SHA256 agregado de fuentes Backend antes/después | Coincidente: `A3E21C277694A11E56949D9C6AE39B709161D7C430A9510DE2D01A9543F590FE`. Incluye Java, tests y recursos. |

Se añadieron **33 pruebas Angular** respecto a las 141 de QA-013. Backend no necesita nuevos tests de comportamiento porque no se cambió su código; se ejecutó toda su regresión. Las suites completas de QA-009 a QA-013 siguen pasando.

Casos nuevos: blanco/longitud; descripción opcional; ID inválido; alta/PUT; fallo 400 y alta incierta; doble envío; navegación posterior a alta fallida sin repetir POST; GET pendiente/fallido/recuperado; PUBLICADO; comparación limpia/revertida; guard y beforeunload; fallo de Paso intermedio; recuperación; GET posterior fallido; respuesta perdida confirmada/no confirmada/conflictiva; no reenvío mientras falla verificación; descarte; publicación vacía en detalle/listado y consulta informativa fallida; status HTTP y GET forzado.

Las pruebas Angular usan servicios simulados o HttpTestingController. Los tests Backend existentes usan los mecanismos locales previos. **No se ejecutaron pruebas contra MySQL real ni STAGING.** No confundir una simulación de pérdida de respuesta con prueba de commit/rollback real con varias conexiones.

Advertencias persistentes:

- CSS Inicio 18,62 kB frente a warning 16 kB.
- CSS Detalle de Conocimiento 22,53 kB frente a warning 16 kB.
- Karma mantiene avisos de disabled en formularios reactivos y cierre de ChromeHeadless tras timeout/SIGKILL; suite completada y proceso con salida 0.
- Backend mantiene aviso Mockito de auto-attachment en pruebas.

No se modificaron budgets, optimización de producción, dependencias ni configuración de infraestructura.

Artefacto Angular verificado:

`C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/dist/buscador-errores/browser/index.html`

Logs locales: Angular `.angular/cache/qa014-tests.log`, `.angular/cache/qa014-build.log`; Backend `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/target/qa014-tests.log`. No distribuir logs de pruebas. No se generó un JAR nuevo para QA-014: Backend permanece sin cambios.

## 7. Riesgos pendientes y UAT

1. **Sin idempotencia Backend:** esta implementación evita reenvíos ciegos de operaciones confirmadas o inciertas dentro de la pantalla; no garantiza ausencia global de duplicados entre navegadores/usuarios. El alta de cabecera con respuesta perdida requiere revisión manual del listado/servidor. El nombre no se trata como identificador único.
2. **Ventana de concurrencia:** una consulta sin registro puede coincidir con una escritura anterior todavía en curso. El reintento es explícito, no automático, y debe respetar los controles Backend existentes. La efectividad de bloqueo padre/orden/FK/UNIQUE necesita MySQL aislado autorizado; no se asumieron restricciones físicas.
3. **Coincidencia de Paso:** orden/ID y contenido permiten evitar repetir un registro visible; no prueban quién lo creó. Edición concurrente sigue sin @Version/ETag. No se introdujeron reglas nuevas de unicidad o snapshots.
4. **Estado informativo:** la advertencia de publicación usa una lectura anterior al PATCH y puede quedar obsoleta con otras sesiones. Es informativa y no altera el contrato. Un PUBLICADO vacío seguirá sin poder ejecutarse ni recibir nuevos Pasos por las reglas existentes.
5. **Navegador:** validar aviso nativo con interacción previa, recarga/cierre móvil/desktop y sus límites. Aceptar descarte conserva datos persistidos, pero no conserva texto local no guardado.
6. **Cachés previas:** el listado general mantiene el comportamiento de caché previo; una revisión manual de un alta incierta requiere datos actuales del servidor, no concluir ausencia por una lista cacheada cuando la red falla. La verificación automática de Pasos sí utiliza HTTP forzado sin fallback.
7. **Despliegue consolidado pendiente:** no hay SQL QA-014. Se conservan las dependencias de migraciones QA-009 y QA-012 del Backend consolidado; no se ejecutaron. Validar esquema y coordinar Backend/Angular solo con autorización posterior. El ajuste de Responsables pendiente de QA-012 tampoco se modificó.

UAT recomendada:

- ADMINISTRADOR y TECNICO: crear BORRADOR, editar cabecera, precarga fallida/reintento; PUBLICADO solo lectura.
- Doble clic durante POST y navegación cancelada después de alta: ID confirmado, Continuar y sin otro POST de creación.
- Dos Pasos confirmados y tercero fallido: identificar IDs/orden, conservar texto, recuperar dentro del mismo BORRADOR.
- Simular respuesta perdida y verificar: ya existente → no POST; ausente → reintento explícito; consulta caída → mantener bloqueo y datos.
- GET posterior fallido: conservar Paso confirmado y usar Actualizar listado sin duplicarlo.
- Dirty/pristine/cambio revertido, Cancelar modal, otra ruta, recarga/cierre; no aviso después de guardado confirmado.
- Publicar vacío desde listado/detalle: aviso visible, PATCH permitido, ejecución rechazada conforme a reglas existentes.
- Posición/orden con saltos; móvil, teclado, zoom y contraste en formulario con estilos reutilizados.

**QA-014 completado localmente y detenido para revisión. Sin migraciones ni despliegue; no se avanzó a otro módulo.**
