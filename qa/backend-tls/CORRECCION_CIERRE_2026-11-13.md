# Corrección del cierre QA — 2026-11-13

## Diagnóstico y alcance

Se reprodujo que recargar Qa.Common con Import-Module -Force desde Qa.Jdbc elimina New-QaProcessStartInfo del ámbito del supervisor. El cierre principal depende de esa función y el catch original ocultaba la excepción. Es una causa probable sólida de la incidencia histórica; la excepción original no quedó conservada y no se presenta como evidencia recuperada.

Se corrigieron únicamente herramientas QA. No se modificaron Java funcional, perfil QA, POM, frontend, JAR backend, complemento TLS, credenciales ni privilegios. No se ejecutó SQL, consultó Docker, leyó DPAPI o inició Spring/Angular. El estado actual de cuentas no se volvió a consultar en esta etapa.

## Cambios

- Qa.Jdbc.psm1 y Qa.Supervisor.psm1: importaciones internas sin -Force. Cada ventana sigue usando un proceso PowerShell limpio y el manifiesto de fuentes.
- Qa.Close.psm1: adaptador PowerShell extraído y comprobable mediante transporte simulado. Captura fase, proceso iniciado, código de salida, timeout, validez del contrato, actor, ejecución, UTC y duración. No copia mensajes del proveedor, stdout/stderr, entorno o secretos al informe.
- Invoke-QaBackendSupervisor.ps1: comprueba dependencias antes de anunciar readiness; utiliza el adaptador nuevo y conserva su resultado detallado. Mantiene la parada Java, ambos vigilantes y el criterio conservador original. Valida que la confirmación del segundo vigilante corresponda al mismo actor/ejecución.
- close_qa_account.py: añade close_report sin cambiar el destino, las sentencias permitidas ni close_window. El reporte separa intento, respuesta al comando y verificación posterior de cuentas/sesiones. close() conserva su contrato booleano para consumidores anteriores. La CLI mantiene estado/closed y añade diagnóstico sanitizado.
- cleanup_sentinel.py: conserva diagnóstico por intento y distingue un diccionario closed=false de un resultado exitoso. Su ejecución operativa genera reportes correlacionados; no se ejecutó ese modo aquí.
- Request-QaClose crea el marcador con CreateNew y tolera su existencia sin reescribirlo. Evita la carrera entre escritores del cierre principal.

## Semántica de los estados

lock_attempted indica intento local; no prueba que MySQL recibiera el comando. lock_acknowledged solo es true si la llamada SQL del comando devolvió éxito. Un error de transporte deja su respuesta desconocida. account_locked_verified y app_sessions_zero_verified solo se establecen mediante las comprobaciones SQL posteriores. Un fallo de verificación no convierte una respuesta SQL satisfactoria en un bloqueo rechazado.

Un proceso que no llegó a iniciarse tiene lock_attempted=false; una respuesta perdida puede tener lock_attempted/lock_acknowledged desconocidos. confirmed exige proceso iniciado, exit 0, JSON válido y correlacionado, fase COMPLETE, closed=true y las tres confirmaciones afirmativas. Respuestas parciales, antiguas, de otro actor o con tipos incorrectos no confirman el cierre.

El reporte agregado conserva por separado el fallo del principal y la confirmación del segundo vigilante. database_state puede quedar CONFIRMADO por un canal independiente mientras el resultado operativo conservador sigue requiriendo atención por el canal fallido. No se eliminaron el respaldo ni las verificaciones finales independientes.

## Regresión ejecutada sin aplicaciones ni SQL

- 27 comprobaciones PowerShell: alcance/importaciones, contrato satisfactorio, closed=false, fase incongruente, salida no cero, timeout simulado, JSON inválido/incompleto, correlación/actor incorrectos, tipos inválidos, confirmación desconocida, dependencia ausente, sanitización, marcador idempotente y exclusión de variables staging.
- 9 casos nuevos Python: éxito, error de bloqueo, respuesta SQL seguida de verificación fallida, sesiones remanentes, cuenta no bloqueada, sesión desaparecida 1094, dos cierres concurrentes y reintentos de reportes true/false.
- 18 casos existentes de aislamiento y cierre con consultas simuladas. Se excluyó expresamente el caso que inicia un proceso padre de prueba.
- 5 casos existentes de integridad del paquete.

Las pruebas nuevas no leen custodias ni crean procesos hijos. Usan respuestas de transporte/SQL simuladas y datos canario públicos. Las pruebas de marcador e integridad solo usan archivos sintéticos bajo qa/runtime, ignorado por Git.

## Límites y siguiente autorización

No se probó hoy el bloqueo real en MySQL ni un cierre operativo completo. Los límites de espera existentes se conservan: un timeout sigue significando estado desconocido, no cuenta desbloqueada ni cierre confirmado. Antes de otra ventana debe aprobarse el candidato actualizado y reconfirmarse respaldo, cuentas y capacidad de cierre bajo la autorización SQL correspondiente.

No se alteraron registros del primer arranque ni manifiestos históricos. El JAR probado permanece idéntico; cambia el manifiesto de fuentes por esta corrección operativa. No hay permiso implícito para desbloquear cuentas o repetir el arranque.
