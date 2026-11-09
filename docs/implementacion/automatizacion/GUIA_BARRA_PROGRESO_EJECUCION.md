# Barra de progreso de ejecución

## Diagnóstico previo

El bloque existente pertenece a `EjecucionChecklistComponent` y estaba al final del cuerpo de pasos. Sus estilos están en `ejecucion-checklist.component.css`, junto con `tickets-comun.css`. Se revisaron también `shell-layout.component.html`, su CSS y los estilos globales.

Los contenedores principales no restringen el desplazamiento con overflow. El encabezado general y la franja azul no son fijos. El overflow de la franja azul es interno y no es ancestro del bloque. La lista lateral tenía su propio sticky a 16 px, que competiría con una barra superior de altura variable.

## Cambio

Se movió el único bloque de progreso debajo de la franja azul, antes de alertas y listado. Conserva las mismas condiciones de visibilidad, eventos, indicadores y expresiones disabled. No se modificó el TypeScript de ejecución para este cambio.

La barra usa sticky con separación superior de 12 px (8 px en móvil), fondo opaco del tema y z-index 10, por debajo de diálogos y menús. Se desactiva su animación de entrada para evitar desplazamientos visuales. La lista lateral permanece en flujo normal para no competir por el mismo espacio. Los botones se adaptan a anchuras pequeñas y permiten saltos de línea.

## Archivos de este cambio

- `frontend-dimabug/src/app/features/ejecucion/ejecucion-checklist.component.html`.
- `frontend-dimabug/src/app/features/ejecucion/ejecucion-checklist.component.css`.
- `frontend-dimabug/src/app/features/ejecucion/ejecucion-progreso-layout.component.spec.ts`.

No hay migraciones, cambios de backend ni nuevas dependencias.

## Validación

Se añadieron pruebas de orden en el DOM, ausencia de duplicados, botones y bloqueo por pasos pendientes o automatización en curso, estados de carga/error y ejecución finalizada. Las pruebas de navegador a 360 y 1280 px desplazan un documento con el componente renderizado y comprueban posición sticky real, separación respecto a la franja y ausencia de desbordamiento horizontal. No se modifican ejecuciones reales ni se envían correos.

Resultado: 398 pruebas Angular aprobadas y compilación de producción correcta; persisten las advertencias previas de presupuesto del bundle y CSS de inicio/conocimiento. Angular responde HTTP 200 en localhost:4200.
