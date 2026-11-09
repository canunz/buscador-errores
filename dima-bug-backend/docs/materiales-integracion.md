> Referencia histórica conservada. Describe una entrega previa y puede diferir del checkout actual. [Documentación vigente](../../docs/README.md). Los enlaces a archivos ausentes se identifican expresamente; [original íntegro y hash](../../docs/auditoria/historico/README.md).

# Integración real de materiales de apoyo

## Inspección previa del módulo

Se revisó la implementación antes de crear los tests. La funcionalidad existente
se describe en [materiales de archivo](materiales-archivo.md); esta página registra
alcance de integración y riesgos, no requisitos nuevos.

- MantenerMaterialApoyoService y MantenerMaterialPasoService: listar, crear y
  modificar metadatos/enlaces. PUT no sustituye bytes de un archivo administrado.
- MaterialConocimientoArchivoService y MaterialPasoArchivoService: subir, descargar
  y eliminar materiales, verificando padre/destino. No se presupone otro CRUD.
- ArchivoMaterialService: valida y coordina TransactionTemplate y filesystem.
- Puertos MaterialApoyoRepository y AlmacenamientoArchivoPort; adaptadores reales
  MaterialApoyoRepositoryAdapter y AlmacenamientoArchivoFilesystemAdapter.
- MaterialApoyoJpaRepository y MaterialApoyoEntity persisten co_material_apoyo;
  ConocimientoRepository/PasoRepository/ProcedimientoRepository comprueban pertenencia.
- Materiales de conocimiento reindexan por IndexarConocimientoService y
  BusquedaConocimientoJdbcAdapter; los de paso no se agregan a esa proyección.
- SecurityConfig permite ADMINISTRADOR/TECNICO en ambos grupos de rutas. La regla
  DELETE de conocimiento restringida a ADMINISTRADOR no equivale a DELETE de material.
- PDF e IMAGEN admiten bytes locales; imágenes reconocidas por firma PNG/JPEG/WebP.
  ArchivoHttp y ArchivoMaterialService controlan vacío, 10 MiB, MIME/firma/tipo;
  nombre no blanco y máximo 150. Multipart configura 10 MB por archivo/11 MB request.
- Filesystem genera UUID v4, valida referencias file:materiales/UUID, usa CREATE_NEW
  y NOFOLLOW_LINKS en lectura y acota el volumen leído. No conserva el filename original.
- Subida: escribe bytes y registra compensación afterCompletion si BD no confirma.
  Fallo de compensación se registra en log; no existe transacción distribuida.
- Eliminación: REQUIRES_NEW confirma borrado de fila/reindexación antes de borrar
  bytes. Fallo posterior devuelve error de almacenamiento y requiere limpieza operativa.

Fuentes: src/main/java/cl/casol/backend/conocimiento/application/service/ArchivoMaterialService.java,
MaterialConocimientoArchivoService.java, procedimiento/application/service/MaterialPasoArchivoService.java,
shared/infrastructure/storage/AlmacenamientoArchivoFilesystemAdapter.java,
shared/application/archivo/FormatoArchivo.java y shared/infrastructure/security/SecurityConfig.java.

## Riesgos prioritarios

| Prioridad | Riesgo | Evidencia/alcance |
| --- | --- | --- |
| P0 potencial | Acceso/escritura fuera de raíz por referencias inseguras | Probar rechazo de traversal y centinela temporal intacto; no defecto confirmado |
| P1 | Bytes huérfanos si falla BD o compensación | Probar FK real fallida y compensación; fallo de compensación pendiente |
| P1 | Metadatos sin bytes si almacenamiento falla/desaparece | Probar escritura fallida y archivo ausente; no existe reconciliación automática |
| P1 | Borrado de BD confirmado con limpieza física pendiente | Comportamiento diseñado, probar error 500 y estado posterior |
| P1 | Permisos/parent incorrecto al descargar | JWT reales y rechazo 401/404; ambos roles actuales permitidos |

## Infraestructura y escenarios

MaterialesArchivoIT reutiliza MySqlIntegrationSupport y @TempDir. Arranca HTTP real,
servicios, JPA, JDBC, JWT, BCrypt y filesystem funcionales. No mocks. El contexto
escanea componentes funcionales excluyendo configuraciones ajenas de tests e importa
SecurityConfig. Su DataSource se registra antes de Boot, con validación de destino.

El directorio es un descendiente de @TempDir fijado con prioridad sobre variables
externas. No se utiliza ./uploads ni almacenamiento del proyecto. Limpieza restringida
a descendientes del directorio propio. Cada escenario limpia filas en orden de FK,
recrea usuarios/padres sintéticos y limpia bytes. JUnit elimina el directorio al terminar.

Se habilita OSIV en este contexto HTTP como en la configuración funcional por defecto:
los servicios de archivos verifican el padre fuera de TransactionTemplate y sus mappers
pueden recorrer asociaciones LAZY. No se cambia application.properties. No se certifica
que esos servicios funcionen como llamadas directas sin sesión/transacción envolvente.

Diez escenarios nuevos:

1. Subida PDF por HTTP/service real, bytes y metadatos MySQL, reindexación, descarga
   exacta y eliminación física/BD con retirada del nombre de la proyección.
2. Subida, descarga y eliminación de archivo de paso de procedimiento.
3. Fallo FK MySQL 1452: ArchivoMaterialService real escribe bytes; callback usa el
   repositorio JPA real con padre inexistente. Se exige archivo presente antes del
   fallo y ninguna fila/archivo después de compensar. No simula el fallo con throw.
4. Escritura fallida: ocupar la raíz storage temporal con un archivo regular;
   upload devuelve 500 sin persistir metadatos ni alterar ese archivo centinela.
5. Borrado físico fallido: retener bytes temporalmente y ocupar su destino con un
   directorio no vacío. DELETE devuelve 500 después del commit de BD; los bytes
   retenidos evidencian limpieza pendiente. Es un fallo de I/O real, no un mock.
6. Consulta/descarga con JWT de ADMINISTRADOR y TECNICO; ausencia/invalidación 401,
   padre incorrecto y material inexistente 404.
7. Nombre vacío/excesivo, VIDEO local, MIME incompatible, firma inválida y vacío:
   400 sin filas ni bytes.
8. Límite exacto 10 MiB aceptado y descargado idéntico; exceso de un byte 413.
   Nombre lógico con separadores se sanea en Content-Disposition; ruta física UUID.
9. PNG por firma admitida; desaparición física posterior devuelve 404 conservando
   metadatos, como contempla el código.
10. Referencias de traversal/absolutas/componentes extra rechazadas por el adaptador
    real en lectura/borrado; archivo centinela fuera de storage permanece intacto.

Los bytes fixture son sintéticos reconocibles por firma; no demuestran integridad
semántica completa del formato PDF/PNG ni protección antivirus.

## Ejecución y pendientes

```powershell
.\mvnw.cmd -B -Pintegration-mysql verify
```

El perfil ejecuta esta clase además de las tres clases anteriores; las unitarias
habituales siguen separadas. Reportes: target/failsafe-reports.

Pendientes: fallos de compensación, crashes entre commit y cleanup, PUT/DELETE
concurrentes, symlinks/junctions en ancestros, JPEG/WebP, límite total request,
descargas corruptas, enlaces externos y limpieza/reconciliación operativa.
No se modifica lógica, esquema, application.properties o infraestructura compartida.
Solo se añade inclusión Failsafe en POM, test y documentación. Si aparece un defecto,
se conserva el FAIL y se documenta antes de pedir autorización para corregirlo.

## Resultados reales

2026-10-08: `mvnw.cmd -B -Pintegration-mysql verify`, BUILD SUCCESS.

| Clase | PASS | FAIL | ERROR | SKIPPED | Tiempo |
| --- | --- | --- | --- | --- | --- |
| MaterialesArchivoIT | 10 | 0 | 0 | 0 | 29,37 s |
| AutenticacionHttpIT | 7 | 0 | 0 | 0 | 47,81 s |
| ConocimientoJpaIT | 5 | 0 | 0 | 0 | 24,48 s |
| MySqlReferentialIntegrityIT | 1 | 0 | 0 | 0 | 15,70 s |
| Total | 23 | 0 | 0 | 0 | Maven completo: 2:15 min |

Los errores MySQL 1452 y DirectoryNotEmptyException se provocaron expresamente;
sus aserciones y limpieza pasaron. No se detectaron defectos funcionales nuevos
en los escenarios ejecutados ni se corrigió código para hacer pasar pruebas.
Se confirmó eliminación de @TempDir tras ejecutar la clase. Evidencia real:
target/failsafe-reports/cl.casol.backend.integration.MaterialesArchivoIT.txt y XML.

Archivos creados: MaterialesArchivoIT.java y este documento.
Archivos actualizados: pom.xml (inclusión de la clase en Failsafe) y
docs/integracion-mysql.md (alcance/enlace). Cambios previos de seguridad u otras
tareas del working tree permanecen intactos; no pertenecen a esta ampliación.

Se ejecuta adicionalmente `mvnw.cmd -B test` por el cambio de configuración Maven
compartida: BUILD SUCCESS, 472 PASS, 0 FAIL, 0 ERROR y 0 SKIPPED; 41,145 s.
DEV no se utilizó. Se verificó por los IDs de los reportes que los cuatro MySQL
temporales ya no existen. El directorio temporal de archivos también fue eliminado.
