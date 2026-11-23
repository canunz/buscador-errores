# Preparación JDBC del backend QA — 2026-11-12

Actualización 2026-11-13: JWT y supervisor implementados; consultar `PREARRANQUE_2026-11-13.md`. Las condiciones pendientes enumeradas abajo describen el cierre de la etapa del día 12.

Estado: preparación compilada y probada offline. No autoriza desbloqueo, conexión JDBC autenticada ni arranque Spring.

## Archivos y alcance

- `application-qa-local.properties`: destino fijo `127.0.0.1:13318/dima_bug_qa_mvp`, `VERIFY_CA`, sin fallback al almacén del sistema, sin recuperación de clave pública y SocketFactory exclusivo QA. Se mantienen `validate`, inicialización SQL desactivada y los controles de correo/automatización en false.
- `src/dimabug/qa/PinnedSocketFactory.java`: rechaza otro destino o propiedades débiles; verifica el hash del almacén público; deja validar CA/vigencia a Connector/J y comprueba la huella del certificado en la misma sesión TLS antes de devolver el socket al controlador. Cierra conexiones rechazadas.
- `CreatePublicTruststore.java`: importa únicamente la CA pública a un JKS nuevo, nunca sobrescribe ni importa claves privadas. La cadena `qa-public-ca-integrity` es una contraseña pública de integridad de certificados públicos; no es una credencial. La integridad efectiva está vinculada también al SHA-256 registrado.
- `VerifyLoader.java`: prueba de carga aislada de clases y ausencia de configuraciones ajenas a QA, sin invocar Spring.
- `JdbcProbe.java`: preparado para una ventana futura autorizada. Una conexión con el mismo JDBC y TLS, consultas de identidad/esquema y estado SSL; sin DML. **No ejecutado**. Rechaza modificaciones de la URL prevista y no imprime excepciones del proveedor.
- `Qa.Custody.psm1`: valida propietario/ACL/identidad QA y recupera únicamente la contraseña de aplicación desde la custodia DPAPI existente. Devuelve SecureString, sin generar credenciales. El descifrado del documento completo ocurre en memoria; sus campos ajenos a la contraseña de aplicación no se entregan al backend. Los strings administrados no admiten borrado garantizado.
- `Qa.Jdbc.psm1`: construye ProcessStartInfo con entorno limpio, contraseña desde DPAPI y JWT recibido como SecureString; almacén y cargador explícitos. No inicia procesos. El propietario debe limpiar inmediatamente las variables sensibles mediante `Clear-QaBackendStartInfo` y no mostrar el objeto.
- `prepare_candidate.py`: compilación Maven offline en un directorio nuevo bajo `qa/runtime/backend-builds`; copia solo Java, perfil QA e imagen pública del correo. No lee configuraciones históricas ni ejecuta pruebas Maven, aplicaciones o SQL. Lee de Docker únicamente CA y certificado públicos. No descarga imágenes ni modifica contenedores.
- `verify_controls.py` y `test/ControlsTest.java`: verificaciones Java offline, sin contraseñas ni red.
- `../Start-QaBackend.ps1`: verifica manifiesto/paquete; `-CheckCustody` comprueba carga DPAPI y descarta el resultado. `-Start` queda expresamente bloqueado hasta preparar y autorizar la ventana supervisada; se retiró el arranque directo anterior.
- `../qa_manifest.py`: registra paquete TLS, hashes y fuentes de compilación además del JAR; detecta cambios posteriores en fuentes o archivos generados.

No se modificaron fuentes funcionales Java, POM original, frontend, esquema, privilegios ni configuraciones remotas.

## Candidato generado

Paquete: `qa/artifacts/backend-20261113T020232054458Z/bundle.json`.

JAR backend SHA-256: `ec789866bdce8087d05aff03452b6978b6b0b5eb1d5fcbc9d88503a8996ecb8f`.

Compilación con Java 21, Spring Boot 4.1.1 y Connector/J 9.7.0. JAR QA sin `application.properties`, perfiles staging/Gmail/SMTP ni scripts SQL. Es un artefacto específico de QA, no una propuesta de despliegue de producción. El empaquetado del complemento se actualizó por separado después de las pruebas; el bundle registra su archivo final y las fuentes correspondientes.

Se preservaron los intentos incompletos y versiones intermedias en rutas ignoradas; solo se registra el paquete final. El manifiesto no acredita una compilación reproducible bit a bit. No se ejecutó la suite funcional Maven en esta etapa.

## Verificaciones realizadas

1. Compilación backend y complemento correctas, sin Spring.
2. Recursos del JAR limitados a la lista permitida para QA.
3. 26 comprobaciones Java correctas: destino/puerto, CA obligatoria, rechazo de fallback/recuperación de clave pública/versiones débiles/almacén alterado, validación PKIX con CA real, CA incorrecta, vigencia, huella correcta/incorrecta y cierre de sockets ante fallos simulados. Las pruebas del flujo del SocketFactory usan sockets simulados; no demuestran por sí solas el comportamiento de una sesión JDBC autenticada real.
4. Cargador de Spring Boot accede al complemento y Connector/J sin cargar recursos de configuración ajenos a QA; no se invocó BackendApplication.
5. Sintaxis PowerShell y exclusión de variables heredadas sensibles correctas.
6. Carga real DPAPI y validación de ACL/identidad correctas desde el contexto normal de Windows. En el sandbox de Codex, LocalAppData está redirigido y la custodia no aparece; no se creó ni copió allí.
7. Cinco pruebas de integridad del paquete: acepta el paquete intacto y rechaza alteración del helper, cambios de fuentes backend/TLS y referencias fuera del directorio del paquete.

No se generaron credenciales, ejecutó SQL, desbloquearon cuentas o iniciaron Angular/Spring. El estado de bloqueo MySQL no se volvió a consultar en esta etapa; se conserva la última verificación previa y no se ejecutó ningún cambio sobre cuentas.

## Condiciones pendientes y procedimiento de la próxima etapa

1. Definir/aprobar custodia de JWT QA independiente (mínimo 32 bytes UTF-8). No reutilizar contraseñas MySQL ni clave AES Gmail. No se generó JWT en esta etapa.
2. Preparar y revisar un supervisor de ventana independiente: identidad del proceso Java, plazo finito, parada del PID exacto y bloqueo/cierre de sesiones aun si el lanzador falla. El supervisor anterior de pruebas SQL no basta para detener el backend.
3. Antes de desbloquear, reconfirmar instancia/volumen QA, cuentas bloqueadas, cero sesiones, puertos, privilegios, respaldo recuperable y baseline de estructura/recuentos. Solo consultas autorizadas en esa etapa.
4. Solicitar autorización específica para la ventana. Desbloquear solo la cuenta de aplicación del origen proxy; mantener administrador bloqueado.
5. Ejecutar primero JdbcProbe con entorno y TLS del bundle. Cerrar su conexión antes de abrir Hikari (el límite de aplicación es cuatro conexiones). Ante fallo, bloquear/cerrar y detener el procedimiento.
6. Arrancar el candidato con PropertiesLauncher, helper exacto por hash, `loader.config.location` explícito, perfil QA y configuración externa explícita. Verificar cada conexión TLS del pool, no solamente el probe independiente.
7. Exigir validación Hibernate satisfactoria sin sustituir `validate` por `update/create`. Los 44 objetos del esquema inventariado no constituyen todavía validación de tipos por Hibernate 7.4.5.
8. Revisar registros sanitizados y consultar únicamente `GET /api/health`. Ese endpoint es liveness estático y no certifica MySQL. No solicitar códigos ni invocar endpoints de negocio: aun sin despacho pueden escribir registros.
9. Detener PID exacto, bloquear cuenta, cerrar sesiones residuales, confirmar ausencia de cambios de negocio y conservar hashes/evidencia sanitizada. Bloquear la cuenta por sí solo no termina conexiones existentes.

## Reversión

En esta preparación no hay cambios MySQL que revertir. Las versiones anteriores del manifiesto se conservan en `qa/history`; los artefactos intermedios no están registrados. Para retirar los cambios QA, revisar un diff limitado a los archivos enumerados, conservando siempre modificaciones locales previas; no usar reset/clean global.

Durante la futura ventana: interrumpir ante fallo TLS, identidad inesperada, error de esquema, automatizaciones activas o escritura inesperada. Primero parar el proceso QA y cerrar acceso/sesiones; después comparar baseline y conservar evidencia. No restaurar datos, aplicar DDL ni modificar privilegios automáticamente. Staging y producción permanecen fuera del procedimiento.
