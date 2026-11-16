> Referencia de la fase documental inicial. No equivale a aceptación formal ni a una lista exhaustiva de funciones actuales. Véase [estado consolidado](../README.md) y los contratos fuente.

# Historias de usuario reconstruidas

Estado documental: actualizado desde el código auditado el 2026-11-06. Los resultados de pruebas citados corresponden a esa auditoría.


Las HU siguientes son formulaciones documentales derivadas de funciones presentes. No son una sustitución de historias aprobadas, estimaciones o actas. La numeración es nueva y se relaciona con RF de esta documentación; no se confunde con los nombres históricos Hu01SecurityIntegrationTest y similares.

| HU documental | Historia | Criterio observable candidato | RF |
|---|---|---|---|
| HU-D01 | Como persona con cuenta quiero iniciar sesión para acceder según mi perfil | Login válido emite JWT; inválido no autoriza, sujeto a política de inactivo actual | RF-01 |
| HU-D02 | Como administrador quiero mantener usuarios/catálogos para controlar disponibilidad | Operación autorizada persiste y rechazos no anuncian éxito; último admin protegido | RF-02 a RF-05 |
| HU-D03 | Como usuario quiero encontrar contenido publicado para resolver una falla | Búsqueda filtra publicado y asistente solo ofrece soluciones autoservicio | RF-06, RF-07, RF-15 |
| HU-D04 | Como personal operacional quiero mantener conocimiento y pruebas para reutilizar soluciones | Relaciones, estados, campos y pertenencia validados por los servicios | RF-06, RF-08, RF-09 |
| HU-D05 | Como técnico quiero definir un procedimiento para registrar pasos de trabajo | Cabecera BORRADOR y pasos se confirman incrementalmente, PUBLICADO inmutable | RF-10 |
| HU-D06 | Como ejecutor quiero registrar cumplimiento para dejar seguimiento | Propietario/admin accede; completar exige cumplimiento; concurrencia pendiente | RF-11 |
| HU-D07 | Como usuario quiero reportar y seguir un ticket para recibir atención | Alta autorizada crea ABIERTO e historial; solo actor permitido cambia estados | RF-14 |
| HU-D08 | Como personal de soporte quiero resolver tickets y registrar resultado para reutilizar atención | Resolución exige texto y permiso; notas internas se ocultan al usuario | RF-12, RF-14 |
| HU-D09 | Como editor quiero asociar materiales para apoyar la solución | Tipo/tamaño/pertenencia válidos y descarga autorizada; recuperación pendiente | RF-13 |

Aprobación, prioridad de negocio, estimación, responsable y aceptación de cada historia: [PENDIENTE DE VERIFICACIÓN]. No se fabrican UAT ni capturas para cerrar criterios.


## Referencias

[Índice vigente](../README.md) · [Auditoría de origen](../auditoria/AUDITORIA_DOCUMENTAL.md)
