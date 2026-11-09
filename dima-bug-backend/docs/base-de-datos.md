> Referencia histórica conservada. Describe una entrega previa y puede diferir del checkout actual. [Documentación vigente](../../docs/README.md). Los enlaces a archivos ausentes se identifican expresamente; [original íntegro y hash](../../docs/auditoria/historico/README.md).

# Persistencia y base de datos

## Configuración y alcance de evidencia

El proyecto usa MySQL Connector/J, Spring Data JPA y dialecto MySQL. Configura
`spring.jpa.hibernate.ddl-auto=none` y `spring.jpa.show-sql=true`; Hibernate no
crea ni migra el esquema desde las entidades. Las variables están documentadas en
[configuración de desarrollo](configuracion-dev.md).

Existe [V20260925_01__agregar_estado_eliminado_conocimiento.sql](../src/main/resources/db/migration/V20260925_01__agregar_estado_eliminado_conocimiento.sql),
que amplía el ENUM de estado de `co_conocimiento`. No hay dependencias ni
configuración Flyway/Liquibase en el POM. Su ubicación no prueba ejecución automática.
No se encontró un DDL inicial completo ni datos de arranque reproducibles.

Los nombres siguientes son mapeos de la aplicación, no una certificación del
esquema instalado, sus claves foráneas, cascadas o índices físicos.

## Modelo por áreas

| Área | Tablas conocidas por JPA |
| --- | --- |
| Catálogos | `cl_sistema`, `cl_modulo`, `cl_hardware`, `cl_frecuencia`. |
| Identidad y organización | `u_usuario`, `u_rol`, `u_departamento`, `u_responsable`, `u_departamento_contacto`. |
| Conocimiento | `co_conocimiento`, `co_sintoma`, `co_causa`, `co_prueba`, `co_conocimiento_prueba`, `co_solucion`, `co_solucion_asignacion`, `co_material_apoyo`. |
| Procedimiento | `pr_procedimiento`, `pr_paso`. |
| Ejecución y seguimiento | `se_ejecucion`, `se_ejecucion_paso`, `se_resultado`. |

Las entidades mezclan relaciones JPA e identificadores escalares. Por ejemplo,
Usuario referencia Rol; Conocimiento referencia catálogos y usuarios;
`co_conocimiento_prueba` usa clave compuesta. Las reglas de pertenencia también
se comprueban en Application; no deben inferirse restricciones SQL solo a partir
de esas validaciones.

## Búsqueda y transacciones

[BusquedaConocimientoJdbcAdapter](../src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java)
combina JPA (`flush`) y JDBC. Lee datos relacionados, escribe
`co_conocimiento_busqueda` con `INSERT ... ON DUPLICATE KEY UPDATE` y consulta
`MATCH ... AGAINST`. La búsqueda filtra conocimientos PUBLICADO. El DDL e índice
FULLTEXT necesarios no están incluidos en el repositorio y requieren validación.
Los materiales aportan sus nombres directos de conocimiento, no bytes de archivos
ni materiales de pasos, al documento de búsqueda.

Los servicios utilizan `@Transactional` y, para archivos, `TransactionTemplate`.
Eliminar un conocimiento cambia su estado a ELIMINADO y elimina su documento de
búsqueda. Eliminar un material borra su registro y reindexa si es de conocimiento.
Filesystem no participa en la transacción MySQL; su consistencia está explicada
en [materiales](materiales-archivo.md#eliminación-y-consistencia).

`co_material_apoyo` mapea ID, conocimiento o paso, nombre, tipo y referencia URL.
JPA declara nombre de hasta 150, URL de hasta 500 y ambos no nulos; el tipo se
mapea como enum String. La aplicación crea un único destino por material, pero
no se puede confirmar una restricción XOR en MySQL. Los bytes se almacenan fuera
de BD; no hay BLOB de materiales en este mapeo.

## Requiere validación del equipo

- Versión MySQL soportada, DDL completo, índices y restricciones del esquema real.
- Procedimiento de provisión, migraciones y creación del primer usuario/roles.
- Integración contra MySQL real. Las pruebas actuales usan mocks para persistencia;
  no garantizan compatibilidad de esquema ni comportamiento transaccional del servidor.
