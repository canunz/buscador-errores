> Referencia histórica conservada. Describe una entrega previa y puede diferir del checkout actual. [Documentación vigente](../../../docs/README.md). Los enlaces a archivos ausentes se identifican expresamente; [original íntegro y hash](../../../docs/auditoria/historico/README.md).

# ADR-002: archivos en filesystem y referencias en MySQL

Estado: registro retrospectivo del comportamiento implementado. Fecha,
participantes y alternativas históricas: **Requiere validación del equipo**.

## Evidencia y decisión observada

[AlmacenamientoArchivoPort](../../src/main/java/cl/casol/backend/shared/application/port/out/AlmacenamientoArchivoPort.java)
define guardar, leer, eliminar y compensar. Su adapter filesystem usa un directorio
configurable y claves UUID-v4. MaterialApoyo persiste una cadena
`file:materiales/{UUID}`; no bytes BLOB. Los endpoints de lectura pasan por JWT y
verificación de pertenencia, sin servir el directorio públicamente.

[ArchivoMaterialService](../../src/main/java/cl/casol/backend/conocimiento/application/service/ArchivoMaterialService.java)
compensa subidas cuando la transacción no termina confirmada. Para eliminación,
confirma primero el borrado BD en una transacción nueva y después borra el archivo.
Un fallo físico se propaga como error de almacenamiento, sin éxito silencioso.

## Motivación y límites de evidencia

La documentación previa de eliminación explica que se prioriza evitar un registro
que apunte a un archivo borrado antes del commit; se conserva esa explicación en
[materiales](../materiales-archivo.md#eliminación-y-consistencia).
La motivación histórica de elegir filesystem frente a BLOB o cloud y las
alternativas consideradas: **Requiere validación del equipo**.

## Consecuencias observables

- Los enlaces existentes y los archivos locales comparten el modelo persistido.
- Domain/Application no reciben MultipartFile ni acceden directamente a java.nio.file.
- El directorio requiere persistencia y permisos operativos fuera de MySQL.
- BD y filesystem no comparten atomicidad; puede fallar la compensación o quedar
  un archivo huérfano después del commit. No hay reconciliación automática.
- La descarga utiliza bytes en memoria con límite de 10 MiB; no es un flujo de
  streaming ilimitado. El detalle del contrato reside en [materiales](../materiales-archivo.md).
