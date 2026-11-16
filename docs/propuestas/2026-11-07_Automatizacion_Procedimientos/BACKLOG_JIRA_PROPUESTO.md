# Backlog propuesto: Automatización de procedimientos

Fecha: 7 de noviembre de 2026. Estado: borrador para aprobación. No se crearon issues ni se accedió a Jira.

[Diagnóstico y propuesta técnica](DIAGNOSTICO_Y_PROPUESTA.md).

Épica: **Automatización de procedimientos**. Los IDs AUT-01…AUT-07 son referencias de este documento, no claves reales de Jira. Sprint y complejidad son propuestas, no compromisos de fecha o velocidad.

## Convenciones y dependencias

- P0: imprescindible para un flujo seguro que satisfaga el requerimiento; P1: mejora recomendable que no puede debilitar los gates.
- S0: decisiones, esquema físico y contratos; S1: base durable/salida; S2: entrada/decisión; S3: seguimiento/coordinación/UX; S4: endurecimiento y piloto. Las historias pueden requerir subtareas en más de un sprint.
- Base compartida en S1: ampliación de las cuatro entidades actuales, snapshot de contrato para nuevas ejecuciones, control de agregado, directorio de sucursales confirmado, repositorios de solicitud/evidencia/trabajo/evento/conexión. No crear un segundo checklist ni un broker nuevo por defecto.
- Dependencias de infraestructura se gestionan como subtareas de las historias que las requieren, sin duplicar tablas o trabajo en cada historia.
- DoD común: implementación revisada respetando capas/puertos; casos de aceptación y negativos ejecutados con evidencia real; regresión de contratos/roles/manuales; documentación actualizada; sin secretos o destinatarios reales no autorizados; feature flag segura y configuración separada QA/producción. SQL y correo reales solo bajo autorización de ambiente específica.
- Los casos P01–P30 se detallan en la propuesta. Los resultados históricos de 920/284 no son resultados de estas historias.

## AUT-01. Integrar Gmail empresarial con buzón autorizado

**Historia:** como administrador autorizado quiero conectar un buzón corporativo dedicado para que DimaBug envíe y lea las comunicaciones del proceso sin usar contraseñas de Gmail.

**Objetivo:** proporcionar puertos de envío/lectura y conexión OAuth segura. **Valor:** continuidad y trazabilidad sin depender del buzón personal de cada técnico. **Prioridad/clasificación:** P0, Imprescindible. **Sprint candidato:** S0 permisos, S1 implementación QA. **Complejidad:** alta; política Workspace y secretos pueden bloquear la entrega.

### Criterios de aceptación

1. La conexión solo se activa por administración, tras verificar el buzón retornado y su allowlist; no permite elegir arbitrariamente cualquier usuario corporativo.
2. Solicita `gmail.send` y `gmail.readonly` solo para el modo completo aprobado; no mail.google.com/modify/Directory por conveniencia. Los datos de credencial no llegan a Angular ni a logs.
3. Retorno OAuth con state, expiración, uso único y protección del flujo, separado del JWT propio. Un retorno ajeno o repetido no conecta un buzón.
4. Refresh/revocación se gestionan con biblioteca validada y secreto externo; 401/403 de política se muestran como reconexión/bloqueo, no generan spam.
5. Existe un registro de conexión/cursor, auditoría y aislamiento QA/prod; desactivar impide nuevos despachos y conserva historia.
6. Solo se prueba envío real a cuentas QA allowlisted si hay autorización aparte. La historia puede ensayarse inicialmente con adaptador simulado.

### Subtareas

- Confirmar tenant, buzón y aprobación Workspace; revisar alcance interno/verificación y descartar DWD por defecto.
- Definir CorreoAutomatizacionPort, DTO internos y adaptador OAuth/Gmail en infraestructura.
- Revisar dependencia mantenida compatible con Boot 4/Java 21; no instalar hasta aprobar implementación.
- Implementar vínculo/estado de conexión y referencia a secretos; flujo administrativo/callback acotados.
- Preparar stubs para refresh, revocación, 403 policy, cuotas y errores transitorios.

**Dependencias:** administrador Workspace, mecanismo de secretos/HTTPS, S0 aprobado y datos QA. **DoD específica:** P18/P19 y pruebas OAuth adversarias ejecutadas; scopes y almacén revisados; ausencia de acceso a buzones ajenos comprobada. DWD, si se elige, requiere una decisión y pruebas adicionales explícitas.

## AUT-02. Solicitar evidencias automáticamente al preparar un POS

**Historia:** como técnico quiero seleccionar sucursal/POS y comenzar una preparación para que DimaBug solicite al contacto autorizado las fotos necesarias sin redactar o repetir correos.

**Objetivo:** crear ejecución, solicitud/ronda y trabajo durable con intención explícita. **Valor:** menos coordinación manual y destinatarios trazables. **Prioridad/clasificación:** P0, Imprescindible. **Sprint:** S1. **Complejidad:** alta por contexto, idempotencia y persistencia.

### Criterios de aceptación

1. Se valida existencia/actividad de sucursal, POS/contexto y contacto vigente; si falta el jefe/correo, queda acción pendiente sin inventar destinatario ni enviar.
2. Un doble inicio con la misma clave/payload retorna la misma ejecución; reutilizar clave con otro contexto produce conflicto.
3. Crear ejecución, paso, solicitud/ronda y outbox es consistente; un fallo de BD no deja una solicitud aprobable parcial.
4. El correo identifica solicitud/ronda/contexto y pide categorías mueble/red/electricidad, con enlace de identidad corporativa de alcance limitado.
5. Abrir ficha, precargar o cambiar selección no envía correos. La acción inicial es única y explícita.
6. Se registra intento y aceptación proveedor, IDs y resultado incierto; no se completa una actividad física por emitir la solicitud.

### Subtareas

- Confirmar fuente maestra de sucursal/contacto; implementar puerto y catálogo mínimo solo si falta equivalente.
- Añadir contexto/capturas y política opt-in a entidades actuales mediante diseño aditivo revisado.
- Implementar caso de inicio idempotente, solicitud por instancia/ronda y dispatcher/outbox común.
- Preparar plantilla MIME, claves de correlación, request de fotos y auditoría segura.
- Adaptar el inicio Angular sin duplicar pantallas y sin deshabilitar controles de cancelación.

**Dependencias:** AUT-01 para integración real; base compartida, definición POS y directorio confirmados. Puede desarrollarse con fake antes de conectar Gmail. **DoD:** P01/P02/P19/P25 y regresión de inicio manual ejecutados; contactos/código no hardcodeados; no duplicar se_ejecucion_paso o co_material_apoyo.

## AUT-03. Recibir y asociar respuestas y fotos de forma segura

**Historia:** como técnico quiero que las respuestas legítimas se vinculen al paso correcto para disponer de evidencias sin buscar correos o descargar archivos manualmente.

**Objetivo:** sincronización incremental durable, correlación, identidad y cuarentena. **Valor:** expediente de evidencia ordenado y menos riesgo de atribuir fotos a otro POS. **Prioridad/clasificación:** P0, Imprescindible. **Sprint:** S2. **Complejidad:** muy alta por MIME, identidad, archivos y recuperación.

### Criterios de aceptación

1. Leer solo el buzón autorizado y procesar mensajes del alcance necesario; no acceder al buzón del jefe. Un filtro no se presenta como scope limitado al hilo.
2. Vincular por solicitud/ronda, IDs Gmail/RFC, nonce y contexto; From o asunto solos no bastan. Respuestas ambiguas/antiguas no avanzan un paso.
3. MIME anidado, attachments e inline se procesan con límites; logos/firma no se contabilizan automáticamente como fotos de evidencia.
4. Conexión/messageId y partId únicos impiden asociaciones y eventos duplicados en relecturas, reinicios o dos workers.
5. Fotos se guardan privadas en cuarentena; tipo/firma/decodificación/tamaño y escaneo fallan cerrados. No son aprobables antes de cumplir la política de identidad corporativa.
6. Confirmación OIDC valida cuenta corporativa permitida y subject ligado al contacto/solicitud; un enlace reenviado o token adulterado no da acceso.
7. Cursor avanza solo después de persistir trabajos; historia vencida se resincroniza acotadamente sin perder/duplicar entradas.
8. Notificación al técnico y actualización visual no requieren botón Actualizar; latencia de polling se muestra sin prometer tiempo real.

### Subtareas

- Implementar polling/cursor, paginación y trabajos de entrada idempotentes.
- Parser MIME con límites y correlación defensiva; ignorar autosrespuestas/DSN/Reply-To no autorizado.
- Implementar sesión externa limitada OIDC y vínculo de identidad; mantener permisos DimaBug independientes.
- Reutilizar validadores de formato, crear namespace/puerto de evidencias y pipeline de cuarentena/escaneo.
- Implementar asociación por categoría y expediente de ronda; registrar actor/procedencia/hashes.
- Añadir endpoint autorizado de consulta y mecanismo de aviso derivado de persistencia, no solo memoria local.

**Dependencias:** AUT-01/AUT-02, almacenamiento privado, escáner, política de identidad/retención. **DoD:** P03–P08/P20/P21/P24/P25 ejecutados con mensajes sintéticos, adversarios y recuperación; no evidencia expuesta por URL pública; Pub/Sub no obligatorio para cerrar esta historia MVP.

## AUT-04. Aprobar o rechazar evidencias y actualizar progreso

**Historia:** como técnico autorizado quiero decidir sobre las fotos verificadas para que el sistema complete el paso o solicite corrección sin tener que marcar nuevamente su estado.

**Objetivo:** decisión humana atómica, auditable y vinculada al conjunto revisado. **Valor:** elimina actualización duplicada sin perder gate físico/crítico. **Prioridad/clasificación:** P0, Imprescindible. **Sprint:** S2. **Complejidad:** alta por concurrencia/autorización.

### Criterios de aceptación

1. Solo el aprobador permitido de esa ejecución puede decidir; ni jefe externo, remitente, worker Gmail ni cliente con ID ajeno aprueban.
2. Aprobar exige identidad/archivos aptos, categorías suficientes, ronda y versión actuales, IDs/hashes del conjunto y commandId.
3. La aprobación persiste decisión, COMPLETADO, `cumplido=true`, fecha, evento y siguiente trabajo en una transacción; no requiere otro Guardar/Hecho/Siguiente.
4. Rechazar exige motivo; conserva fotos y decisión, crea exactamente una ronda nueva y encola solicitud de corrección autorizada.
5. Dos decisiones o una foto nueva competidoras no generan doble avance/ronda. Un comando obsoleto devuelve conflicto.
6. PATCH legado no elude el gate de una ejecución opt-in. Reapertura requiere política/motivo y revisión de dependientes.

### Subtareas

- Implementar reglas de decisión y autorización por actor/propiedad, no por botón visible.
- Capturar conjunto/versiones y control de agregado con orden de locks documentado.
- Proyectar boolean legado y estado rico consistentemente, con verificación de bypass en servicio actual.
- UI accesible de revisión por categoría, aprobación única y rechazo con motivo; consultas por resultado si red incierta.
- Auditoría y transacción con trabajos posteriores, sin I/O Gmail dentro de la transacción.

**Dependencias:** AUT-03 y modelo/compatibilidad base. **DoD:** P12–P17/P23/P24/P26 ejecutados, rechazo reintentado no duplica rondas y se demuestra que no se puede completar por correo/tiempo ni PATCH directo.

## AUT-05. Gestionar recordatorios y atraso sin duplicados

**Historia:** como responsable de soporte quiero seguimiento configurable para que una solicitud pendiente reciba recordatorios oportunos y el técnico conozca el atraso, sin perseguir manualmente al jefe.

**Objetivo:** política D0/D1/D3/D5 configurable, ventanas y pausa durables. **Valor:** continuidad sin spam ni cron dependiente de una pestaña. **Prioridad/clasificación:** P0, Imprescindible. **Sprint:** S3. **Complejidad:** alta por reloj, concurrencia y correo incierto.

### Criterios de aceptación

1. Se configuran offsets, días hábiles/corridos, zona IANA, horarios/feriados, canal de escalamiento y límites de reintento; no hay fechas hardcodeadas en dominio.
2. Cada regla/ronda produce un trabajo único; cada intento registra fecha y resultado. Tras reinicio no se envía una ráfaga de vencidos.
3. Completada/cancelada/cerrada o pausa impiden nuevos despachos. Evidencia apta detiene nuevas peticiones de las mismas fotos mientras se valida.
4. Pausa/reanudación tiene permisos y motivo, persiste y no impide recibir respuestas. Rechazo abre un reloj/ronda nueva, no reutiliza decisiones anteriores.
5. Timeout ambiguo pasa a conciliación, no a reenvío ciego; errores transitorios usan backoff limitado y política/credencial inválida requieren acción.
6. Los intentos ya en vuelo y su posible entrega tardía se muestran/auditan; no se promete revocar un envío aceptado externamente.

### Subtareas

- Diseñar política versionada por solicitud y reloj inyectable.
- Configurar dispatcher Spring y reclamación/lease/fencing en BD sin broker adicional.
- Implementar coalescencia, cancelación de pendientes, pausa y escalamiento técnico.
- Registrar métricas por resultado de negocio, sin confundir intentos y entregas.
- Preparar runbook de caídas, reanudación y casos inciertos.

**Dependencias:** AUT-01/AUT-02, semántica de AUT-03/AUT-04, horarios de negocio y outbox. **DoD:** P09–P11/P18/P19/P21/P22 ejecutados; dos instancias no duplican autorización de envío y el requisito de efectos en vuelo está aceptado/documentado antes del piloto.

## AUT-06. Continuar automáticamente hasta la siguiente intervención necesaria

**Historia:** como técnico quiero que DimaBug encadene las acciones verificables y se detenga solo cuando debo actuar, para evitar marcas y confirmaciones redundantes.

**Objetivo:** coordinador secuencial por condiciones, modo y criticidad. **Valor:** reduce administración manual manteniendo control de actividades físicas. **Prioridad/clasificación:** P0, Imprescindible. **Sprint:** base en S1, integración S3. **Complejidad:** muy alta; afecta compatibilidad y garantías del agregado.

### Criterios de aceptación

1. MANUAL, AUTOMATICO, VALIDACION_EXTERNA e INFORMATIVO se combinan con `esCritico` existente; crítico no es un modo excluyente.
2. El backend habilita solo el siguiente paso elegible. Ver otro paso no permite ejecutarlo. Un handler no ejecuta shell/URL arbitrario desde configuración o email.
3. Un evento confirmado encola continuación idempotente; el coordinador se detiene en acción humana/crítica, evidencia no apta o error.
4. Un paso físico no se completa por reloj, email o envío aceptado. Boot/comandos/instalaciones siguen manuales en MVP sin agente confiable.
5. Cierre automático requiere todos los requisitos efectivos y gates completos y política capturada; cancelación conserva control.
6. Ejecuciones nuevas capturan contrato; legadas mantienen modelo manual y campos históricos desconocidos no se inventan. El inicio antiguo no degrada una definición opt-in.
7. Commit/evento/outbox y control de agregado evitan cierre con paso pendiente; workers fuera de transacción no aplican resultados obsoletos.

### Subtareas

- Definir transiciones, condiciones y registro permitido de handlers; verificar reglas con negocio.
- Diseñar ampliación aditiva de entidades existentes y proyección de `cumplido`; revisar DDL después de esquema físico.
- Implementar coordinador y comandos internos SISTEMA acotados, captura de contrato y lock/versionado.
- Revisar servicios/endpoints actuales para gate compartido y compatibilidad; no crear motor paralelo.
- Probar errores, reintentos, pausa, cancelación, reapertura y finales tardíos.
- Preparar flag, nueva definición BORRADOR del piloto y plan de retorno compatible.

**Dependencias:** decisiones S0, base común, AUT-02/AUT-04 para flujo real; AUT-01/AUT-05 para efectos externos. **DoD:** P14–P17/P21/P23/P28/P29 y regresión real ejecutadas; se demuestra ausencia de avance indebido y se conserva inmutabilidad de PUBLICADOS.

## AUT-07. Simplificar la interfaz y medir intervenciones

**Historia:** como técnico quiero una pantalla que muestre la acción pendiente y guarde mis notas para dedicarme al trabajo y no a administrar estados.

**Objetivo:** reutilizar checklist, evitar avance optimista y duplicación de acciones, medir mejora real. **Valor:** experiencia más clara con errores recuperables y accesibilidad. **Prioridad/clasificación:** P0 para gates/confirmación servidor y visibilidad; P1/Recomendable para autosave y mejoras de medición. **Sprint:** apoyo en S1/S2, cierre S3/S4. **Complejidad:** media-alta.

### Criterios de aceptación

1. No mostrar un paso cumplido ni avanzar como definitivo antes del commit confirmado. Los errores conservan observación y acción pendiente.
2. Inicio combina contexto e intención; Aprobar y continuar y Rechazar y pedir corrección son acciones únicas, sin botones redundantes para persistir lo mismo.
3. Autosave envía solo observación con control de versión; Guardando/Guardado/Error es visible y no cambia cumplimiento con un boolean viejo.
4. Se actualiza progreso externo mediante lectura forzada/polling acotado, sin requests solapados o loop después de navegar/cerrar. Hay versión y última actualización visibles.
5. Se conserva lectura de instrucciones, historial, pausa/reintento autorizado y confirmación de cancelación. No ocultar controles críticos por simplificar.
6. Teclado, foco, lector de pantalla/aria-live, contraste y revisión de fotos/categorías se validan en UAT.
7. Métricas distinguen tiempo de respuesta/validación, clics por rol, tareas físicas semiautomáticas e informativas; ninguna reducción se declara medida sin línea base comparable.

### Subtareas

- Diseñar recorrido con técnico y jefe, sin rediseño global o pantallas duplicadas.
- Adaptar checklist/servicios actuales, invalidación de caché y reconciliación de respuestas inciertas.
- Implementar autosave parcial y estados de error accesibles; mantener datos no guardados.
- Añadir specs del checklist actualmente no localizadas, pruebas HTTP simuladas y carreras de UI.
- Instrumentar eventos mínimos y realizar comparación base/piloto sin capturar datos sensibles.

**Dependencias:** contratos AUT-02/AUT-03/AUT-04/AUT-06; estados AUT-05 para seguimiento. **DoD:** P26/P27/P28, accesibilidad/UAT y regresión de formularios ejecutadas; indicadores muestran muestra/período y separan observado/estimado.

## Gates de salida de la épica

- S0 aprobado y permisos/maestros reales confirmados por responsables, sin copiar credenciales.
- P01–P29 ejecutados según alcance de release, con resultados y ambiente trazables; P30 solo si se incorpora push.
- Ensayos MySQL aislado de transacciones, unicidad, multiworker, aprobación/cancelación y recuperación; no sustituir por mocks/H2.
- Seguridad de OAuth/OIDC, archivos, propietario y bypass legado revisada y pruebas negativas aprobadas.
- Piloto con datos/cuentas QA autorizados, feature flag y plan de recuperación; ningún correo de sucursal real en pruebas iniciales.
- Documentación/métricas y pendientes actualizados; no afirmar que la épica termina con acceso automático al POS, validación eléctrica remota o carga de folios no demostrada.
- Autorización separada para migraciones, envío real o despliegue. La aprobación de estas historias para programar no implica permisos de operación de producción.

**Estado final:** backlog propuesto listo para revisión, sin issues creados y sin implementación iniciada.
