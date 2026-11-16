> Referencia histórica conservada. Describe una entrega previa y puede diferir del checkout actual. [Documentación vigente](../../docs/README.md). Los enlaces a archivos ausentes se identifican expresamente; [original íntegro y hash](../../docs/auditoria/historico/README.md).

# Transferencia de Skill 4 — Pruebas a Skill 5 — Despliegue

Fecha de preparación: 2026-10-09. Proyecto: DimaBug MVP.

**Estado: handoff preparado para revisión; Skill 4 mantiene CIERRE CON CONDICIONES.**
No acredita recepción formal, cierre incondicional ni GO de producción. Responsables
de entrega, recepción, operación y autorización de publicación: **por designar**.
Los criterios siguientes son una propuesta de transferencia; el equipo debe
validarlos, pues no se encontró una definición formal completa de ambas Skills.

## 1. Estado transferido y evidencias

| Categoría | Significado | Situación actual |
| --- | --- | --- |
| A. IMPLEMENTADO Y VERIFICADO | Evidencia local de código y pruebas; no certificación del servidor. | Backend, correcciones y escenarios RN-PROC-01 indicados abajo. |
| B. IMPLEMENTADO, NO VERIFICADO EN SERVIDOR | Existe artefacto local, o implementación de infraestructura informada sin comprobación independiente. | Workflow CI y scripts; controles del Droplet solo informados. CI tampoco tiene ejecución remota acreditada. |
| C. PENDIENTE | No existe evidencia suficiente de implementación o comprobación necesaria. | Compatibilidad Angular, provisión real, configuración operativa, primer run CI y aceptación de transferencia. |
| D. RIESGO ACEPTADO | Aprobación explícita, alcance, responsable y vigencia registrados. | **Ninguna aceptación formal acreditada de los riesgos residuales de esta transferencia.** |
| E. RIESGO PENDIENTE DE ACEPTACIÓN | Propuesta documentada sin aprobación. | Recuperación BD/filesystem de materiales. |

La aprobación de RN-PROC-01 es una regla de negocio implementada; no equivale a
aceptar riesgos de archivos ni a aprobar publicación.

Lectura de los XML existentes en `target/surefire-reports` y `target/failsafe-reports`
durante esta transferencia, sin volver a ejecutar pruebas:

| Suite | Clases con XML | PASS | FAIL | ERROR | SKIPPED | Último tiempo Maven documentado |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Habitual: unitarias, adaptadores y slices MVC | 45 | 474 | 0 | 0 | 0 | 28,145 s |
| Integración MySQL temporal | 5 | 45 | 0 | 0 | 0 | 1:38 min |

Ambas BUILD SUCCESS según la [evidencia RN-PROC-01](decision-historial-procedimientos.md#pruebas-y-resultados-reales).
Los XML corroboran conteos, no sustituyen el log Maven ni prueban ejecución CI.
Son resultados locales del árbol probado, no certificación automática de un futuro
commit, JAR, frontend o servidor. Skill 5 debe vincular versión/commit y artefacto
publicable con sus propios resultados y conservar evidencias.

Fuentes principales: [auditoría](auditoria-cobertura-riesgos-skill4.md),
[concurrencia](concurrencia-integracion.md#corrección-autorizada-de-p1-01--p1-02-2026-10-09),
[seguridad](seguridad-integracion.md), [autorización HTTP](autorizacion-http-integracion.md),
[materiales](materiales-integracion.md),
[procedimientos/ejecuciones/resultados](procedimientos-ejecuciones-resultados-integracion.md),
[CI](ci-pruebas-backend.md) y [aceptación propuesta de archivos](aceptacion-riesgo-archivos-skill4.md).

Algunos documentos generales y planes iniciales describen etapas anteriores a
Testcontainers o al ensayo de migraciones. Para estos hechos prevalecen el soporte
actual, los XML y los documentos recientes citados; no se afirma que esos documentos
históricos estén completamente actualizados.

## 2. Arquitectura y módulos implementados

Un proyecto Maven, una aplicación Spring Boot 4.1.1 y Java 21: **monolito modular**
organizado por dominios y capas Domain / Application / Infrastructure. Puertos de
salida y adaptadores JPA, JDBC, JWT, BCrypt y filesystem; controllers llaman a
servicios Application. Hay dependencias Spring en Application y acoplamientos entre
dominios; no hay aislamiento de módulos verificado automáticamente.
Ver [arquitectura](arquitectura.md) y [POM](../pom.xml).

| Dominio | Funcionalidad implementada / alcance |
| --- | --- |
| Catálogo | Sistemas, módulos, hardware y frecuencias. |
| Identidad | Autenticación, usuarios, roles, departamentos, responsables y contactos. |
| Conocimiento | Conocimientos, síntomas, pruebas, causas, soluciones, asignaciones, materiales y búsqueda. |
| Procedimiento | Procedimientos, publicación, pasos y materiales de pasos. |
| Ejecución | Inicio, consultas, checklist, finalización/cancelación y snapshot RN-PROC-01. |
| Seguimiento | Resultados Funcionó/No funcionó y consulta de efectividad de soluciones. |
| Shared | Seguridad, errores HTTP, health y contratos/adapter de archivos. |

`clasificacion` no tiene implementación Java comprobada. Angular no está en este
repositorio; no se transfiere como frontend compilado o probado.

Flujo backend: HTTP → Spring Security/JWT → controller/DTO → Application → puerto
→ adaptador JPA/JDBC/filesystem. JWT stateless, BCrypt, roles ADMINISTRADOR y TECNICO
y restricciones específicas de operación/propietario. No se deben ampliar permisos
en despliegue. La proyección de búsqueda usa JDBC/FULLTEXT y comparte transacciones
con JPA; no basta con generar tablas desde entidades.

Materiales: bytes fuera de MySQL, metadatos y referencias en BD, directorio filesystem
privado. No hay BLOB ni atomicidad distribuida entre ambos sistemas. Las pruebas
usan MySQL 8.0.36 temporal y archivos temporales, sin DEV/producción.

## 3. Correcciones y límites de lo verificado

| Hallazgo | Corrección / evidencia A | Límite que se transfiere |
| --- | --- | --- |
| P1-01: ejecución COMPLETADA con checklist posteriormente desmarcado | Bloqueo por agregado, recarga y validación del estado actual; escenario original e inverso PASS. | Contención/timeouts y otras intercalaciones no son un ensayo de carga del servidor. |
| P1-02: edición obsoleta restaura ELIMINADO y su búsqueda | Lectura actual exclusiva compartida por modificación/estado/eliminación; ELIMINADO y cero proyecciones, PASS. | No garantiza ausencia de toda carrera posible. Corrección sin migración adicional. |
| P1 HTTP 401 en lugar de 403 | Respuesta directa evita despacho ERROR que reautoriza `/error`; JWT real y roles PASS. | Mantener contrato 401/403 detrás del proxy. |
| P2 login revela cuenta inactiva con credenciales inválidas | Validación de contraseña y respuesta genérica; regresión de autenticación PASS. | No certifica rate limiting ni resistencia a enumeración por tiempos. |
| RN-PROC-01 | Captura atómica de instrucción, orden y criticidad; asociación del checklist, legacy explícito y captura concurrente PASS. | Cabecera del procedimiento y materiales no están congelados; Angular y migración real pendientes. |

El rollback JPA/JDBC, fallos SQL controlados, FK y escenarios de compensación están
probados según sus documentos. No se declara cobertura total: la auditoría mantiene,
entre otras brechas, el recorrido real completo Síntoma → Prueba → Causa → Solución
y variantes de pertenencia/indexación. Las brechas deben tener decisión y seguimiento;
no se convierten en defectos confirmados solo por no estar ensayadas.

## 4. Infraestructura informada y arquitectura objetivo

**Información aportada por el equipo; no comprobada en esta tarea:** Droplet
DigitalOcean, Ubuntu 24.04 LTS, 2 vCPU, 4 GB RAM, 25 GB SSD y 2 GB swap. Cloud
Firewall, UFW, SSH por llaves, dos cuentas Linux informadas, MySQL por túnel SSH
y retirada del puerto público 3306. No se documentan IP, llaves ni credenciales.
Estado B solo en calidad de informado; Skill 5 debe verificar cada control.

Arquitectura objetivo, no topología desplegada acreditada:

```text
Internet → Cloud Firewall → UFW → Nginx :443
                                  ├─ Angular (archivos estáticos)
                                  └─ proxy API → Spring Boot :8080 → MySQL :3306
```

Nginx debe servir Angular y enrutar la API sin alterar sus contratos. Spring Boot
8080 y MySQL 3306 **no deben exponerse públicamente**: verificar listeners/bindings,
reglas de ambas capas firewall y acceso desde un origen externo autorizado, también
IPv6 si está habilitado. El repositorio no fija un puerto HTTP propio; 8080 es el
puerto objetivo a confirmar/configurar. La ubicación efectiva de MySQL debe
verificarse; si es remota, definir red privada/TLS y acceso mínimo.

HTTPS requiere dominio/DNS, certificado, renovación y políticas de proxy por
definir. Cualquier apertura de HTTP 80 para redirección o validación de certificado
necesita una decisión explícita; no se presupone abierta.
**Mantener Ubuntu 24.04 LTS. No actualizar a 26.04 sin planificación independiente.**
Los recursos informados no acreditan capacidad: comprobar disco libre, memoria/JVM,
MySQL, carga, logs, materiales y espacio de backups. Swap no sustituye ese ensayo.

## 5. Requisitos operativos de Skill 5

Todos los controles de servidor de esta sección son C hasta presentar evidencia.

| Área | Requisito y evidencia de cierre |
| --- | --- |
| Java/artefacto | Java 21 comprobado, JAR Spring Boot construido desde commit identificado mediante wrapper y suites verdes. Registrar versión, checksum y dependencias; conservar artefacto anterior compatible. Maven no es requisito de runtime si se publica el JAR construido. |
| MySQL | Verificar versión MySQL 8 exacta, esquema, tablas, índices FULLTEXT, FK, CHECK y privilegios mínimos. Compatibilidad probada localmente con 8.0.36; no certifica otra revisión. `ddl-auto=none`, sin Flyway/Liquibase ni creación automática del esquema. |
| Variables y JWT | Proveer únicamente en el proceso autorizado las variables de la tabla siguiente; acceso restringido, sin versionarlas ni imprimirlas. Definir expiración y procedimiento de rotación con impacto sobre tokens vigentes. |
| systemd | Preparar unidad operativa, usuario de servicio sin privilegios administrativos, directorio de trabajo, variables protegidas, permisos de storage, reinicio y arranque tras reboot. Verificar parada ordenada, logs y acceso a MySQL. No existe unidad de despliegue acreditada. |
| Nginx/Angular | Preparar proxy API, assets y fallback de rutas del cliente; conservar Authorization y códigos HTTP. Validar URLs del frontend, mismo origen/CORS según topología y descarga/subida. No existe configuración Nginx acreditada. |
| HTTPS/red | DNS, certificado válido/renovación, HTTPS y cierre público de 8080/3306 comprobados. Validar SSH por llaves y mínimo privilegio; túnel de administración sin publicar el puerto local. |
| Archivos | Directorio absoluto persistente fuera del JAR/checkout y de la publicación estática. ACL/propietario mínimo; comprobar root/ancestros y enlaces simbólicos. No permitir escritores no confiables. Alinear límite Nginx con multipart 10 MB por archivo / 11 MB por request y validar los límites efectivos, sin ampliarlos inadvertidamente. |
| Logs y monitoreo | Revisar DEBUG JWT y `show-sql=true` actuales mediante configuración operativa autorizada, sin afirmar que se cambiaron. Proteger logs, rotación y alertas de errores, compensación, disco, memoria, MySQL y servicio/certificados. Definir responsables y umbrales. |
| Backups | Respaldo coordinado MySQL/archivos, copia protegida fuera del único disco, retención y RPO/RTO definidos. Ensayar restauración aislada con consistencia de referencias y documentar resultados. No hay backup acreditado. |
| Rollback | Runbook para artefactos, frontend, configuración, esquema y archivos; decidir compatibilidad y mantenimiento antes de retirar una versión. No asumir que rollback de JAR revierte datos o DDL. |
| Comprobación de servicio | `/api/health` público devuelve estado estático; no comprueba MySQL ni storage. Añadir comprobaciones operativas autorizadas para dependencia, autenticación, flujo sintético y recuperación; no presentar health como readiness integral. |

Variables comprobadas en [application.properties — referencia no disponible](../../docs/auditoria/REFERENCIAS_HISTORICAS.md#archivos-no-disponibles):

| Variable | Contrato actual / requisito de operación |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Sin defaults; conexión del entorno de despliegue autorizado. CI/Testcontainers no las utiliza. Nunca reutilizar DEV para un ensayo. |
| `JWT_SECRET` | Sin default; mínimo 32 bytes UTF-8 y no vacío, conforme a validación del backend. Generación/custodia segura por el equipo. |
| `JWT_EXPIRATION` | Milisegundos positivos; default `3600000`. Validar decisión operativa. |
| `DIMABUG_STORAGE_MATERIALES_DIR` | Default relativo `./uploads/materiales`; sustituir operativamente por ubicación absoluta persistente y privada. |

No se identificó carga automática de `.env`. Los valores reales se mantienen fuera
de documentos, GitHub Actions y artefactos de prueba. Puerto/binding/logging del
servidor son configuración pendiente, no nuevas variables propias de DimaBug.

## 6. Migraciones transferidas

**Scripts preparados y ensayados únicamente en Testcontainers; aplicación a DEV o
producción no realizada ni acreditada.** No se aplican automáticamente por su ruta.

| Cambio pendiente en el entorno objetivo | Artefacto / precondiciones y validación |
| --- | --- |
| Cinco FK de usuarios | [V20261008_01 — referencia no disponible](../../docs/auditoria/REFERENCIAS_HISTORICAS.md#archivos-no-disponibles): `co_conocimiento.modificado_por`, `pr_procedimiento.creado_por/modificado_por`, `se_ejecucion.usuario_id`, `se_resultado.usuario_id` → `u_usuario.usuario_id`; RESTRICT, nulabilidad e índices conservados. Exigir cero huérfanos mediante [diagnóstico — referencia no disponible](../../docs/auditoria/REFERENCIAS_HISTORICAS.md#archivos-no-disponibles), tipos/índices compatibles, nombres libres y FK no aplicadas previamente. |
| Snapshot RN-PROC-01 | [V20261009_01 — referencia no disponible](../../docs/auditoria/REFERENCIAS_HISTORICAS.md#archivos-no-disponibles): flag en ejecución y tres campos capturados anulables en checklist, CHECK de copia completa. Filas anteriores: flag 0 y tres NULL; sin backfill ni historia inventada. Verificar preservación de IDs/resultados y compatibilidad del cliente. |

Ver planes [FK](migracion-fk-usuarios.md) y
[snapshot](decision-historial-procedimientos.md#migración-y-plan-operativo).
Antes de ambos: inventario del esquema real autorizado, backup restaurable, ensayo
aislado, ventana sin escritores y registro de cada paso. Orden de ensayo existente:
esquema saneado → diagnóstico → cinco FK → snapshot. No usar las fixtures sintéticas
del soporte como datos iniciales de producción. Preparar roles/primer usuario por
procedimiento autorizado aún pendiente.

El script anterior [V20260925_01](../src/main/resources/db/migration/V20260925_01__agregar_estado_eliminado_conocimiento.sql)
añade ELIMINADO al ENUM. Verificar ese requisito en el esquema destino; no afirmar
que falta ni ejecutarlo ciegamente. El dump saneado de pruebas incluye estructuras
que no se reconstruyen solo con JPA, incluida la proyección FULLTEXT; es una referencia
de ensayo, no una certificación del servidor ni un mecanismo completo de provisión.

MySQL DDL tiene commit implícito; los scripts no son idempotentes. Ante aplicación
parcial, mantener mantenimiento, inspeccionar y resolver solo el paso pendiente
autorizado. No borrar datos para satisfacer una FK ni repetir scripts sin diagnóstico.
Rollback preferente: reparación hacia adelante, conservación de snapshots e historia.
Un backend anterior a RN-PROC-01 podría mostrar pasos actuales o crear legacy nuevo:
no es un rollback funcional seguro sin un plan. Restaurar backup requiere decisión
y tratamiento de escrituras posteriores; no hay reversión automática ni DROP autorizado.

## 7. Dependencias Angular

Contrato confirmado en [EjecucionResponse](../src/main/java/cl/casol/backend/ejecucion/infrastructure/web/dto/EjecucionResponse.java)
y [EjecucionPasoResponse](../src/main/java/cl/casol/backend/ejecucion/infrastructure/web/dto/EjecucionPasoResponse.java):

- Ejecución: `heredada: boolean` en inicio/lista/detalle/completar/cancelar.
- Paso de ejecución: `heredado: boolean`, `orden: number | null`,
  `instruccion: string | null`, `esCritico: boolean | null` en lista/PATCH.
- Conservar `ejecucionPasoId`/`pasoId` y resultados; no reconstruir el checklist con
  pasos vigentes. Mostrar legacy como definición original no disponible; no sustituir
  NULL por orden 0, instrucciones actuales o criticidad false. No requiere nuevos
  campos de snapshot en requests de resultados.

**Pendiente:** adaptar modelos/UI, compilar Angular y validar ejecución nueva,
consulta heredada y actualización de resultados contra el backend candidato.
No se afirma compatibilidad automática con tipos estrictos anteriores ni prueba
end-to-end de navegador. Coordinar frontend/backend/esquema y evitar escritores
antiguos durante la transición.

## 8. Riesgos y decisiones pendientes

| Riesgo | Estado / decisión requerida |
| --- | --- |
| Crash, compensación fallida, fallo doble, commit incierto, huérfanos y referencias sin bytes | E. [Propuesta preparada](aceptacion-riesgo-archivos-skill4.md), sin aprobación. Definir responsable, alcance, vigencia y controles; RA-04 requiere decisión explícita. No hay recuperación durable automática acreditada. |
| Root/ancestros manipulables, symlinks o publicación accidental del storage | C, riesgo condicionado P1/P0 según impacto; verificar permisos/topología Linux. No incluir exposición de contenido en una aceptación genérica de recuperación. |
| Esquema/cliente desalineados con snapshot o FK | C. Ensayar versión MySQL y migraciones, compatibilidad Angular y coordinación de escritores; mantener historia. |
| CI solo local y artefacto sin trazabilidad | B/C. Primer run real y relación commit/JAR/reportes pendientes; no atribuir 45/474 PASS a GitHub Actions. |
| Brechas funcionales/carga/configuración recogidas por auditoría | C. Mantener registro, decidir pruebas/correcciones o postergación aprobada; no usar este handoff para darlas por resueltas. |

La propuesta de archivos contempla una vigencia sugerida de 30 días desde aprobación,
no una fecha acordada. Sus backups, runbook, inventario y atención operativa deben
tener evidencias antes de activar esa aceptación para despliegue. No existe categoría
D acreditada aquí; responsables, umbrales y aceptación permanecen pendientes.

## 9. CI transferido

[backend-tests.yml — referencia no disponible](../../docs/auditoria/REFERENCIAS_HISTORICAS.md#archivos-no-disponibles): PR y push a `main`,
Java 21, wrapper, caché Maven y dos jobs independientes. Comandos:

```text
./mvnw -B test
./mvnw -B -Pintegration-mysql verify
```

Surefire/Failsafe se publican con `always()` si se generaron; fallos conservan el
resultado CI fallido. Permiso `contents: read`, acciones fijadas por SHA, credenciales
checkout no persistentes, sin secretos DEV/producción ni despliegue. Docker local
del runner y MySQL temporal; no BD externa. Configuración B y validación YAML local,
**sin ejecución remota acreditada**. Revisar políticas de Actions y checks requeridos.
Ver [CI](ci-pruebas-backend.md) para requisitos, limitaciones y evidencia pendiente.

## 10. Checklist de entrada a Skill 5

La entrada habilita planificación/ensayos autorizados; no publicación. Cada casilla
requiere responsable, fecha y evidencia, no se presume completada por este documento.

- [ ] Designar receptores QA/arquitectura/DevOps, aprobador del riesgo y autorizador de publicación; registrar recepción del handoff.
- [ ] Identificar commit/artefactos candidatos y reunir reportes/logs de ambas suites; comprobar que incluyen correcciones y RN-PROC-01.
- [ ] Registrar pendientes CI, Angular, migraciones y aceptación de archivos con responsables y orden de ejecución.
- [ ] Verificar inventario real del Droplet, controles informados y Ubuntu 24.04; no planificar actualización a 26.04 dentro de este despliegue.
- [ ] Acordar entorno aislado de ensayo, accesos mínimos y ventana; DEV no es banco de pruebas de despliegue.
- [ ] Preparar plan de configuración, backups/restauración, migración, monitoreo y rollback antes de acciones en un entorno real.
- [ ] Validar con el equipo qué brechas de Skill 4 deben resolverse y cuáles pueden postergarse con aceptación explícita.

## 11. Criterios propuestos para futura decisión GO / NO-GO

**No se evalúa ni concede GO en esta transferencia.** La decisión futura corresponde
al responsable designado después de revisar evidencias del candidato y del entorno.

| Condición obligatoria propuesta | Evidencia para considerar GO futuro | NO-GO si |
| --- | --- | --- |
| Candidato probado y CI | Ambos jobs reales exitosos, reportes/totales sin fallos y artefacto identificado; brechas críticas resueltas o decididas explícitamente. | Falla una suite, no hay evidencia del candidato o CI sigue sin validación remota. |
| Integridad y migración | Ensayo/restauración aislados, cero huérfanos previos, esquema y restricciones verificados tras aplicación autorizada, legacy preservado. | Resultado DDL parcial/desconocido, FK/CHECK/índices faltan o no existe recuperación validada. |
| Cliente/historia | Angular compilado y compatible con heredada/heredado/NULL; flujos nuevos y legacy comprobados en el candidato. | UI fabrica historia, falla por nulabilidad o requiere escritores antiguos incompatibles. |
| Seguridad/red | HTTPS y renovación, mínimos privilegios, storage privado, autenticación/permisos y cierre externo de 8080/3306 acreditados. | Exposición de backend/MySQL/archivos, secreto comprometido o bypass confirmado. |
| Operación y archivos | Backups/restore, capacidad, monitoreo, runbooks y responsables comprobados; aceptación formal vigente para escenarios permitidos. | Riesgo sin aceptación, copia no recuperable, control mínimo incumplido o pérdida crítica no contemplada. |
| Rollback y autorización | Plan compatible ensayado, preservación de datos y autorización trazable de publicación. | No hay alternativa segura o no se identifica quién asume la decisión. |

Un P0/P1 confirmado sin resolver obliga a revisar NO-GO; una brecha o riesgo
condicionado exige valoración y decisión documentada, no se declara automáticamente
defecto. La recepción de este documento no sustituye ninguna de estas evidencias.

## 12. Registro de transferencia

| Campo | Estado |
| --- | --- |
| Entrega / recepción y responsables | Documento preparado; firmas o aprobación de recepción pendientes. |
| Versión/commit y artefactos publicables | Pendiente de fijar por Skill 5. |
| Aceptaciones de riesgo | Pendientes; no registradas como aprobadas. |
| Fechas de ensayo, migración y publicación | Pendientes, sin autorización implícita. |
| Decisión de salida de Skill 4 | Se conserva CIERRE CON CONDICIONES. |
| Decisión de producción | No emitida. |

Esta tarea únicamente prepara documentación. No modifica código, pruebas,
configuración ni infraestructura; no ejecuta SQL, migraciones, despliegues ni
conexiones a DEV/producción.
