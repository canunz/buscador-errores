# Manual técnico

La fuente de configuración y operación está centralizada en [instalación](../08_Despliegue/Instalacion_Local.md), [variables](../08_Despliegue/Variables_Entorno.md), [migraciones](../04_Base_Datos/Migraciones.md) y [seguridad](../06_Seguridad/Arquitectura_Seguridad.md).

- Mantener Java 21 y los lockfiles/POM. Backend `mvnw.cmd package`; frontend tests Chrome Headless y `npm run build`.
- No incorporar secretos en properties, environments, argumentos compartidos o commits. Habilitar Gmail requiere configuración externa; no sustituir JWT ni ampliar scopes para leer evidencias.
- No editar definiciones publicadas utilizadas por ejecuciones. Los contratos de pasos y recursos referencian la versión original.
- Recepción de evidencias es manual autenticada; normalizar/validar antes de persistir; conservar rondas y auditoría. No reintentar un envío incierto como si fuera fallo definitivo.
- La API pública debe comprobar padre/solución/material en cada consulta, no solo en el listado. Los controles de UI no otorgan publicación.
- Los límites de tráfico son locales por instancia; preparar controles de borde para una topología distribuida.
- Los adjuntos y tokens cifrados necesitan conservación/recuperación operativa; no se certificó restauración ni hay DDL inicial completo.

Los helpers locales de diagnóstico/reinicio son herramientas operativas con validaciones, no forman parte del arranque normal. No ejecutarlos durante QA o publicación de Git. Las auditorías fechadas conservan hallazgos pendientes salvo cierre documentado específico.

[Índice vigente](../README.md).
