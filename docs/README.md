# Documentación vigente de DimaBug

Referencia: **2026-11-09**. Este índice y las guías canónicas sustituyen las afirmaciones de estado de la auditoría inicial del 2026-11-06. Los informes fechados de auditoría e implementación se conservan como evidencia histórica; sus conteos no se presentan como resultados actuales.

## Estado actual

IMPLEMENTADO describe código presente; VALIDADO especifica la evidencia y su alcance; PENDIENTE identifica lo que no está acreditado. Ninguna fila implica aceptación institucional o GO de producción.

| Área | IMPLEMENTADO | VALIDADO | PENDIENTE |
|---|---|---|---|
| Identidad y administración | JWT/roles, organización y catálogos | Suites de permisos y servicios | Recuperación de contraseña backend; endurecimiento y operación de producción |
| Conocimiento y tickets | Soluciones, estados/asignaciones, adjuntos y flujos de tickets | Pruebas HTTP/JPA/servicios | UAT completo y auditoría de riesgos históricos aún aplicables |
| Procedimientos | Revisión administrativa, pasos, hardware opcional, materiales | Edición/versionado y permisos en H2; comprobaciones locales documentadas | Aprovisionamiento reproducible completo de MySQL desde cero |
| Ejecuciones | Contrato capturado de pasos, criticidad, progreso, historial y materiales históricos | Suites automatizadas; usuario confirmó recepción de fotografías en ejecución local | Certificación integral de concurrencia/carga en producción |
| Evidencias | Solicitud, recepción manual de fotos, aprobación/rechazo y rondas | HTTP/multipart, formatos, duplicados, bloqueo crítico e historial | Recepción automática desde la bandeja Gmail (no implementada) |
| Gmail/OAuth | gmail.send, state/PKCE, renovación, cifrado de tokens, revocación y prueba administrativa | Mocks/H2; usuario confirmó recepción de correos reales durante tareas anteriores | Configuración/operación de Workspace y despliegue final; no enviar real en QA |
| Portal público | Soluciones elegibles, materiales autorizados, API separada y límites | API anónima/privada, archivos, router, accesibilidad básica y responsive | Revisión editorial del contenido público; protección distribuida si hay varias instancias |
| Chatbot | Búsqueda SQL de soluciones de autoservicio autorizadas | Pruebas de filtrado y acceso público | No hay LLM externo; no se documenta como IA generativa |
| NFC/QR | Ruta estable por solución y copiar enlace con base configurable | Con/sin sesión, URLs inválidas, permisos y viewport móvil | Dominio definitivo, programación y escaneo de etiquetas físicas |
| Funciones locales | Favoritos/avisos y pantallas demostrativas conservadas | Alcance de pruebas de sus componentes | Sincronización/entrega remota completa y reportes reales |

## Guías canónicas

- [Proyecto y alcance](01_Proyecto/Descripcion_Proyecto.md) · [Objetivos](01_Proyecto/Objetivos_Alcance.md)
- [Requisitos reconstruidos](02_Requisitos/Requisitos_Funcionales.md) · [Casos de uso](02_Requisitos/Casos_Uso.md)
- [Arquitectura general](03_Arquitectura/Arquitectura_General.md) · [Backend](03_Arquitectura/Arquitectura_Backend.md) · [Frontend](03_Arquitectura/Arquitectura_Frontend.md)
- [Datos y modelo](04_Base_Datos/Modelo_Relacional.md) · [Diccionario](04_Base_Datos/Diccionario_Datos.md) · [Migraciones](04_Base_Datos/Migraciones.md)
- [API](05_API/Endpoints.md) · [DTO](05_API/Contratos_DTO.md) · [Autenticación](05_API/Autenticacion.md)
- [Seguridad](06_Seguridad/Arquitectura_Seguridad.md) · [Controles y límites](06_Seguridad/Controles_Seguridad.md)
- [Estrategia QA](07_Pruebas/Estrategia_Pruebas.md) · [Resultados](07_Pruebas/Resultados_Pruebas.md)
- [Instalación](08_Despliegue/Instalacion_Local.md) · [Variables](08_Despliegue/Variables_Entorno.md) · [Staging](08_Despliegue/Despliegue_STAGING.md)
- [Manual de usuario](09_Manuales/Manual_Usuario.md) · [Manual técnico](09_Manuales/Manual_Tecnico.md)

## Detalles de implementación reutilizados

- [Edición y revisiones de procedimientos](implementacion/automatizacion/GUIA_EDICION_ADMINISTRATIVA.md)
- [Recepción/aprobación de evidencias](implementacion/automatizacion/RECEPCION_MANUAL_EVIDENCIAS.md)
- [Materiales durante ejecuciones](implementacion/automatizacion/MATERIALES_APOYO_EN_EJECUCION.md)
- [Gmail API y OAuth](implementacion/automatizacion/GUIA_GMAIL_API_OAUTH.md) · [Prueba administrativa](implementacion/automatizacion/PRUEBA_ADMINISTRATIVA_GMAIL_API.md)
- [Portal público](implementacion/automatizacion/PORTAL_PUBLICO_AUTOSERVICIO.md)
- [Enlaces NFC/QR](implementacion/automatizacion/NFC_SOLUCIONES_PUBLICAS.md)

## Evidencia y exclusiones

Las comprobaciones de consolidación no envían correo real, no aplican migraciones ni escriben en staging. Los logs/XML generados permanecen ignorados; se publica únicamente un resumen saneado de resultados. Las entregas académicas generadas y sus copias se conservan localmente, fuera de Git; las fuentes vigentes están en `docs/`.

Las auditorías anteriores conservan su fecha y no prueban que todos sus hallazgos se hayan cerrado. Persisten límites de CI remoto, UAT, pentest, restauración y operación de producción.
