# Formulario de pasos simplificado

## Interfaz

Se sustituyeron las secciones A–E por Información del paso, Acción del paso y Configuración del correo (solo para acciones de correo). La pregunta «¿Qué debe hacer el técnico?» ofrece Seguir instrucciones, Solicitar evidencias y Enviar correo. Aprobación, condición de finalización, plazo y recordatorios se conservan en Opciones avanzadas, cuando corresponden.

Se reutilizan el editor de destinatarios, servicios y endpoints existentes. El diálogo mantiene los botones principales y añade nombre accesible y control de foco con el CDK ya instalado. Las acciones se distribuyen en tres columnas y pasan a una columna en móvil.

## Compatibilidad

- No hay cambios de backend, SQL, registros históricos ni lógica de envío.
- INFORMATIVO se muestra como Seguir instrucciones, conservando su valor y finalización. AUTOMATICO muestra un aviso de su acción actual y permite editar sus opciones sin convertirlo automáticamente.
- Los alias MANUAL y VALIDACION_EXTERNA conservan sus valores mientras no se elija otra acción.
- Cambiar de acción guarda en memoria el borrador de configuración de cada acción durante la apertura del formulario. Volver recupera sus evidencias, finalización y recordatorios. Cancelar conserva el comportamiento habitual y no persiste estos borradores.
- Marcar explícitamente un paso como crítico mantiene aprobación humana; si antes finalizaba al mostrar información, pasa a exigir confirmación. No se modifica una modalidad por abrir el formulario.
- Continúan las restricciones existentes para edición de borradores y protección de revisiones publicadas.

## Archivos

- `frontend-dimabug/src/app/shared/ui/configuracion-paso.component.ts`, `.html`, `.css` y `.spec.ts`.
- `frontend-dimabug/src/app/core/models/configuracion-paso.model.ts`: conserva la acción automática recibida al inicializar el formulario.
- `frontend-dimabug/src/app/features/procedimiento/procedimiento-detalle.component.html`, `.css`, `.ts` y `pasos-qa013.component.spec.ts`.

## Comprobaciones

Las pruebas nuevas comprueban tres opciones visibles, correo y evidencias condicionales, conservación de borradores al alternar, modalidades históricas, aprobación crítica y guardado mediante servicios existentes. Se comprueba la distribución CSS en documentos de navegador de 360 y 960 px y ausencia de desbordamiento horizontal. No se envían correos ni se crean ejecuciones en staging durante estas pruebas.

Resultado: 392 pruebas Angular aprobadas. Compilación de producción correcta. La persistencia se comprobó mediante el contrato de guardado del frontend con servicios simulados, sin editar datos reales de staging. No se requieren migraciones ni reinicio de Spring Boot.
