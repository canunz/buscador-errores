# Migraciones y provisión manual

Referencia 2026-11-09. No hay runner Flyway/Liquibase ni DDL base completo. La existencia de un SQL no demuestra que pueda reaplicarse o que corresponda a cualquier entorno. En esta consolidación **no se ejecuta ningún SQL contra staging**.

## Scripts originales

Los cinco archivos de `dima-bug-backend/src/main/resources/db/migration/` amplían conocimiento, soluciones, organización y tickets. `V20261017_02__tickets_adjuntos_y_nivel_etiqueta.sql` contiene `DROP TABLE IF EXISTS ticket_adjunto`; no debe ejecutarse a ciegas sobre datos existentes. Las referencias de guías antiguas a SQL ausentes no se reconstruyen automáticamente.

## Ampliaciones de implementación

En `docs/implementacion/automatizacion/`:

| Archivo | Propósito |
|---|---|
| 00_verificar_esquema_mysql.sql | Inspección del contrato esperado; revisar comentarios antes de usar |
| 01_contrato_paso_mysql.sql | Contrato de instancias de pasos |
| 02_sucursales_mysql.sql | Directorio de sucursales |
| 03_flujo_evidencias_desarrollo.sql | Solicitudes, fotografías/eventos y estados |
| 04_modalidades_por_paso_desarrollo.sql | Configuración/modalidades y adaptación de datos |
| 05_directorio_ciudad_cargo_desarrollo.sql | Campos del directorio |
| 06_edicion_administrativa_procedimientos.sql | Revisiones y auditoría administrativa |
| 07_hardware_procedimientos.sql | Asociación opcional al hardware |
| 08_correo_sin_sucursal.sql | Nullabilidad compatible con correo directo |
| 09_gmail_oauth_cifrado.sql | Persistencia OAuth cifrada y controles relacionados |
| 10_prueba_gmail_administrativa.sql | Prueba de correo y auditoría separadas |
| 11_materiales_soluciones_autoservicio.sql | Materiales por solución, privados por defecto |

Los scripts de reparación histórica como `APLICAR_REPARACION_STAGING.sql` no son un instalador general. Algunos archivos incluyen DML de adaptación o inicialización; no todos son DDL puro. Las aplicaciones confirmadas por el usuario y comprobaciones de lectura de tareas anteriores se describen en sus informes; no equivalen a certificar un entorno nuevo.

Antes de aplicar: confirmar instancia/esquema, versión MySQL, estructura real, permisos mínimos y respaldo/recuperación. Revisar sentencia por sentencia y cuáles ya están aplicadas. No usar ddl-auto=update/create ni init SQL automático contra staging. La aplicación no necesita CREATE/ALTER en operación normal.

La revisión de consolidación registra hashes/alertas de los SQL sin modificarlos ni ejecutarlos. El ensayo de restauración sigue pendiente.

[Estado global y evidencia](../README.md).
