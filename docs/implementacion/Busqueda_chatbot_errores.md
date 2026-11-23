# Búsqueda de errores del chatbot — 10 de noviembre de 2026

## Alcance y causa

Se corrige únicamente el mecanismo de búsqueda pública del chatbot. No se modifican MySQL/staging, registros originales, entidades, migraciones, contratos HTTP, autenticación productiva ni permisos. Se preservan los cambios locales de OCR, conversaciones, materiales y enlaces NFC/QR.

La búsqueda anterior dividía por espacios y exigía todos los fragmentos mediante `AND LIKE`. Consultaba título de conocimiento, título y descripción de solución pública. Un cambio de fecha o sufijo de archivo descartaba el caso. Además, contaba soluciones para puntuar en lugar de medir similitud. El módulo Conocimiento utiliza otro mecanismo (FULLTEXT y coincidencias locales), que no cambia en esta tarea.

## Normalización y ranking

`ConsultaError` es una clase de dominio sin Spring, JPA ni dependencias nuevas. Se reutiliza en la selección de conocimientos y en el orden de sus soluciones.

- Normaliza mayúsculas, acentos, espacios, separadores Windows/Unix y guiones tipográficos.
- Reconoce fechas completas año-mes-día con separadores espacio, `_`, `/`, `.`, `-`, con mes y día de dos dígitos. No pretende interpretar cualquier formato de fecha.
- Ignora sufijos numéricos `_NNN` antes de una extensión de archivo para comparar la firma. Conserva excepciones de prefijos técnicos como `ORA_00942.log`.
- No elimina números arbitrariamente: códigos hexadecimales, HTTP, ORA, SQLSTATE, puertos y otros tokens con cifras permanecen significativos. Un código distinto no se considera equivalente.
- Omite palabras de enlace para puntuar, conservando negaciones. Para consultas con ruta exige además cobertura del mensaje anterior a ella: la ruta compartida no basta para equiparar leer con escribir.
- No corrige ni reescribe el texto original en la BD o en la respuesta.

Los ejemplos autorizados:

```text
Error al escribir el archivo /dim/tbk/2025 01 20/voucher-venta-original_49.txt
Error al escribir el archivo /dim/tbk/2026_06_10/voucher-venta-original_280.txt
```

comparten la firma:

```text
error escribir archivo dim tbk voucher venta original txt
```

La puntuación pondera título del conocimiento (6), título de solución (4) y descripción pública de solución (1), por término coincidente. Los términos genéricos pesan 1, los códigos 6 y los demás términos útiles 3. Se exige al menos 60 % de cobertura ponderada; los códigos técnicos deben coincidir completos. Las coincidencias textuales exactas y las firmas completas reciben prioridad adicional; las parciales son de menor prioridad. Términos cortos exigen límites de palabra para evitar coincidencias como `IP` dentro de `equipo`.

La búsqueda sigue siendo léxica, no un motor semántico ni generativo. No infiere que dos incidentes sean técnicamente idénticos ni crea instrucciones nuevas.

## Consulta y límites

`BusquedaConocimientoJdbcAdapter.buscarAutoservicio()` usa consultas parametrizadas sobre `co_conocimiento` y `co_solucion`. No utiliza el índice interno, ni descripción/comentarios del conocimiento, síntomas, causas, pruebas, asignaciones o materiales como fuente de coincidencias.

La preselección admite términos alternativos con `OR`, ordena por una aproximación de relevancia y limita a 250 filas públicas candidatas. Java calcula la puntuación normalizada, conserva el máximo por conocimiento (no la cantidad de soluciones), ordena de forma estable por puntuación/ID y devuelve hasta 50 conocimientos. `ConsultarChatbotService` sigue mostrando hasta cinco soluciones autorizadas, priorizando las coincidencias dentro de cada conocimiento y manteniendo sus alternativas públicas.

El `GET /api/publico/soluciones?q=...` del buscador del portal conserva su comportamiento anterior; los detalles públicos por ID y NFC/QR no dependen de esta búsqueda y no cambian.

## Evidencia de seguridad

Los filtros se mantienen en SQL antes de puntuar:

```sql
c.conocimiento_estado = 'PUBLICADO'
so.solucion_estado = 1
so.solucion_autoservicio = 1
```

La lectura posterior de soluciones sigue utilizando `buscarActivasAutoservicioPorConocimiento`. El controlador público conserva la respuesta existente y reemplaza el título interno del conocimiento por `Autoservicio`. Los materiales conservan su autorización independiente.

- Los parámetros `LIKE` se escapan con `ESCAPE '!'`; el texto no se interpola como SQL.
- Una consulta sin términos útiles no lista el catálogo.
- `LimiteAutoservicioFilter` sigue activo: 10 consultas de chatbot/minuto por cliente, límite de bytes y sin confiar en `X-Forwarded-For` del solicitante.
- Las pruebas comprueban que un hermano privado, una solución inactiva, un borrador o un término presente únicamente en campos internos no producen resultados públicos.
- Se verifica que consultar no modifica título, descripción ni comentarios originales.

## Archivos

Código productivo:

- `dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/domain/ConsultaError.java` (nuevo).
- `dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java`.
- `dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/application/service/ConsultarChatbotService.java`.

Pruebas de la corrección:

- `dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/domain/ConsultaErrorTest.java` (nuevo).
- `dima-bug-backend/src/test/java/cl/casol/backend/ChatbotBusquedaErroresHttpTest.java` (nuevo).
- `dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaAutoservicioTest.java`.
- `dima-bug-backend/src/test/java/cl/casol/backend/conocimiento/application/service/ConsultarChatbotServiceTest.java`.

La suite completa detectó fixtures de autenticación desactualizados respecto de la política de roles ya incorporada en HEAD. Solo se actualizan pruebas: `LoginCodigoTestSupport.java` reconoce `modo=PASSWORD` para staff y usa las credenciales sintéticas ya sembradas; conserva la rama de código para otros roles. Sus cinco consumidores (`AutomatizacionHttpPersistenciaTest`, `CorreoSinSucursalHttpTest`, `EdicionProcedimientoAdminHttpTest`, `GmailOAuthHttpTest`, `HardwareProcedimientoHttpTest`) pasan la clave de su fixture existente. `Hu01SecurityIntegrationTest` espera 401 para contraseña incorrecta, coherente con su nombre y el endpoint actual, en lugar del antiguo 404. No cambia ningún servicio de autenticación, contraseña real ni rol.

Este documento y `docs/07_Pruebas/evidencias/busqueda_chatbot/resultado.json` contienen el informe y evidencia numérica local. Los logs y artefactos de Maven quedan en `target` (ignorados).

## Pruebas

Ejecutar desde `C:/Proyectos/DimaBug-MVP/dima-bug-backend`, sin variables privadas de otros entornos:

```powershell
.\mvnw.cmd '-Dtest=ConsultaErrorTest,ChatbotBusquedaErroresHttpTest,BusquedaAutoservicioTest,ConsultarChatbotServiceTest,AutoservicioPublicoHttpTest,ChatbotSecurityIntegrationTest,LimiteAutoservicioFilterTest' test
.\mvnw.cmd package
```

Los escenarios HTTP usan H2 en memoria con nombre exclusivo y validación de que no se conectan a otra BD. Cada caso funcional tiene un cliente sintético independiente; el filtro de frecuencia no se desactiva. Su prueba específica verifica el rechazo 429 en la consulta número 11 y el límite de bytes.

| Escenario | Evidencia |
| --- | --- |
| Error idéntico | Solución pública recuperada; prioridad sobre similares |
| Fecha variable | Espacios, guiones y `_` en la fecha del ejemplo |
| Archivo variable | Sufijo distinto y nombre `voucher-copia_123.txt` |
| Rutas | Windows y Unix conservan la firma |
| Palabras clave/parciales | `escribir voucher venta`, `configur impresora`, acentos y mayúsculas |
| Error inexistente | Sin resultados; también se distingue leer de escribir sobre la misma ruta |
| Códigos | `0x0000011b`, HTTP 404, ORA, SQLSTATE y puertos; códigos diferentes no coinciden |
| Múltiples soluciones | Coincidencia directa primero, máximo cinco, alternativas públicas y ranking sin premio por cantidad |
| Seguridad | Borrador, inactiva, privada, hermana privada, campos internos e índice interno excluidos |
| Inmutabilidad | Los textos y registros de prueba quedan intactos tras consultar |

Validación final: **1173 pruebas backend aprobadas**, 0 fallos, 0 errores y 0 omitidas en 112 suites; `mvnw.cmd -q package` terminó correctamente y generó el JAR. **502 pruebas Angular aprobadas**, incluyendo OCR, materiales y navegación pública. `git diff --check` correcto.

Evidencia numérica del normalizador con los textos proporcionados: [resultado local](../07_Pruebas/evidencias/busqueda_chatbot/resultado.json). La prueba HTTP con JDBC real/H2 valida adicionalmente filtros, relaciones y contenido recuperado; el JSON no pretende acreditar una consulta a staging.

## Riesgos y pendientes

- Validar el caso real y sus flags de publicación en staging tras autorización; no se consultó ni modificó esa BD durante esta implementación.
- Se validó SQL con H2 en modo MySQL; falta medir rendimiento y plan de ejecución con MySQL 8 y volumen real. Las búsquedas parciales y normalización de SQL pueden requerir optimización a mayor escala.
- El límite de 250 candidatos puede recortar catálogos muy grandes o con muchas alternativas; no se garantiza exhaustividad ilimitada. Mantener revisión humana de sugerencias y evaluar umbral con un conjunto de casos autorizados.
- Solo se generalizan los patrones documentados. Fechas de otros formatos, identificadores no numéricos, sinónimos y errores de OCR adicionales requieren nuevos casos; no se eliminan códigos para aumentar artificialmente coincidencias.
- Se preserva la agrupación de soluciones por conocimiento y el límite de cinco; no se amplía automáticamente la visibilidad por tener sesión administrativa en el chatbot público.

## Mejora futura: descripciones y síntomas

NO IMPLEMENTADA: incorporar `conocimiento_descripcion`, `co_sintoma` o campos del índice interno a búsqueda pública.

Antes de hacerlo se necesita autorización explícita que defina campos publicables, revisión de datos personales/rutas/credenciales, responsables y mecanismo de retirada. Buscar un campo privado y devolver solo la solución también puede revelar su existencia mediante consultas; no basta con ocultarlo en JSON. Si el volumen lo exige, evaluar una proyección/índice público separado compuesto exclusivamente por campos aprobados. No copiar sin filtrado `co_conocimiento_busqueda`.

No hay migraciones nuevas, cambios en registros existentes, commits, push ni despliegues.
