# Modelo relacional y versiones

La fuente de mapeo vigente son las entidades JPA y los SQL revisados de esta entrega. El diagrama `Diagramas/DimaBug_Modelo_Relacional_v2.drawio` y sus renders representan la fase anterior: **no incluyen todas las ampliaciones recientes** y no certifican el esquema físico actual. Se conservan como artefacto histórico identificado, no como modelo actualizado de producción.

Relaciones relevantes actuales:
- Usuario/rol y organización; conocimiento/solución y asignaciones; tickets y adjuntos.
- Procedimiento con revisión/origen/vigencia y hardware opcional; pasos y materiales de esa definición.
- Ejecución asociada al procedimiento concreto; instancias de pasos con contrato capturado, estado y revisión.
- Solicitud de evidencia por instancia de paso/ronda; fotografías y eventos con actor/fecha.
- Cuenta OAuth Gmail con tokens cifrados y registros de autorización/envío; prueba administrativa separada de las ejecuciones.
- Material de solución con autorización individual; no se convierten materiales privados de conocimiento/pasos en públicos automáticamente.

La edición publicada genera otra revisión con IDs nuevos. No se resuelven recursos históricos buscando silenciosamente la revisión vigente. La configuración de correo sin sucursal utiliza relaciones opcionales; no elimina los catálogos existentes.

No se incluye un DDL inicial completo. Los tests H2 no prueban paridad completa de MySQL ni restauración. Véase el catálogo de migraciones antes de provisionar.

[Estado global y evidencia](../README.md).
