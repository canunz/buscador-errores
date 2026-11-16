# Diccionario de datos: fuente vigente

Las definiciones completas de campos, longitudes, nullabilidad y enums se mantienen en las entidades de `dima-bug-backend/src/main/java/cl/casol/backend/*/infrastructure/persistence/entity/` y en los SQL. Este índice evita duplicar un volcado desactualizado de todos los campos.

| Área | Datos incorporados/conservados |
|---|---|
| Procedimiento/paso | Datos editoriales, hardware, revisión de configuración, modalidad y condiciones |
| Ejecución/paso | Contrato de instrucciones/criticidad/modalidad/configuración, estado, revisión y observación |
| Solicitud/evidencia | Ejecución/paso/ronda, destinatarios capturados, estado de correo/recepción, imágenes normalizadas y hashes |
| Evento | Solicitud, actor, fecha, tipo y detalle de la acción |
| OAuth Gmail | Estado, tokens cifrados, vencimiento y control de autorización/idempotencia; nunca valores en documentación |
| Prueba administrativa Gmail | Destinatario, actor, estado, fecha, resultado/ID de proveedor y eventos |
| Material de solución | Solución, título, tipo, referencia de archivo/enlace y publicado=false por defecto |

Los nombres físicos se verifican en las entidades y en los scripts, no a partir de este resumen lógico. No se publican filas de usuarios, credenciales, tokens, respaldos ni un dump de staging.

[Estado global y evidencia](../README.md).
