# DimaBug: configuración y envío de comunicaciones dentro del paso

## Inicio y ejecución
«Iniciar procedimiento» crea la ejecución con usuario, fecha y contrato de sus mismos pasos. No solicita sucursal ni envía correo. Tras el HTTP 201 abre /ejecuciones/{id}; conserva la corrección del guard y el reintento de navegación sin otro POST.

En un paso de SOLICITAR_EVIDENCIA o ENVIAR_CORREO aparece «Enviar correo» cuando corresponde activarlo. El paso queda pendiente de solicitud hasta que el usuario confirme el modal. Los pasos anteriores deben estar completos; el backend valida esta condición además de la interfaz. Los pasos manuales conservan instrucciones, materiales, observaciones opcionales y un único «Siguiente».

## Descripción de las interfaces
**Editor de pasos:** modal amplio, secciones A–E, azul marino y azul corporativo, fondo blanco, bordes redondeados y separación visual. A contiene orden/instrucción/criticidad. B define modalidad y aprobación. C contiene contactos de sucursal y correos adicionales Para/CC. D incluye asunto, cuerpo y opciones individuales para las evidencias. E agrupa condición de finalización, plazo y recordatorios. Los campos de correo se ocultan en pasos manuales o informativos. Las casillas de evidencias configuran requisitos; no son casillas para completar pasos durante la ejecución.

**Ejecución:** encabezado de progreso, navegación lateral con estados, instrucciones y acción contextual. Un panel identifica «Pendiente de solicitud». El modal de correo contiene cuatro secciones: sucursal, destinatarios, mensaje y acciones. Permite buscar por código/nombre/ciudad y muestra nombre, cargo y correo del responsable registrado. Los datos no registrados se indican como tales.

**Confirmación e historial:** solo un identificador de envío confirmado y una fecha del servidor permiten mostrar éxito. Con el adaptador actual se muestra «Correo simulado correctamente. No se envió un mensaje real». Fecha/hora se convierten explícitamente a America/Santiago. La línea de tiempo muestra eventos, responsable, destinatarios, estado y evidencias; incluye rondas rechazadas y permite consultar sus fotografías mediante el endpoint autenticado.

## Permisos y persistencia
Los técnicos solo pueden enviar a contactos autorizados de la sucursal seleccionada o del paso. Los administradores pueden incorporar otros correos @dimarsa.cl y editar el mensaje; el servidor verifica los permisos. Los correos y cambios de tipo se normalizan, sin duplicados y con un destinatario Para obligatorio.

El asunto se guarda en el JSON de configuración existente del paso y se captura en el JSON de la solicitud. Cuerpo, destinatarios, fecha del servidor, identificador del proveedor, estado y seguimiento se guardan en las tablas existentes. Los eventos se asocian a solicitud → instancia de paso → ejecución. El historial enriquecido se construye desde esos registros persistidos; no depende del estado local de Angular.

El envío bloquea la cabecera de ejecución y utiliza una identidad determinista por instancia de paso. Un reintento con la misma selección y contenido devuelve la misma solicitud; si el contexto cambia, el servidor exige revisar el historial. El error del proveedor revierte la solicitud y sus eventos: no se muestra un envío inexistente. El modal conserva los datos si falla la operación.

Recordatorios, recepción simulada de evidencias, aprobación/rechazo, nuevas rondas y avance automático conservan sus servicios originales. Enviar una solicitud no acredita la carga de folios ni completa un paso crítico que requiere revisión.

## Base de datos
05_directorio_ciudad_cargo_desarrollo.sql agrega únicamente ciudad y responsable_cargo, ambos opcionales, a u_sucursal. Fue aplicado por la usuaria en staging local. No actualiza procedimientos, ejecuciones ni responsables existentes. Los campos pueden mantenerse en Administración → Sucursales. La lectura real de Hibernate verificó el esquema actualizado.

## Archivos principales modificados
- Editor frontend: configuracion-paso.component.ts/.css, destinatarios-editor.component.ts y procedimiento-detalle.component.ts/.html/.css.
- Ejecución frontend: validacion-externa.component.ts/.html/.css y ejecucion-checklist.component.ts/.html.
- Clientes/modelos: automatizacion-ejecucion.service.ts, configuracion-paso.model.ts, admin-sucursales-api.service.ts y admin-sucursales.component.ts/.html.
- Backend: GestionarEjecucionService, AutomatizarEvidenciaService, AutomatizacionEjecucionController, ConfiguracionPaso, SolicitudEvidencia, MantenerPasoService y entidades/servicios/controladores del catálogo de sucursales.
- Pruebas: AutomatizacionFlujoJpaTest, AutomatizacionHttpPersistenciaTest, procedimiento-automatizacion.component.spec.ts, validacion-externa.component.spec.ts, configuracion-paso.component.spec.ts y correo-paso-modal.component.spec.ts.

## Límites y configuración
No hay integración Gmail real autorizada: el proveedor sigue en SIMULADO, con indicación visible y sin correos reales. Deben registrarse destinatarios reales en el catálogo o paso; no se inventan direcciones de Control Cajas. Los procedimientos existentes no cambian arbitrariamente; sus modalidades deben configurarse desde el editor si aún son manuales.

AUTOMATICO conserva únicamente el handler permitido de validación de sucursal. En el flujo diferido utiliza un contexto de sucursal registrado por una comunicación previa. Si no existe ese contexto, permanece pendiente y no afirma una validación que no pudo ejecutar.

El modal incorpora etiquetas, foco contenido y restaurado con Angular CDK, Escape, mensajes anunciados, controles de al menos 44 px y diseño adaptable. Las pruebas de DOM y foco no constituyen una certificación completa de WCAG 2.2 AA ni sustituyen una revisión con lector de pantalla. Se entrega esta descripción de interfaces; no se afirma una revisión visual autenticada con capturas.

## Validación ejecutada
- Suite Java completa: 1016 pruebas aprobadas, sin fallos ni errores.
- Integración final tras la última mejora de contexto: 36 pruebas aprobadas (incluye HTTP/JWT real de prueba y persistencia H2).
- Angular: 351 pruebas aprobadas, incluidas DOM del modal, captura de foco, selección de responsables, confirmación simulada, error de envío, doble clic y rondas del historial.
- Compilación frontend correcta. Conserva advertencias de presupuesto del bundle y CSS de inicio/conocimiento.
- MySQL local: lectura Hibernate correcta del esquema migrado y 90 instancias de pasos existentes; no se crearon ejecuciones de demostración ni se enviaron correos reales.
- Runtime local confirmado tras el reinicio de la usuaria: Angular en 4200, backend actualizado (PID 47688) en 9090, salud directa y por proxy 200, WebSocket OPEN. Hibernate lee correctamente los mapeos y 90 instancias de pasos existentes. Las rutas privadas responden 401 sin sesión; no se afirma una revisión visual autenticada. No se persistieron contraseñas ni tokens de su entorno.
