> Referencia de la fase documental inicial. No equivale a aceptación formal ni a una lista exhaustiva de funciones actuales. Véase [estado consolidado](../README.md) y los contratos fuente.

# Errores HTTP

Estado documental: actualizado desde el código auditado el 2026-11-06. Los resultados de pruebas citados corresponden a esa auditoría.


Fuente: GlobalExceptionHandler, TicketExceptionHandler y SecurityConfig. Los DTO de negocio normalmente usan un objeto `message`; los errores de filtro/Servlet pueden tener otro cuerpo. No se promete un formato uniforme para todos los despachos.

| Estado | Contratos encontrados | Acción de cliente |
|---|---|---|
| 400 | DTO inválido, JSON ilegible, regla de negocio, archivo/formato inválido, parámetro Ticket inválido | Corregir entradas, conservar formulario |
| 401 | Falta de autenticación, JWT inválido o credenciales inválidas | Revisar sesión sin repetir credenciales automáticamente |
| 403 | Rol/actor no autorizado, usuario inactivo | Informar falta de permiso, no simular éxito |
| 404 | Recurso inexistente u oculto por política; Ticket ajeno puede responder 404 | No inferir existencia de otro usuario |
| 409 | Duplicado, versión obsoleta, transición de Ticket o conflicto de integridad | Recargar y conciliar, evitar sobrescritura/reintento ciego |
| 413 | Request/archivo supera límite aceptado | Reducir archivo y revisar límites efectivos multipart/proxy |
| 500 | Almacenamiento o limpieza de archivos según handler | Distinguir registro confirmado de limpieza pendiente |
| 503 | Excepción de acceso/conexión a datos según handler global | Mostrar mensaje seguro y revisar servicio |

El estado por éxito se conserva según anotación o ResponseEntity de cada método. No se inventan errores ni payloads observados en un servidor real. Para Requests de creación con respuesta perdida, confirmar si el registro existe antes de reenviar; POST y pasos de procedimiento son operaciones independientes.


## Referencias

[Índice vigente](../README.md) · [Auditoría de origen](../auditoria/AUDITORIA_DOCUMENTAL.md)
