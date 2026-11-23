# Preparación QA local — DimaBug MVP

Estado: archivos preparados; NO se ha creado base/usuario, generado credenciales, ejecutado SQL, compilado, iniciado aplicaciones MVP ni desplegado. Docker Desktop fue iniciado por una autorización posterior independiente. No hay autorización de ejecución por la existencia de estos archivos.

## Línea base y alcance del candidato

Rama `integracion-final-uat`, HEAD `f823d616e0150925d29d4d01e99d6510b9643f13`.
Los 120 archivos pendientes iniciales (55 modificados y 65 nuevos) están enumerados individualmente en `baseline-pendientes.json`, con categoría, motivo, estado Git y SHA-256 antes de esta preparación:

| Categoría | Archivos |
|---|---:|
| Funcionalidades/configuración/esquema del MVP | 59 |
| Pruebas y fixtures | 31 |
| Documentación y evidencias | 14 |
| Herramientas operativas | 16 |

Esta clasificación es inventario, no una exclusión del compilador. En particular, la utilidad `correo/maintenance/RecuperarGmail.java` se clasifica operativa, pero continúa donde estaba. No se cambia Maven ni se elimina su empaquetado. Decidir excluirla o modificar funcionalidades requiere consultar al usuario primero. Los tests de esa utilidad también se identifican como soporte operativo. Las dependencias OCR y el SQL de cola forman parte de las dependencias del MVP, no se ejecutan automáticamente.

La definición de candidato de trabajo incluye el contenido actual del repositorio y esta preparación QA, identificado en `candidate.json`. No se ha construido ni aprobado un artefacto de release. El manifiesto incluye fuentes, pruebas, documentación y herramientas para trazabilidad; esa inclusión no implica que deban publicarse en el despliegue.

## Configuración independiente

- Perfil `qa-local`, sin activar staging/gmail/smtp ni importar sus archivos.
- API fija `127.0.0.1:19090`; MySQL propuesto `127.0.0.1:13318/dima_bug_qa_mvp`.
- Usuario previsto `dimabug_qa_app`: no ha sido creado ni se ha definido contraseña.
- JPA `validate`, SQL init `never`, sin DDL ni seeds automáticos.
- JWT requerido vía `QA_JWT_SECRET`; DB password vía `QA_DB_PASSWORD`. No se incluyen valores ni se toman los de otros entornos.
- OAuth/Gmail real false, cliente/secreto/clave Gmail vacíos; SMTP auto-config excluido.
- Despachador de códigos y seguimiento false; Procedimientos DESHABILITADO.
- Uploads/logs/tmp previstos en `qa/runtime/` y excluidos de Git. No se han creado datos ni credenciales.
- Angular: `qa-local` añade un target separado. El proxy original y las configuraciones development/production quedan intactos. El acceso API QA usa `/api` y el proxy específico a `127.0.0.1:19090`.
- La configuración Angular QA está destinada exclusivamente al servidor local del lanzador; no publicar su build estático bajo staging u otro origen. No se modificó la lógica global `api.ts` para alterar otros entornos.

No utilizar `reiniciar_backend_staging_local.py`, `Restaurar-GmailBackend.ps1`, el túnel 3307 ni los respaldos/custodia de staging para QA. No usar la cuenta remitente real como fixture.

## Lanzadores y barreras

Requieren PowerShell 7. Ambos lanzadores validan el manifiesto y **no arrancan sin `-Start`**. En esta fase solo se permite usarlos sin ese switch.

`Qa.Common.psm1` crea `ProcessStartInfo` y vacía todo el entorno heredado. Solo copia una lista de variables del sistema operativo necesarias para Windows; PATH se construye con el ejecutable y directorios de Windows. No se copian DB/JWT/Google/Gmail/SMTP, SPRING_APPLICATION_JSON, perfiles, argumentos Java/Node, proxies ni secretos QA preexistentes. El verificador del manifiesto también se ejecuta con entorno explícito y Python aislado.

El frontend invoca directamente el Angular CLI instalado mediante node, sin npm install, npx ni resolución automática de paquetes. El backend requiere JAR QA registrado y verificado, runtime preparado y secretos `SecureString` pasados explícitamente; no lee archivos .env, DPAPI ni credenciales de Windows. No genera secretos ni ofrece contraseñas por defecto. Los secretos solo se convierten en memoria para el entorno del proceso hijo, nunca a argumentos o salida. La limpieza del puntero BSTR no constituye garantía de borrado de todas las copias de cadenas en memoria administrada.

El backend usa únicamente la ubicación explícita de `application-qa-local.properties` y el perfil qa-local. No admite argumentos adicionales del operador que puedan apuntar al esquema staging. Rechaza puertos ocupados y no detiene otros procesos. Requiere `qa/runtime/tmp` previamente autorizado/preparado.

## Manifiesto reproducible del contenido

`qa_manifest.py` enumera archivos de Git y nuevos no ignorados. Guarda ruta relativa y SHA-256 de bytes; el hash agregado se calcula en orden por ruta. Excluye `qa/candidate.json` para evitar autorreferencia; rechaza custodia o artefactos/runtime privados incluidos accidentalmente en Git. Identifica HEAD, rama, fuentes y futuras salidas por hash. El candidato usa `QA-local-<HEAD corto>-<huella corta>`.

El manifiesto no almacena secretos ni intenta identificar archivos privados por su contenido. No incluir custodia o secretos reales entre las fuentes versionadas. Cambiar fuente, prueba, documento o herramienta inventariados invalida la comprobación; no se actualiza silenciosamente al arrancar. `--replace` exige revisión deliberada, no oculta que cambiaron los archivos. La huella es de bytes del workspace, por lo que también detecta cambios de finales de línea; el lockfile completo queda incluido.

Comprobaciones permitidas sin arrancar:

```powershell
python -I -B qa/verify_preparation.py
pwsh -NoProfile -File qa/Verify-Preparation.ps1
python -I -B qa/qa_manifest.py --check
pwsh -NoProfile -File qa/Start-QaFrontend.ps1
pwsh -NoProfile -File qa/Start-QaBackend.ps1
```

El manifiesto inicial se genera con `--write`, sin construir artefactos. No sustituirlo por el JAR antiguo de target. Actualmente `artifacts.backend` y `artifacts.frontend` quedan nulos y las versiones de servidor MySQL/Node/npm runtime pendientes.

Tras otra autorización, un build controlado puede registrarse con `--write --replace --backend-artifact <ruta dentro de qa/artifacts>`. El registrador exige el perfil QA idéntico y clases del flujo nuevo, verifica el hash y asocia la huella de fuentes. No demuestra por sí solo que un build sea reproducible o que todas las clases correspondan a esas fuentes: se debe conservar evidencia del build autorizado y validar huella antes/después. No se ha ejecutado ese registro ni se ha creado qa/artifacts.

## Cambios de esta preparación

Modificado: `frontend-dimabug/angular.json`, únicamente adición de configuraciones build/serve qa-local.

Creado:

- `dima-bug-backend/src/main/resources/application-qa-local.properties`.
- `frontend-dimabug/proxy.qa-local.json`.
- `frontend-dimabug/src/environments/environment.qa-local.ts`.
- `qa/.gitignore`, `baseline-pendientes.json`, `candidate.json` y este README.
- `qa/Qa.Common.psm1`, `Start-QaBackend.ps1`, `Start-QaFrontend.ps1`.
- `qa/qa_manifest.py`, `verify_preparation.py`, `Verify-Preparation.ps1`.

No se modifican defaults de desarrollo/producción, lógica de negocio, scripts operativos anteriores, lockfile ni credenciales. El chequeo compara los hashes de los 119 archivos de partida distintos de angular.json para detectar sobrescrituras.

## Pendientes que requieren aprobación

1. Revisar y aceptar el alcance funcional de los 120 archivos y las decisiones de empaquetado pendientes; no hacer commits todavía.
2. Motor Docker local habilitado por autorización independiente. Se descubrieron dos MySQL detenidos en 13316/13317; se preservan. El usuario eligió una instancia nueva en 13318. Confirmar imagen exacta, custodia y nombres/puerto antes de crearla; véase mysql/README.md.
3. Consolidar/revisar DDL y fixtures sintéticos; no ejecutar scripts destinados a reparar staging ni usar DDL automático para ocultar diferencias.
4. Crear custodia/secrets QA independientes y directorios runtime en una fase autorizada. No reutilizar las claves recuperadas de Gmail.
5. Construir artefactos desde este candidato, registrar hashes y herramientas, comprobar aislamiento de conexión y almacenamiento y autorizar el arranque.
6. Resolver mecanismo de autenticación funcional sin correo real. No existe un capturador runtime QA implementado aquí; los mocks actuales pertenecen a tests. No se añade bypass de autenticación, endpoint de códigos, usuario hardcodeado, Mailpit ni transporte alternativo. Con esta base bloqueada no se acredita login completo ni entrega de correo.
7. Ejecutar después los 54 casos y la restauración, con pruebas MySQL reales y evidencias sanitizadas. Esta preparación no es un GO de despliegue.

## Preparación posterior de MySQL nuevo en 13318

Se actualizaron el perfil QA, el mensaje del lanzador backend y la validación estática al puerto 13318. No se modificaron archivos originales de staging ni el resto de la línea base.

Se añadieron `mysql/compose.qa-local.json`, `mysql/verify_mysql_preparation.py`, `mysql/test_definition.py` y `mysql/README.md`. El Compose usa una imagen obligatoria pendiente de aprobación, descarga never, red interna y volumen nuevos; no monta SQL ni declara base o usuario de aplicación automáticos. No se creó ni arrancó el recurso ni se generaron credenciales.

El manifiesto anterior se conserva en `history/QA-local-f823d61-f02e6f3e165a.json`. El manifiesto actual identifica el nuevo estado con esos archivos; no se registra un JAR construido.
