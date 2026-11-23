# Resultado TLS y privilegios MySQL QA — 2026-11-12

Prueba autorizada completada. Evidencia publica sanitizada: `docs/07_Pruebas/evidencias/mysql-qa-tls-privilegios-2026-11-12.json`, SHA-256 `7b82e5bc2381c84e727d85f95a1b9bf0a6e1844d6f9be573d0f2549725035192`.

## Estado final verificado

- MySQL QA y proxy existentes iniciados con autorizacion expresa, sin recreacion ni cambios de volumen/red/imagen. No se tocaron los contenedores qa016/qa0174, staging o produccion.
- Ambas cuentas QA ACCOUNT LOCK. Cero sesiones dimabug_qa_app. Un nuevo intento de login por TLS se rechazo con errno 3118; cadena y huella se verificaron tambien en ese intento.
- Administrador QA nunca se desbloqueo durante esta prueba. App se desbloqueo exclusivamente para la ventana acotada; permisos conservados, sin grants adicionales ni credenciales nuevas.
- Baseline/final coincidentes para cuentas, grants por tabla, roles, inventario, columnas, claves/indices/FK y conteos. 44 tablas; unica fila de negocio/control: u_organizacion_control(1). Marcador sintetico ausente, ninguna tabla/cuenta canario creada. No se seleccionaron tokens ni hashes de contrasenas.
- Guardian independiente armado antes de UNLOCK, cierre normal verificado por root y por guardian antes de desarmarlo. No quedaron sesiones app. No se ejecuto un timeout/caida real durante la ventana: esos caminos se comprobaron mediante simulaciones.

## TLS y autenticacion

PyMySQL 1.2.4 instalado exclusivamente en qa/runtime/tls-client/site desde wheel de hash fijado, sin dependencias opcionales ni modificacion de Python global. Cliente con TLS obligatorio y sin fallback, certificados obligatorios, TLS minimo 1.2 y huella de hoja validada en SSLContext.wrap_socket antes de que PyMySQL envie autenticacion. Clave app recuperada mediante DPAPI CurrentUser en memoria; sin SQL con password, argv, variables persistentes ni registros con credenciales.

Conexion real Windows 127.0.0.1:13318 -> proxy -> app@172.21.0.3: TLSv1.3, TLS_AES_256_GCM_SHA384; CA/huella validas, UUID/cuenta/origen y base esperados, Ssl_cipher no vacio.

Se mantiene la limitacion del certificado autogenerado sin SAN: no valida el nombre 127.0.0.1. La prueba usa el mecanismo CA MAS pin expresamente aprobado para QA local; no equivale a VERIFY_IDENTITY por nombre ni configura TLS de Spring/Angular. No se copiaron claves privadas, generaron certificados o instalo confianza global.

## Casos ejecutados

| Grupo | Resultado |
|---|---|
| SELECT, INSERT, UPDATE, DELETE sinteticos | 4 satisfactorios; tres DML afectaron una fila cada uno, lecturas A/B correctas |
| Transaccion sintetica | ROLLBACK y ausencia del marcador verificados |
| UPDATE/DELETE en cinco tablas gm_* | 10 denegaciones, errno 1142 |
| INSERT cero filas en gm_cuenta_oauth | Denegado, errno 1142 |
| SELECT sin datos sobre mysql.user | Denegado, errno 1142 |
| CREATE/ALTER/DROP no mutantes bajo precondiciones | 3 denegaciones, errno 1142 |
| CREATE USER sobre cuenta ya existente | Denegado, errno 1227 |
| GRANT a destinatario inexistente | Denegado, errno 1142 |

Total real: **4 positivos y 17 negativos denegados**, ninguno inconcluso. No se aceptaron errores de existencia/sintaxis como prueba de autorizacion. El caso global es representativo; la ausencia del resto de privilegios globales, roles y PROXY se verifico mediante metadatos, no ejecutando operaciones destructivas.

Antes de desbloquear pasaron **9 pruebas simuladas**: ausencia de TLS, CA no confiable, certificado invalido, pin incorrecto sin autenticacion, pin correcto, deadline/muerte del padre, orden bloqueo/cierre, rechazo de otra identidad y cierre fallido no declarado satisfactorio.

PyMySQL no expone SQLSTATE en sus excepciones: se conserva null con una nota explicita. La clasificacion funcional usa errno 1142/1227, no un SQLSTATE inventado. Registros y metadatos internos de InnoDB/FULLTEXT pueden cambiar al probar DML; la coincidencia constatada corresponde a filas, permisos y estructura, no a igualdad binaria de archivos fisicos.

## Herramientas y limites

- tls_support.py: raiz local, CA/pin y acceso DPAPI sin salida de credenciales.
- watchdog.py: proceso independiente oculto con plazo monotono 180s; bloquea app y cierra sus sesiones verificadas.
- test_controls.py: simulaciones sin SQL ni credenciales reales.
- run_privilege_test.py: sin --execute hace solo preflight de lectura; --execute abre la ventana temporal autorizada. NO repetir la prueba real automaticamente.
- requirements-client.txt: cliente fijado por version/hash.

Duracion registrada del ejecutor: 7.563 segundos. Reporte runtime original bajo qa/runtime/tls-runs/20261112T232943969837Z, con reporte fuente hasheado y codigo de la ejecucion identificado por SHA-256 en la evidencia.

No se arranco Angular/Spring, habilito envio de correo, ejecuto migraciones ni modifico autenticacion de DimaBug. No se realizo restauracion del respaldo, carga de fixtures de negocio, carga sostenida ni validacion Hibernate. Esas actividades no quedan aprobadas por esta prueba.
