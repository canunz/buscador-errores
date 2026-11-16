> Informe/guía de su fase de implementación. El [estado consolidado](../../README.md) y las guías posteriores de recepción/portal distinguen las validaciones más recientes. Los defaults versionados no acreditan el estado privado de una instancia.

# Automatización de procedimientos: primer incremento

> Registro histórico del primer incremento. Para la acción SOLICITAR_EVIDENCIA implementada posteriormente, consultar [GUIA_FLUJO_EVIDENCIAS.md](GUIA_FLUJO_EVIDENCIAS.md) y sus resultados; los pendientes descritos aquí corresponden a la fecha de este primer corte.

Fecha: 7 de noviembre de 2026. Propuesta aprobada por el usuario para comenzar por etapas. **El MVP completo todavía no está implementado ni desplegado.** Este incremento entrega el catálogo del piloto y la base de persistencia/UX; AUT-01…AUT-07 no se consideran historias terminadas.

## Decisiones confirmadas por el usuario

- Dominio de responsables del piloto: `dimarsa.cl`, comparado exactamente. No incluye subdominios, dominios parecidos o acceso a otros buzones.
- Fuente del responsable: catálogo administrado dentro de DimaBug. No se reutilizan departamentos como sucursales ni se inventan contactos reales.
- El paso final de folios termina al **enviar la solicitud a Control Cajas**. Cuando exista la integración, su condición será aceptación del envío por Gmail, no entrega acreditada, carga de folios o POS listo para ventas. Ante resultado incierto de envío no se completará por un timeout.
- No se ejecutan migraciones, envíos reales, autorización OAuth ni despliegues en este incremento.

## Cambios implementados

1. **Catálogo de sucursales:** código único normalizado, nombre, responsable, correo `@dimarsa.cl`, estado y versión. Alta, edición y activación/desactivación; no hay eliminación física. Código inactivo sigue reservado. Backend rechaza controles/CRLF, listas de destinatarios, dominios falsos, subdominios, direcciones decoradas y versiones obsoletas. La validación del dominio no acredita identidad del remitente.
2. **Administración Angular:** pantalla `/admin/sucursales`, enlace desde Administración, formulario, errores, conflicto de edición sin pérdida de borrador y protección de salida con cambios pendientes. Solo administrador en router y API. La consulta operacional muestra sucursales activas sin exponer correo ni versión del catálogo.
3. **Contrato capturado por ejecución:** nuevas ejecuciones manuales conservan orden, instrucción, criticidad, modo MANUAL y versión del contrato en `se_ejecucion_paso`. Consultas usan esa copia. Filas anteriores permanecen sin contrato; mantienen la lectura legada, sin inventar evidencias históricas.
4. **Estados y revisión:** ocho estados detallados, coherencia con `cumplido` y revisión persistida del paso. El PATCH acepta `versionEsperada`; una versión obsoleta responde 409. Clientes legados pueden seguir omitiéndola: esa compatibilidad no equivale a protección de edición obsoleta para esos clientes. La revisión es de aplicación, no una anotación JPA `@Version` del paso.
5. **Coordinación transaccional:** editar paso, completar y cancelar adquieren el mismo bloqueo pesimista de cabecera antes de leer el agregado. Revisión, estado, fecha, observación y proyección booleana se guardan juntos. El bloqueo requiere la transacción del caso de uso; las escrituras externas que eludan ese caso de uso no quedan cubiertas.
6. **Defensa del PATCH legado:** un contrato no MANUAL no puede completarse/desmarcarse con un booleano. Guardar solo una observación preserva su estado detallado. Los cuatro modos están representados en el contrato, pero esta etapa **no ofrece configuración de modos no manuales ni su motor de ejecución**.
7. **Checklist:** no marca ni avanza antes de confirmación del backend; bloquea doble mutación local; guarda observaciones tras 800 ms sin reenviar un booleano viejo; conserva texto escrito durante una petición; consulta versiones frescas al sincronizar y ante errores. Consulta de fondo cada 20 s con pantalla visible y ejecución abierta, sin desplazar el foco. Impide cerrar con notas sin guardar y protege la salida durante un guardado.
8. **Datos en caché:** al abrir el checklist se consulta al servidor antes de presentar datos privados. Una consulta denegada no deja visible contenido de localStorage de otra sesión. Esto verifica el checklist; no constituye una auditoría completa del caché del resto de la aplicación.

La criticidad continúa siendo independiente del modo. El marcado manual existente sigue siendo explícito. **No se ha implementado todavía una aprobación crítica respaldada por fotos o identidad Google**; no interpretar la existencia del enum o del snapshot como ese control ya terminado.

## Contratos nuevos y ampliados

| Ruta | Permiso / comportamiento |
|---|---|
| `GET /api/admin/sucursales` | Administrador; catálogo completo |
| `POST /api/admin/sucursales` | Administrador; código, nombre, responsableNombre y responsableEmail |
| `PUT /api/admin/sucursales/{id}` | Administrador; datos y versión leída |
| `PATCH /api/admin/sucursales/{id}/estado` | Administrador; activa y versión obligatorias |
| `GET /api/sucursales` | A/T/S; activas, sin correo ni revisión |
| `PATCH /api/ejecuciones/{id}/pasos/{pasoId}` | Mismo permiso y pertenencia; añade versionEsperada opcional |
| Respuesta de paso | Añade estado, version, contratoVersion y modoEjecucion; campos previos conservados |

La tabla nueva es `u_sucursal`. No se cambian responsables/departamentos previos ni se precargan datos corporativos. Los DTO de sucursal no conceden privilegios mediante campos de actor o usuario asignables.

## Validación realizada

- Backend: **966 pruebas, 0 fallos, 0 errores, 0 omitidas**, Maven offline; 46 casos nuevos respecto de las 920 pruebas previas. Regresión completa final: BUILD SUCCESS, 7/11/2026 11:13:15 -03. No fue un clean build.
- Frontend: **309 pruebas correctas**, ChromeHeadless 157; 25 casos nuevos respecto de las 284 previas. Pruebas finales incluyen autorización antes de mostrar caché y sincronización de borradores.
- Compilación Angular de desarrollo y comprobación TypeScript de aplicación/especificaciones registradas en [EVIDENCIA_VALIDACION.json](EVIDENCIA_VALIDACION.json).
- JPA real sobre **H2 efímero**, con transacciones separadas: snapshots, rollback, misma revisión desde dos sesiones, cierre contra desmarcado, revisión del catálogo y dos altas simultáneas del mismo código. H2 no acredita el dialecto, aislamiento o esquema del MySQL de destino.
- MVC verifica 401/403, administración exclusiva, lectura operacional A/T/S sin correo y ausencia de borrado físico.
- El primer arranque de Chrome en contenedor falló; el primer intento de Java se bloqueó al cargar el agente de Mockito. Se repitieron fuera de esa restricción, con mocks/H2. Hubo una corrección de compilación del test (nombre `Timeout`) y otra de traducción de la violación de unicidad en el contexto JPA aislado. Los resultados anteriores no se presentan como éxitos.

## Esquema y preparación para QA

Los dos scripts aditivos se entregan **sin ejecutar**:

- [01_contrato_paso_mysql.sql](01_contrato_paso_mysql.sql): siete columnas adicionales en `se_ejecucion_paso`, sin backfill de snapshots.
- [02_sucursales_mysql.sql](02_sucursales_mysql.sql): catálogo con unicidad de código y revisión.

**No desplegar este backend sobre el esquema anterior sin revisar y aplicar estos cambios en un ambiente autorizado.** Los mapeos JPA nuevos requieren esas columnas/tabla incluso aunque Gmail no esté disponible. No se presupone que una feature flag vuelva compatible un esquema antiguo. Los scripts permanecen fuera de `src/main/resources/db/migration`; no se ha habilitado un ejecutor de migraciones ni modificado configuración de conexión.

Antes del despliegue faltan verificación MySQL aislada, respaldo/restauración, revisión de permisos y auditoría de cambios del directorio, y aceptación visual/operacional. No se afirma aptitud para producción por las pruebas locales.

## Trabajo pendiente del MVP aprobado

- AUT-01: OAuth de buzón dedicado, referencias a secretos, reconexión y adaptador Gmail. Aún no implementado ni autorizado un buzón real.
- AUT-02: inicio con sucursal/POS/idempotencia, contexto capturado del responsable y solicitud durable.
- AUT-03: sincronización incremental, cursor, MIME/adjuntos, cuarentena, escaneo e identidad OIDC del responsable.
- AUT-04: aprobación/rechazo por ronda y versión, auditoría, nueva solicitud y transición atómica.
- AUT-05: outbox, leases, conciliación de resultados inciertos y recordatorios de días 1/3/5 en horario definido.
- AUT-06: configuración en BORRADOR, opt-in y feature flag, predecesores, coordinador y cierre por condiciones. Los estados/modelos y las defensas de este incremento son base parcial.
- AUT-07: selección de contexto al iniciar, revisión de fotos, estados externos completos, métricas y UAT. Autosave/confirmación/sincronización ya están implementados como parte de esta historia.

Para integrar OAuth se utilizará una biblioteca mantenida, conservando el JWT propio y la identidad Google como validaciones diferentes. Referencias: [OAuth2 Client de Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html), [validación JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html). No se añadieron dependencias en este incremento.

El backlog y diagnóstico originales se conservan como propuesta histórica; este registro distingue las capacidades implementadas de las que siguen pendientes. No se modificó el paquete académico ni se recrearon diagramas/PPT anteriores.
