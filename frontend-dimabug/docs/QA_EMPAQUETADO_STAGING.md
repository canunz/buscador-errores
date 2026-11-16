# DimaBug — corrección de configuración empaquetada

Fecha: 12 de octubre de 2026. Esta verificación sustituye el checksum del Backend publicado anteriormente en `QA_RESULTADOS.txt`; el Frontend no cambió.

## Causa raíz comprobada

`dima-bug-backend/src/main/resources/application.properties` no existía en el checkout usado para compilar y nunca estuvo versionado en este repositorio. La raíz `.gitignore` excluía expresamente ese archivo. Gitignore no excluye archivos del proceso Maven: la causa inmediata del JAR incompleto es que el recurso no estaba presente; su exclusión Git impide transportarlo de forma reproducible entre checkouts.

`pom.xml` usa Spring Boot 4.1.1, Java 21, recursos Maven convencionales y `spring-boot-maven-plugin`. No hay exclusiones de recursos ni configuración de empaquetado que requiera cambios. No se modificó el POM.

El usuario confirmó que el JAR anterior contenía el recurso y que `/etc/dimabug/staging.env` define las variables del servidor. No se inspeccionó el servidor ni se recuperó el contenido del recurso del JAR anterior.

## Corrección mínima

Se añadió configuración compartida sin valores de credenciales:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

| Variable externa | Propiedad efectiva |
|---|---|
| `DB_URL` | `spring.datasource.url` mediante el placeholder añadido |
| `DB_USERNAME` | `spring.datasource.username` mediante el placeholder añadido |
| `DB_PASSWORD` | `spring.datasource.password` mediante el placeholder añadido |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active`, reconocida directamente por Spring Boot |

Las variables personalizadas `DB_*` no equivalen automáticamente a `spring.datasource.*`. Las variables estándar `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`, si se suministran, mantienen la precedencia sobre el archivo empaquetado.

No se fija un perfil activo ni una URL, usuario o contraseña por defecto. La configuración compartida se carga también bajo el perfil `staging`; no necesita un `application-staging.properties` para este mapeo.

El proceso Java debe heredar las variables desde el mecanismo de ejecución existente. Spring Boot no carga por sí solo un archivo arbitrario llamado `/etc/dimabug/staging.env`: definir variables en ese archivo es suficiente únicamente si el servicio ya lo consume. No se modificó ese mecanismo ni ningún archivo del servidor.

La configuración externa de JWT, puertos y almacenamiento se conserva. El código existente requiere `jwt.secret` y `jwt.expiration`; esta corrección no inventa ni incorpora valores para esas propiedades.

## Archivos de esta corrección

1. `.gitignore` de la raíz: retirada la exclusión específica del archivo compartido seguro. Se conservan las exclusiones de `.env` y `.env.*`.
2. `dima-bug-backend/src/main/resources/application.properties`: mapeo de las tres variables existentes.
3. `dima-bug-backend/src/test/java/cl/casol/backend/shared/infrastructure/config/DataSourceEnvironmentTest.java`: tres pruebas de configuración sin crear DataSource ni conectar MySQL.
4. `frontend-dimabug/docs/QA_EMPAQUETADO_STAGING.md`: este informe; el informe general añade una referencia a esta corrección.

No hubo cambios de lógica de negocio, esquema, arquitectura, dependencias ni Frontend funcional. No se ejecutaron migraciones, commits, push o despliegues.

## Pruebas y empaquetado ejecutados

Comando ejecutado con JDK 21 desde `dima-bug-backend`:

```powershell
.\mvnw.cmd verify
```

Resultado final: **500 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**. Se incluyen las 497 pruebas anteriores y tres pruebas nuevas que verifican:

- Carga real del recurso, binding de las tres variables y activación del perfil `staging`.
- Precedencia de `SPRING_DATASOURCE_URL` sobre el valor del archivo.
- Ausencia de una URL alternativa cuando falta `DB_URL`: queda un placeholder no resoluble; no hay credenciales de respaldo.

Las pruebas usan valores ficticios y no crean un bean DataSource. No se comprobó el arranque completo contra MySQL ni STAGING. Angular no se volvió a probar porque no cambió su código ni su configuración.

Intentos previos: `clean verify` falló al intentar eliminar un directorio de `target`; no se atribuye esa operación a una compilación exitosa. La primera prueba negativa esperaba que el contexto de solo binding fallara, pero Spring puede conservar placeholders sin resolver; se corrigió la expectativa y la suite completa pasó. El empaquetado exitoso fue `verify`, no `clean verify`.

Verificación posterior del ZIP/JAR:

- Existe `BOOT-INF/classes/application.properties`.
- Su contenido es idéntico al archivo fuente, incluidos los tres placeholders; Maven no sustituyó los valores de entorno durante el build.
- El manifest contiene `Main-Class: org.springframework.boot.loader.launch.JarLauncher` y `Start-Class: cl.casol.backend.BackendApplication`.
- Incluye el driver `mysql-connector-j` en `BOOT-INF/lib`.

## Artefacto nuevo

Ruta exacta:

```text
C:\Users\carol\OneDrive\Desktop\cata-front\buscador-errores\dima-bug-backend\target\backend-0.0.1-SNAPSHOT.jar
```

SHA-256:

```text
eda19e6bbeb2538bf056d4b568da65d3e8fc738289bfedcf03ad0cc926cbc768
```

El archivo `.jar.original` no es el artefacto ejecutable que debe entregarse. Para una futura instalación debe usarse el JAR indicado y conservar la configuración externa del servidor. No se realizó la instalación.
