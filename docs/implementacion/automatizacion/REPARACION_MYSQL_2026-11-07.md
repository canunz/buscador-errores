# Reparación del esquema: verificada después de aplicación administrativa

## Estado actual

El usuario informó que ejecutó el script con su cuenta administradora. Se verificó en la misma base local que las siete columnas ya existen y que la proyección JDBC funciona. Una consulta Hibernate real sobre `EjecucionPasoEntity` y el mapeo `EjecucionMapper.toDomain` leyeron correctamente los 25 registros, sin iniciar Spring Boot, HTTP, jobs o migraciones.

El hash de los nueve campos preexistentes coincide exactamente con el registrado antes de la migración: `6f75467b27e39bf142b31a8780c1951edd4649834624e0866893c8d467e09409`. Se conservaron las 25 filas y sus valores originales. El error de columna faltante queda corregido y comprobado a nivel JDBC/JPA; no se inició una sesión HTTP del usuario para probar la pantalla.

En esa comprobación la tabla `u_sucursal` aún estaba ausente; su creación posterior se validó durante la entrega del [flujo de evidencias](GUIA_FLUJO_EVIDENCIAS.md). Esta reparación inicial del checklist no acreditaba por sí sola la integración de la automatización.

## Antecedente del intento rechazado

El usuario autorizó añadir las columnas faltantes. Se identificó la conexión de la instancia `BackendApplication` PID 36924 y se verificó el destino local: `127.0.0.1:3307`, base `dima_bug_staging`, MySQL `8.0.46-0ubuntu0.24.04.4`.

Se confirmó que faltan las siete columnas del contrato/revisión. El esquema contiene además tres campos legados de snapshot (`paso_orden_capturado`, `paso_instruccion_capturada`, `paso_critico_capturado`); se conservan sin reinterpretar ni modificar. No se crean tablas duplicadas ni se aplica backfill.

El intento aditivo fue rechazado con **SQLState 42000, código MySQL 1142: privilegio ALTER insuficiente**. La autorización del usuario está recibida; el bloqueo es el permiso de la cuenta MySQL de la aplicación. No se cambiaron credenciales, privilegios, configuración del backend o datos.

Antes y después del rechazo se verificaron 25 filas. El SHA-256 de todos los valores preexistentes, incluidos los tres campos legados, fue `6f75467b27e39bf142b31a8780c1951edd4649834624e0866893c8d467e09409`. No se guardaron contraseñas ni contenido de filas en el registro.

En ese momento la reparación quedó pendiente. [APLICAR_REPARACION_STAGING.sql](APLICAR_REPARACION_STAGING.sql) contiene el cambio que el usuario aplicó posteriormente. No es necesario conceder privilegios permanentes de DDL a la cuenta de la aplicación. El script no debe repetirse ahora que las columnas ya existen.

Después de aplicarlo se verificaron la consulta JDBC y la consulta/mapeo Hibernate. No se iniciaron sesiones de usuario ni se modificó una ejecución para ensayar. El usuario puede volver a abrir el checklist para comprobar su flujo autenticado.

En el intento inicial se observó también que `u_sucursal` no existía. Esa creación no se intentó entonces: la autorización de esa reparación estaba acotada a las columnas del checklist. El estado posterior se documenta en la guía del flujo.
