# Datos del solicitante — portal /solicitudes (local)

## Catálogos comprobados y reutilizados
`u_sucursal` / `SucursalEntity`: id, nombre y estado `sucursal_activa` disponibles. `u_departamento` / `DepartamentoEntity`: id, nombre y `departamento_estado` disponibles. No existe en el modelo revisado una asociación obligatoria sucursal–departamento; no se inventó una relación. Se reutilizan ambos catálogos; no se crean tablas duplicadas ni se insertan opciones en MySQL.

El portal usa GET `/api/tickets/solicitudes/catalogos`, autenticado y expresamente permitido para el alcance básico. Solo devuelve opciones activas con `{id,nombre}`. La API pública mínima del módulo identidad y su adaptador de tickets respetan la arquitectura hexagonal: no se usan desde tickets los repositorios internos de identidad. Los endpoints completos de sucursales, departamentos y trabajadores siguen bloqueados para autorregistros.

## Formulario y validaciones
Dos secciones: Datos del solicitante y Descripción del problema. Se conservan el logo existente con mascota, título, descripción, fotos, historial y prioridad oculta.

- Nombre completo: obligatorio, máximo 150 caracteres.
- Sucursal y departamento: obligatorios; selección entre opciones activas. El servidor vuelve a consultarlas y obtiene sus nombres canónicos, sin aceptar etiquetas del cliente.
- Contacto: obligatorio, máximo 50 caracteres; admite +56 9 1234 5678, teléfonos fijos, 600 y anexos como 1234, anexo 1234 o ext. 1234. Formato permisivo de 2–18 dígitos totales incluyendo un eventual anexo, sin letras fuera de los prefijos admitidos.
- IP o AnyDesk: opcional, máximo 100 caracteres. IPv4 estricta, IPv6 hexadecimal (incluye compresión ::) o ID AnyDesk de 9–10 dígitos, con espacios opcionales. No se resuelven hostnames, no se admiten URLs, alias AnyDesk ni identificadores de interfaz IPv6. No se establece ninguna conexión al valor declarado.
- Código de usuario: opcional, máximo 80 caracteres; no representa el id del usuario DimaBug ni altera permisos. Se indica no ingresar contraseñas.

Angular valida antes de enviar; Spring aplica Bean Validation al multipart y el dominio valida formatos y longitudes. Los selectores vacíos o con error mantienen el envío bloqueado y permiten reintentar. La prioridad se fija en MEDIA y el solicitante real se obtiene exclusivamente de la sesión. Parámetros extra como solicitanteId, asignadoId, prioridad o nombres inventados de catálogo no se usan.

La ruta JSON antigua rechaza la creación de un autorregistro sin datos del solicitante: no permite eludir los nuevos obligatorios. Los clientes internos de cuentas administradas conservan la creación anterior. Los tickets antiguos se pueden leer sin snapshot.

## Persistencia y privacidad
Se guarda un snapshot por ticket, en la misma transacción de creación y adjuntos. No se modifica la ficha permanente ni `u_usuario`. Las etiquetas de catálogo son una copia histórica: un cambio posterior del catálogo no reescribe solicitudes antiguas. Nombre y ubicación son datos declarados, no prueba de identidad o pertenencia ni fuente de autorización.

JPA/mapper y la consulta JDBC rápida de soporte conservan el snapshot, incluso al cambiar prioridad/estado. La proyección básica permite estos datos solo después de autorizar el ticket propio. No se añaden datos de trabajadores a los selectores ni a los resúmenes. El detalle incluye `datosSolicitante`; solo lo reciben usuarios autorizados por las reglas actuales. La interfaz de detalle del portal los muestra como datos registrados en ese ticket. No se amplió en esta tarea la pantalla interna de atención.

Los nuevos datos de contacto y acceso remoto se eliminan de las copias serializadas de TicketService en sessionStorage/localStorage; quedan en memoria durante la sesión y se vuelven a consultar por API. No se añadieron logs de sus valores. Se conservan las comprobaciones de propietario para adjuntos y tickets, y las rutas internas continúan bloqueadas.

## Migración necesaria, preparada pero no aplicada a MySQL
Archivo: `dima-bug-backend/src/main/resources/db/migration/V20261116_01__ticket_datos_solicitante.sql`.

Agrega ocho columnas NULL a `ticket`, sin modificar catálogos, usuarios, roles, prioridad ni solicitante_id:

| Columna | Tipo |
|---|---|
| sol_nombre_completo | VARCHAR(150) |
| sol_sucursal_id | INT |
| sol_sucursal_nombre | VARCHAR(100) |
| sol_departamento_id | INT |
| sol_departamento_nombre | VARCHAR(100) |
| sol_contacto | VARCHAR(50) |
| sol_equipo | VARCHAR(100) |
| sol_codigo_usuario | VARCHAR(80) |

`ck_ticket_sol_datos` admite snapshots totalmente vacíos para tickets históricos o snapshots con los obligatorios presentes e identificadores positivos. Las referencias entre módulos se guardan como ids, conforme al modelo de tickets existente, sin nuevas FK que impidan conservar el snapshot si cambia/elimina un catálogo. La aplicación exige existencia y estado activo al crear la solicitud.

Se ejecutó el SQL del archivo únicamente sobre H2 aislado en memoria: preservación de un ticket anterior, rechazo de snapshot parcial y datos UTF-8. Se probó también persistencia JPA real y lectura JDBC simulada. **No se ejecutó SQL contra MySQL local, staging o producción.** Falta comprobar el DDL en MySQL 8 QA antes de aplicarlo a un entorno persistente.

La versión nueva con Hibernate validate requiere esas columnas antes de arrancar. Antes de un despliegue: respaldar, verificar esquema/aislamiento, probar y aplicar la migración a QA y luego al destino expresamente autorizado. El script es de aplicación única; los ALTER de MySQL pueden confirmar por separado: no reintentar automáticamente un fallo parcial. Inspeccionar el esquema y resolver bajo autorización. Para revertir aplicación, conservar las columnas y datos; no eliminarlos automáticamente. La reversión de registro universal continúa requiriendo su cuarentena previamente diseñada.

## Verificaciones finales
- 186 pruebas backend, cero fallos, errores u omitidas. Incluyen registro universal con H2, multipart autenticado, obligatorios, prioridad forzada, identidad inmutable, ficha del usuario intacta, catálogos sin contactos/inactivos, rechazo de acceso ajeno, preservación de tickets históricos, snapshots, migración y arquitectura.
- 18 pruebas Angular/guards/servicio HTTP, satisfactorias: campos, formatos, errores de carga, reintento, prevención de doble clic, selección segura y exclusión de identificadores/roles/prioridad y datos sensibles de cachés persistentes.
- Compilación Angular production correcta. Las advertencias existentes de CSS y CommonJS de otras funcionalidades no se abordaron.
- Chrome con API sintética exclusivamente local: 1440, 768 y 390 px, dos selectores y ambas secciones presentes, sin selector de prioridad ni desbordamiento horizontal. Se conservó evidencia de vista previa de foto.
- Sin cambios remotos, correos, Gmail/OAuth, roles ni commits. Los cambios locales previos ajenos al portal se conservaron.

Evidencia: `qa/artifacts/solicitudes-datos-solicitante/verification.json`, logs, capturas y `source-hashes.json`. No son artefactos aprobados para desplegar: el checkout contiene otros cambios anteriores que deben excluirse al preparar un candidato.

## Archivos creados o modificados en esta ampliación
- dima-bug-backend/src/main/java/cl/casol/backend/identidad/api/CatalogosSolicitudApi.java
- dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/security/AlcanceBasicoFilter.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/domain/DatosSolicitante.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/domain/Ticket.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/port/in/CrearTicketUseCase.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/port/in/TicketVista.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/port/out/CatalogoSolicitantePort.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/service/CrearSolicitudService.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/service/CrearTicketService.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/integracion/CatalogoSolicitanteAdapter.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/TicketEntity.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/mapper/TicketMapper.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/adapter/DetalleTicketJdbcAdapter.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/SolicitudController.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/dto/DatosSolicitanteRequest.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/dto/TicketDetalleResponse.java
- dima-bug-backend/src/main/resources/db/migration/V20261116_01__ticket_datos_solicitante.sql
- dima-bug-backend/src/test/java/cl/casol/backend/RegistroUniversalIntegrationTest.java
- dima-bug-backend/src/test/java/cl/casol/backend/SolicitudPortalHttpTest.java
- dima-bug-backend/src/test/java/cl/casol/backend/identidad/api/CatalogosSolicitudApiTest.java
- dima-bug-backend/src/test/java/cl/casol/backend/tickets/application/service/CrearSolicitudServiceTest.java
- dima-bug-backend/src/test/java/cl/casol/backend/tickets/domain/DatosSolicitanteTest.java
- dima-bug-backend/src/test/java/cl/casol/backend/tickets/infrastructure/persistence/DatosSolicitantePersistenciaTest.java
- frontend-dimabug/src/app/core/models/datos-solicitante.model.ts
- frontend-dimabug/src/app/core/models/datos-solicitante.model.spec.ts
- frontend-dimabug/src/app/core/models/ticket.model.ts
- frontend-dimabug/src/app/core/services/ticket.service.ts
- frontend-dimabug/src/app/core/services/ticket-solicitud.service.spec.ts
- frontend-dimabug/src/app/features/tickets/solicitudes-basicas.component.ts
- frontend-dimabug/src/app/features/tickets/solicitudes-basicas.component.html
- frontend-dimabug/src/app/features/tickets/solicitudes-basicas.component.css
- frontend-dimabug/src/app/features/tickets/solicitudes-basicas.component.spec.ts

Herramientas y evidencia de esta etapa: qa/artifacts/solicitudes-datos-solicitante/.
