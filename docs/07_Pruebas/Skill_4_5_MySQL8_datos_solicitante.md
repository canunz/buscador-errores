# Skill 4.5 — validación MySQL 8 de datos del solicitante

Fecha: 2026-11-16 (America/Santiago). Ejecución válida: `5be46820f3b9`.

La denominación «Skill 4.5» se utiliza como etapa solicitada por el usuario. No se encontró una definición formal SKILL.md en el catálogo o repositorio; este informe acredita las verificaciones expresamente solicitadas, sin certificar requisitos no disponibles.

## A. Resultado de la migración
**APROBADA en MySQL 8.0.46 linux/amd64**, con imagen oficial local fijada como `mysql@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b`.

Se usó una instancia nueva y desechable, con servidor identificado por UUID exclusivo, almacenamiento tmpfs, sin volúmenes persistentes y sin puerto MySQL publicado. El proxy exclusivo solo publicó un puerto dinámico en 127.0.0.1, diferente de los puertos compartidos de QA. La red MySQL fue interna; el proxy operó sin capacidades, con filesystem de solo lectura e IP forwarding deshabilitado. No se reutilizaron cuentas, contraseñas, volúmenes ni bases existentes.

La cuenta de aplicación nueva solo recibió SELECT/INSERT/UPDATE/DELETE sobre su base exclusiva y REQUIRE SSL. El backend verificó CA y huella del certificado antes de autenticar; no se deshabilitó esa verificación ni se heredaron secretos de staging.

Migración exacta: `dima-bug-backend/src/main/resources/db/migration/V20261116_01__ticket_datos_solicitante.sql`.
SHA-256: `dae215451c54fc515d6638173d46074f38e6e47a39a09130621076376fa02fe8`.
Se ejecutó una vez en la instancia de la ejecución válida. No se modificó el archivo SQL ni código del MVP.

Se reprodujo el esquema versionado de 44 tablas de QA, incluidos los antecedentes de registro universal. El CREATE TABLE ticket se contrastó con V20261017_01__modulo_tickets.sql. Se verificó la creación de las ocho columnas, sus tipos, longitudes y nulabilidad, y que los metadatos anteriores no cambiaron:

| Columna nueva | Tipo | Admite NULL |
|---|---|---|
| sol_nombre_completo | VARCHAR(150) | Sí |
| sol_sucursal_id | INT | Sí |
| sol_sucursal_nombre | VARCHAR(100) | Sí |
| sol_departamento_id | INT | Sí |
| sol_departamento_nombre | VARCHAR(100) | Sí |
| sol_contacto | VARCHAR(50) | Sí |
| sol_equipo | VARCHAR(100) | Sí |
| sol_codigo_usuario | VARCHAR(80) | Sí |

`ck_ticket_sol_datos` figura como ENFORCED=YES. MySQL rechazó snapshots parciales con código **3819**, SQLSTATE **HY000**, y el nombre exacto de esa restricción. Se verificaron tanto los NULL históricos completos como los opcionales equipo/código vacíos convertidos a NULL en nuevas solicitudes. La validación obligatoria del formulario y del dominio sigue siendo necesaria: la compatibilidad con tickets anteriores exige admitir snapshots totalmente nulos.

## B. Integridad de tickets existentes
Se incorporaron tres tickets sintéticos anteriores: abierto, cerrado y en espera; con prioridades, fechas y asignaciones distintas. Se añadieron una fotografía propia y otra asociada a nota interna. No se utilizaron datos personales ni imágenes reales.

La huella de **todos los campos originales** de los tres tickets, antes de la migración y después de las pruebas funcionales, coincidió:
`d46cda8fe80b236efcae198e191fd163a10a4d7aa295b3e3c06378a86ae6e04a`.

Se conservaron metadatos y bytes de ambas fotografías anteriores. Se generó un respaldo SQL sintético previo, se comprobó gzip/hash y se restauró en otro esquema exclusivo del mismo servidor desechable; los tickets coincidieron y ese esquema temporal se eliminó. Este ensayo no constituye un respaldo de staging.

## C. Pruebas funcionales en MySQL real
**11 pruebas nativas satisfactorias, cero fallos, errores u omitidas**: un control previo de Hibernate y diez pruebas funcionales. Se añadieron cinco regresiones del comprobador de salida SQL, también satisfactorias.

| Prueba funcional | Resultado |
|---|---|
| Hibernate validate, TLS y cuenta DML sin CREATE TABLE | PASS |
| Consulta de ticket propio anterior; ticket ajeno 404 | PASS |
| Fotografía anterior propia; fotografía ajena e interna rechazadas | PASS |
| Creación con foto, snapshot canónico y solicitante autenticado inmutable | PASS |
| Catálogos activos mínimos; directorios y recursos internos bloqueados | PASS |
| Creación sin IP/AnyDesk, código de usuario ni fotografías | PASS |
| Fotografías reales sintéticas JPG, PNG y WEBP | PASS |
| Archivo falso, PDF y exceso de tamaño sin ticket ni archivos huérfanos | PASS |
| Datos inválidos, opciones inactivas y bypass JSON antiguo rechazados | PASS |
| CHECK real MySQL y NULL históricos preservados | PASS |

Se crearon tres tickets sintéticos nuevos. La prioridad manipulada CRITICA se ignoró y quedó MEDIA; tampoco se aceptaron solicitante_id, asignado_id o etiquetas de catálogo suministradas por el cliente. La ficha de usuario permaneció intacta. Se comprobó descarga de fotografías por el propietario y rechazo para otro autorregistro. Las respuestas de fotos conservaron Cache-Control private, no-store.

No se solicitaron códigos, no se crearon trabajos de correo y no se invocaron los mocks de envío/Gmail/OAuth. Las tablas de códigos permanecieron vacías y no se cargó ninguna cuenta OAuth.

## D. Compatibilidad con backend
Antes de migrar, Hibernate validate real rechazó el esquema por una columna sol_ faltante. Después de migrar, el contexto real de Spring Boot inició con **ddl-auto=validate**, sin DDL automático y con permisos DML de aplicación.

Las pruebas utilizaron filtros JWT, controladores, servicios, JPA, MySQL y almacenamiento filesystem reales. Los JWT fueron efímeros de QA y nunca se mostraron. También pasó la lectura optimizada JDBC/MySQL que usa Soporte, no solo el recorrido JPA del propietario. Las clases de producción compiladas para la prueba coincidieron con la compilación local actual; el manifiesto de fuentes identifica lo probado.

No es una prueba de navegador contra Nginx ni una verificación remota del JAR instalado: las llamadas HTTP se hicieron mediante MockMvc y la red de base de datos sí utilizó TLS real. La evidencia responsive/Angular anterior permanece en `qa/artifacts/solicitudes-datos-solicitante`.

## E. Riesgos y bloqueantes
No se encontró un bloqueo de migración o compatibilidad en el escenario MySQL probado. Permanecen estas condiciones operativas:

1. El ensayo representa la estructura versionada y tres tickets sintéticos, no una lectura nueva ni una copia de datos de staging. Se debe reconfirmar el esquema, versión y ausencia de conflictos antes de aplicar allí.
2. Los ALTER de MySQL pueden confirmar por separado y adquirir bloqueos de metadatos. No deben tratarse como una única transacción reversible. Si falla un paso, mantener mantenimiento, inspeccionar qué columnas/restricción existen y no repetir automáticamente el script.
3. Desplegar backend y Angular compatibles conjuntamente. El frontend anterior no envía los obligatorios nuevos; un despliegue solo del backend impediría crear solicitudes desde ese formulario anterior.
4. La nulabilidad completa es intencional para los tickets anteriores. No sustituye la validación obligatoria del backend para nuevos autorregistros.
5. El test de archivos inválidos no promete atomicidad distribuida ante corte eléctrico o caída abrupta. Se conserva el riesgo de reconciliación DB/filesystem ya documentado en PORTAL_SOLICITUDES_LOCAL.md.
6. Deben comprobarse espacio, permisos de adjuntos y límites multipart/Nginx para la pareja final. No se midió una ventana de DDL con carga real.
7. Esto no genera ni autoriza un release nuevo. Hay cambios locales previos ajenos al portal que deben excluirse del candidato; no incorporar WhatsApp/OCR por copiar indiscriminadamente el checkout.

Los intentos anteriores se conservaron como no aprobados. Se corrigieron únicamente herramientas QA: referencia de imagen por digest, extracción del bloque PEM público, conservación de celdas vacías en la salida SQL y aserción del código MySQL 3819 en vez de exigir una subclase de excepción Spring. No se modificó la migración ni el backend para hacer pasar pruebas.

## F. Recomendación y reversión
**Validación técnica favorable para un despliegue controlado en staging**, condicionado a una pareja backend/Angular identificada por hashes, preflight remoto actualizado, respaldos verificados y autorización separada. No se ha desplegado ni modificado staging.

Procedimiento mínimo propuesto:
1. Preparar la pareja de artefactos desde las fuentes seleccionadas, excluyendo cambios ajenos. Vincular sus hashes con este manifiesto y ejecutar las comprobaciones necesarias del candidato final.
2. Revalidar identidad/destino staging, versión instalada, esquema, capacidad y configuración. Respaldar y comprobar recuperación de DB, JAR, Angular, configuración y adjuntos.
3. Ventana de mantenimiento: detener solo staging y deshabilitar despacho durante la intervención. Aplicar la migración una vez, comprobar ocho columnas y CHECK, y comparar la línea base de datos existentes.
4. Instalar la pareja; arrancar con validate y correo controlado. Verificar salud, rutas, permisos y creación/consulta según una autorización de prueba independiente.
5. Reabrir solo con todos los controles críticos confirmados.

Si falla el despliegue: mantener mantenimiento y despacho deshabilitado; detener el backend nuevo; restaurar conjuntamente **backend, Angular y configuración previos compatibles con registro universal**. Conservar las ocho columnas y los snapshots; no ejecutar DROP COLUMN ni restaurar automáticamente DB o adjuntos. Verificar validate del backend de reversión, salud y restricciones por propietario antes de reabrir. El frontend anterior puede omitir temporalmente los nuevos datos declarados; esa es una regresión funcional esperada de la reversión, sin borrar lo ya almacenado.

No retroceder a una versión anterior al registro universal sin la cuarentena e invalidación de códigos ya exigidas por el procedimiento existente. Si el DDL quedó parcial, el estado del commit es desconocido o no se verifican permisos, mantener cerrado y solicitar revisión; no improvisar reparaciones ni aceptar un cierre desconocido.

## Cierre y evidencias
El MySQL, proxy, redes y fotografías temporales exclusivos se eliminaron. La lectura Docker independiente final confirmó **cero contenedores y cero redes Skill 4.5 restantes**. El inventario preexistente de contenedores, volúmenes y redes coincidió al inicio y al cierre de la ejecución aprobada. Las fuentes del backend permanecieron intactas.

Sin cambios de staging/producción, bases compartidas, OAuth ni commits; cero correos enviados.

Evidencia principal: `qa/artifacts/skill45-mysql8/5be46820f3b9/`:
- result.json y summary.json: resultado y controles sanitizados.
- source-manifest.json: huellas de fuentes probadas.
- before_migration_validate.log y native_functional.log: ejecuciones nativas sanitizadas.
- synthetic-pre-migration.sql.gz: respaldo exclusivamente sintético, con restauración aislada comprobada.
- runner-controls.log y SHA256SUMS.txt: regresiones y huellas de evidencia.

Herramientas nuevas o ajustadas: `qa/verification/skill45/run_mysql8.py`, `Skill45BeforeMigrationValidationTest.java`, `Skill45TicketMySqlTest.java`, `PinnedSocketFactory.java` y `test_runner_controls.py`. El verificador TLS reutiliza el existente de QA, parametrizado solo para el endpoint/certificado desechables.
