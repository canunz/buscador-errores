# MySQL QA nuevo — puerto 13318

Preparacion posterior, sin ejecutar SQL: `schema/PLAN_ESQUEMA.md` describe la propuesta de 44 tablas, orden historico, creacion condicionada de esquema/cuentas, grants por tabla, origen proxy exacto, backup/restauracion y gates de aprobacion. Scripts y plantillas permanecen sin ejecutar; no leer credenciales para revisarlos. `python -I -B qa/mysql/schema/verify_schema_proposal.py` y `test_schema_controls.py` solo comprueban archivos.

Actualización: proxy aprobado aplicado; MySQL y proxy QA activos con volumen preservado. Puerto Windows accesible; MySQL devuelve 1130 porque no existen aún usuarios autorizados desde el proxy. Aislamiento local verificado; autenticación funcional pendiente. Ver `RESULTADO_PROXY.md`. No repetir bootstrap ni crear usuarios/migraciones sin alcance autorizado. El párrafo siguiente conserva el diagnóstico anterior a la aplicación del proxy.

Estado 2026-11-12: custodia generada y verificada por el usuario, respaldo OneDrive confirmado sincronizado. Imagen aprobada descargada; contenedor, red y volumen nuevos creados. MySQL completó su inicialización interna; NO se creó esquema/usuarios del MVP ni se aplicaron migraciones. La prueba TCP desde Windows falló: HostConfig contiene el binding pero NetworkSettings.Ports indica una lista vacía para 3306/tcp en la red interna. Se detuvo exclusivamente el nuevo contenedor (Exited 0); volumen y custodia conservados, anteriores siguen detenidos. Pendiente decidir conectividad sin degradar aislamiento. No repetir -Create.

## Decisión autorizada

El usuario eligió una instancia nueva en 13318. Se cambió únicamente el destino del perfil QA de 13316 a 13318 y sus comprobaciones/documentación. Backend permanece en 19090 y Angular en 14200. No se alteraron los perfiles staging/producción, el proxy original ni las herramientas operativas.

La huella anterior del candidato queda conservada en `../history/QA-local-f823d61-f02e6f3e165a.json`; el manifiesto actual se regenera tras esta preparación. La clasificación inicial de 120 archivos no se reescribe.

## Recursos propuestos

| Recurso | Nombre o configuración |
|---|---|
| Proyecto Compose | dimabug-qa-local-13318 |
| Contenedor nuevo | dimabug-qa-mvp-13318-mysql |
| Puerto | 127.0.0.1:13318 -> 3306 |
| Volumen nuevo | dimabug-qa-mvp-13318-mysql-data |
| Red nueva | dimabug-qa-mvp-13318-net, internal=true |
| Límites | 2 CPU, 4 GiB de memoria |
| Imagen | mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b |
| Plataforma | linux/amd64 |
| Política de descarga/reinicio | never / no |
| Raíz MySQL | autenticación por archivo secreto dedicado; acceso root limitado a localhost del contenedor |
| Esquema/usuario de aplicación | NO se crean automáticamente; dima_bug_qa_mvp/dimabug_qa_app siguen pendientes |

`compose.qa-local.json` fija la imagen oficial aprobada mediante el digest del manifiesto linux/amd64, verificado contra los metadatos del registro oficial sin descargar capas. No depende de QA_MYSQL_IMAGE. No usar latest ni sustituir el digest. MySQL 8.0.46 es la última versión de 8.0, rama fuera de mantenimiento comunitario desde abril de 2026: se acepta como candidato de compatibilidad QA aislado, no como recomendación de producción. La compatibilidad funcional sigue pendiente de esquema completo y pruebas.

El secreto previsto es `qa/runtime/secrets/mysql-root.txt` (ignorado), montado en /run/secrets; ese archivo NO existe ni se ha creado una contraseña. No crear un archivo ficticio para superar los controles. No copiar claves AES, JWT, tokens ni contraseñas de staging.

## Custodia aprobada, ejecución pendiente

- Tres contraseñas independientes mediante RNG criptográfico: root, dimabug_qa_schema y dimabug_qa_app. Nunca imprimirlas ni pasarlas como argumentos.
- Custodia primaria DPAPI CurrentUser fuera del repositorio, con ACL restrictiva; requiere conservar el perfil Windows.
- Respaldo portable cifrado autenticado, frase introducida de forma oculta y recuperación verificada sin revelar valores.
- Ubicación externa confirmada: `C:\Users\carol\OneDrive\Estudios y desarrollo personal\DimaBug-QA-MySQL-13318\credentials.portable.json`. La sincronización remota debe confirmarla el usuario; la recuperación local no prueba que OneDrive haya sincronizado. No se almacena la frase con el respaldo.
- Compose local secrets no cifra el archivo de entrada. Materializar el puente root solo para operaciones autorizadas, con ACL restrictiva y exclusión Git comprobada; no eliminar su origen mientras sea necesario para recrear el montaje.
- Root solo para inicialización/gestión local del contenedor. Schema: DDL necesario exclusivamente en dima_bug_qa_mvp, DROP condicionado a migración revisada, sin privilegios globales ni GRANT OPTION. App: SELECT/INSERT/UPDATE/DELETE exclusivamente en ese esquema, sin DDL ni privilegios globales.
- No generar JWT ni claves Gmail en esta fase. No instalar usuarios ni ejecutar grants durante la preparación de custodia.
- Evidencias: solo resultados de verificaciones, nunca secretos, hashes de contraseñas ni contenido de la custodia. Confirmar exclusiones Git y del manifiesto antes de crear archivos privados.

`Initialize-QaMysqlCustody.ps1` (PowerShell 7.4+) genera tres contraseñas aleatorias de 256 bits después de confirmar una frase oculta de al menos 20 caracteres y crear destinos con ACL usuario actual/SYSTEM. Custodia primaria en `%LOCALAPPDATA%\DimaBug-QA-MySQL-13318\credentials.dpapi`; respaldo AES-256-GCM con PBKDF2-SHA256 (600000 iteraciones, salt aleatorio, nonce aleatorio). Ambos archivos se releen y descifran para verificar igualdad. No genera el archivo root en texto plano ni llama a Docker/SQL. No sobrescribe una custodia existente. No se ha ejecutado su modo de generación durante la preparación.

Ejecutar en una terminal dedicada sin grabación de entrada; cerrar al terminar para reducir permanencia de cadenas administradas en memoria. Conservar la frase en un gestor o soporte independiente, nunca en el chat. Compartir solo el JSON final. Si falla, no eliminar archivos ni regenerar automáticamente: `-VerifyOnly` permite revalidar las copias existentes sin generar contraseñas. `-SelfTest` utiliza exclusivamente datos sintéticos en memoria y comprueba recuperación, rechazo de frase incorrecta y detección de alteración.

No se monta /docker-entrypoint-initdb.d ni se declaran MYSQL_DATABASE, MYSQL_USER o MYSQL_PASSWORD. Iniciar un MySQL vacío sí inicializaría sus tablas de sistema y root: también requiere autorización. El esquema del MVP, el usuario limitado y sus grants se prepararán/ejecutarán explícitamente en otra fase autorizada. Event scheduler, local infile y exportación de archivos del servidor quedan deshabilitados; no se configura replicación ni binlog.

## Recursos existentes preservados

## Inicialización controlada

`Start-QaMysqlInstance.ps1` sin parámetros solo valida precondiciones. Con `-Create`, usa el contexto local desktop-linux, rechaza colisiones de nombres/puertos, lee la custodia DPAPI, materializa únicamente el secreto root en `qa/runtime/secrets/mysql-root.txt` con ACL usuario actual/SYSTEM y verifica su exclusión Git. Descarga exclusivamente la imagen/digest aprobados y crea el servicio definido sin builds ni descarga implícita por Compose. La inicialización crea las tablas internas de MySQL y root; no crea dima_bug_qa_mvp ni sus usuarios ni aplica migraciones.

El archivo root de entrada permanece restringido para recreación del montaje; Compose no lo cifra. No publicarlo ni incluirlo en respaldos sin cifrar. Si una fase falla, no repetir `-Create` ni eliminar volumen/secretos: primero inspeccionar el estado. La disponibilidad se prueba por TCP dentro del contenedor para no confundir el servidor temporal de inicialización con el definitivo. La verificación final comprueba loopback y red interna. No compila ni arranca backend/frontend.

## Recursos anteriores

- dimabug-qa016-mysql, detenido, configura 127.0.0.1:13316; volumen dimabug-qa016-mysql-data.
- dimabug-qa0174-mysql, detenido, configura 127.0.0.1:13317; volumen dimabug-qa0174-mysql-data.
- Ambos usan la imagen local mysql:8.0.36 y política de reinicio no.

No reutilizar sus volúmenes ni cambiar su red/puertos/política. No ejecutar prune, down -v, rm ni eliminación de imágenes. Un puerto sin escucha no demuestra que no esté configurado en un contenedor detenido.

## Comprobaciones permitidas ahora

```powershell
python -I -B qa/mysql/verify_mysql_preparation.py
# Lectura opcional de motor local, nombres y puertos; requiere psutil ya disponible:
python -B qa/mysql/verify_mysql_preparation.py --inspect-docker
```

La inspección no muestra variables de contenedores ni logs. Rechaza colisiones de nombres o puerto y no dispone de opción para crear o arrancar recursos. Nombres libres son una observación temporal, no una reserva.

Una validación Compose sin interpolar puede comprobar estructura sin proporcionar imagen/secreto. No es una validación integral de disponibilidad, autenticación, opciones de una imagen concreta ni conectividad. No usar `config --environment` ni pegar configuración resuelta que incluya secretos.

## Condiciones antes de implementar

1. Aprobar imagen exacta/digest y su relación con la versión objetivo, sin descargarla todavía por esta definición.
2. Revalidar contexto local, nombres y 13318, con los contenedores anteriores detenidos; la definición por sí sola no impide que Compose reutilice nombres si alguien omite esa comprobación.
3. Autorizar custodia independiente y recurso MySQL nuevo. No se obtiene autorización de creación por ejecutar el verificador.
4. Validar la configuración resuelta sin mostrar secretos y crear únicamente los recursos nuevos expresamente autorizados.
5. Comprobar loopback, red interna sin conexiones externas y acceso host->MySQL en Docker Desktop. La red declarada no sustituye esa prueba efectiva ni bloquea por sí sola todas las comunicaciones del backend Windows.
6. Consolidar DDL: falta esquema base completo; varios scripts incluyen USE dima_bug_staging y uno de tickets contiene DROP TABLE. No ejecutarlos directamente. JPA validate no crea tablas.
7. Preparar usuario mínimo y prueba de denegación contra un esquema canario SOLO dentro de QA, sin intentar conectar a DigitalOcean con esas credenciales.
8. Cargar fixtures sintéticos y comprobar identidad de instancia, esquema y grants, siempre con autorización posterior de SQL.

No se ha añadido automatización de up/pull/creación de usuarios, SQL o arranque del MVP. La autenticación funcional con correo ficticio sigue pendiente y todos los transportes permanecen deshabilitados.
