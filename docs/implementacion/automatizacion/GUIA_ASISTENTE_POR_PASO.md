> Actualización del flujo de inicio y envío: ver [GUIA_MODAL_CORREOS.md](GUIA_MODAL_CORREOS.md). La selección de sucursal se realiza ahora en el paso, no antes de crear la ejecución.

# Asistente de procedimientos y correos reutilizables

## Configurar sin duplicar pasos
En el detalle del procedimiento, cada paso dispone de su propia configuración. En procedimientos publicados, solo un administrador puede usar «Configurar este paso». Se conservan identificador, orden, instrucciones y criticidad; se audita la configuración anterior y posterior. Las nuevas ejecuciones capturan esta configuración; las existentes conservan su contrato.

El botón global para crear un borrador con evidencias ya no es necesario en la interfaz.

## Agregar y guardar correos
En la configuración del paso, seleccionar SOLICITAR_EVIDENCIA o ENVIAR_CORREO. Escribir el correo, elegir Principal o En copia y pulsar «Agregar nuevo correo». Guardar el paso para persistir la lista en MySQL. En Administración → Sucursales, el mismo editor permite guardar otros responsables con «Guardar sucursal». Los destinatarios de sucursal se reutilizan al iniciar ejecuciones cuando esa opción está activada.

Solo se aceptan direcciones @dimarsa.cl, sin duplicados y con un máximo de veinte destinatarios. Los técnicos pueden seleccionar destinatarios autorizados del catálogo o del paso; los administradores mantienen estas listas. Agregar un correo no envía mensajes por sí mismo. Durante una ejecución, se conserva una copia de los destinatarios usados para mantener la trazabilidad.

## Preparar POS: seis pasos
1. Verificar instalaciones: SOLICITAR_EVIDENCIA, destinatarios de sucursal, fotografías de mueble/red/electricidad y aprobación humana. Al aprobar, completa el paso y avanza.
2. Bootear Debian 12: MANUAL_GUIADO; «Siguiente» registra y avanza.
3. Comandos en terminal: MANUAL_GUIADO; mismo comportamiento.
4. Instalar Comercial: MANUAL_GUIADO; mismo comportamiento.
5. Instalar Transbank: MANUAL_GUIADO; mismo comportamiento. Si es crítico, la acción conserva confirmación explícita.
6. Solicitar folios: ENVIAR_CORREO, destinatarios de Control Cajas registrados por un administrador. ENVIO_REGISTRADO completa la solicitud al registrar el envío. RESPUESTA_VALIDADA espera respuesta y su validación. Enviar no acredita que los folios se hayan cargado.

No se inventan direcciones de Control Cajas ni se cambian arbitrariamente los procedimientos almacenados. La base local contiene el procedimiento original id 1 y sus seis pasos; los borradores anteriores se conservan.

## Persistencia y seguridad
La migración 04 agrega configuración e historial al paso, contrato de configuración a la ejecución y listas de destinatarios a sucursal y solicitud. Reutiliza el registro de eventos para auditar pasos manuales sin crear solicitudes ficticias. No cambia datos históricos.

Las escrituras de ejecución utilizan bloqueo de cabecera, control de versión e idempotencia. Evidencias, decisiones, rechazos, rondas y recordatorios conservan trazabilidad. El modo AUTOMATICO admite exclusivamente la acción implementada VALIDAR_CONTEXTO_SUCURSAL; no ejecuta comandos arbitrarios.

## Operación local
Correo SIMULADO: no hay conexión Gmail real ni envío corporativo. No se presentan mensajes simulados como entregas reales. Para usar correos reales hace falta un adaptador e integración autorizada.

La verificación JDBC/Hibernate no inicia otro servidor. El reinicio local valida PID, perfil staging, destino 127.0.0.1:3307/dima_bug_staging y propiedad de 9090 antes de sustituir únicamente la instancia conocida.

## Archivos principales
- Frontend: configuracion-paso.component.ts, destinatarios-editor.component.ts, procedimiento-detalle.component.*, ejecucion-checklist.component.*, validacion-externa.component.*, admin-sucursales.component.* y servicios/modelos relacionados.
- Backend: ConfigurarPasoService/Controller, ConfiguracionPaso, DestinatarioPaso, GestionarEjecucionService, AutomatizarEvidenciaService, repositorios/mapeadores/DTO y catálogo de sucursales.
- Base: 04_modalidades_por_paso_desarrollo.sql.
- Prueba completa persistida: AutomatizacionFlujoJpaTest.prepararPosCompletoConExactamenteSeisPasosSinCasillas y foliosConConfirmacionNoSeCompletanSoloPorEnviarCorreo (base H2 aislada, correos ficticios, sin envío real).

## Validación realizada
- Backend: 1009 pruebas, cero fallos y errores. Incluye 27 de flujo persistido y los dos recorridos de folios.
- Frontend: 331 pruebas aprobadas; compilación de producción correcta. Quedan advertencias de presupuesto de bundle y CSS.
- MySQL local: migración 04 confirmada; Hibernate leyó 60 filas de ejecución de paso y todos los mapeos de automatización sin errores de columna.
- Runtime actualizado: puerto 9090, PID 15980, salud HTTP 200, staging y correo SIMULADO.
- La prueba visual interactiva quedó pendiente por el bloqueo del entorno de navegador. No se afirma una revisión visual completa.

La configuración real de Preparar POS debe guardarse sobre sus mismos seis pasos, usando el editor de administrador; los destinatarios reales se registran con el botón, sin direcciones inventadas. Los recorridos completos se demostraron con datos sintéticos en H2 aislada, sin alterar ejecuciones históricas ni enviar correos reales.
