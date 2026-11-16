> **Antecedente de auditoría fechado.** Sus conteos/hallazgos describen la fase original. Para estado vigente y cierres específicos consultar [documentación actual](../README.md).

# Matriz de trazabilidad documental

Estado documental: actualizado desde el código auditado el 2026-11-06. Los resultados de pruebas citados corresponden a esa auditoría.


## Convenciones

Los RF/HU documentales se reconstruyeron del código y siguen pendientes de equivalencia con requisitos aprobados. PASS indica el escenario local respaldado por la auditoría, no cierre E2E/MySQL. Fuente de resultados: COMPROBACIONES.json, 920 backend y 284 Angular. No se repitieron suites por cambios documentales.

| RF | Función | Evidencia fuente | Prueba existente y resultado de la auditoría | Estado / límite |
|---|---|---|---|---|
| RF-01 | Autenticar y consultar sesión | `dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AuthController.java` | `dima-bug-backend/src/test/java/cl/casol/backend/identidad/application/service/AutenticarUsuarioServiceTest.java`: 4 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-02 | Administrar usuarios y consultar roles | `dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/AdministrarUsuariosService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/identidad/application/service/AdministrarUsuariosServiceTest.java`: 13 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-03 | Administrar catálogos y asociaciones | `dima-bug-backend/src/main/java/cl/casol/backend/catalogo/application/service/AdministrarCatalogosService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/catalogo/application/service/AdministrarCatalogosServiceTest.java`: 14 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-04 | Administrar departamentos y contactos | `dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/AdministrarOrganizacionService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/identidad/application/service/AdministrarOrganizacionServiceTest.java`: 24 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-05 | Administrar responsables | `dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/AdministrarResponsablesService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/identidad/application/service/AdministrarResponsablesServiceTest.java`: 18 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-06 | Mantener conocimiento y visibilidad | `dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/MantenerConocimientoService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/MantenerConocimientoServiceTest.java`: 15 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-07 | Buscar contenido publicado mediante índice | `dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java` | `dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapterTest.java`: 4 ejecutadas, 0 fallos, 0 errores | Implementada, pero no verificada en MySQL |
| RF-08 | Mantener soluciones y autoservicio | `dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/AdministrarSolucionService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/AdministrarSolucionServiceTest.java`: 10 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-09 | Administrar y asociar pruebas | `dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/AdministrarPruebasService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/AdministrarPruebasServiceTest.java`: 14 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-10 | Definir y publicar procedimientos con pasos | `dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/application/service/MantenerProcedimientoService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/procedimiento/application/service/MantenerProcedimientoServiceTest.java`: 8 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-11 | Registrar ejecución y cumplimiento | `dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/application/service/GestionarEjecucionService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/ejecucion/application/service/GestionarEjecucionServiceTest.java`: 22 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales; concurrencia pendiente |
| RF-12 | Registrar resultados y efectividad | `dima-bug-backend/src/main/java/cl/casol/backend/seguimiento/application/service/RegistrarEfectividadSolucionService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/seguimiento/application/service/RegistrarEfectividadSolucionServiceTest.java`: 19 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales |
| RF-13 | Gestionar materiales y archivos | `dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/ArchivoMaterialService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/ArchivoMaterialServiceTest.java`: 25 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales; recuperación pendiente |
| RF-14 | Gestionar ciclo de tickets | `dima-bug-backend/src/main/java/cl/casol/backend/tickets/domain/Ticket.java` | `dima-bug-backend/src/test/java/cl/casol/backend/tickets/domain/TicketTest.java`: 56 ejecutadas, 0 fallos, 0 errores | Implementada y verificada en escenarios locales; MySQL pendiente |
| RF-15 | Consultar soluciones autoservicio con asistente | `dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/ConsultarChatbotService.java` | `dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/ConsultarChatbotServiceTest.java`: 3 ejecutadas, 0 fallos, 0 errores | Implementada y verificada como búsqueda, no LLM |

## Elementos no cerrados

| Elemento | Evidencia disponible | Estado |
|---|---|---|
| NFC | Ningún lector/contrato encontrado; Form.NFC es normalización Unicode | No verificable como función del proyecto |
| Snapshot | Guía histórica frente a campos/migración ausentes | Documentado, no encontrado en código |
| CI/MySQL IT | Guías históricas, sin clases/perfil/workflow citados | No verificable en este checkout |
| Recuperación password | UI y requests, sin endpoints | Parcial |
| Notificaciones | Consultas + caché/marcas locales | Implementado en código, entrega integrada no verificada |
| Reportes/Nuevo error | Serie literal / persistencia local | Parcial, no tratar como métricas o recepción compartida reales |
| E2E/UAT/despliegue/restauración | Sin actas/reportes actuales disponibles | [PENDIENTE DE VERIFICACIÓN] |


## Referencias

[Índice vigente](../README.md) · [Auditoría de origen](AUDITORIA_DOCUMENTAL.md)
