> **Antecedente de auditoría fechado.** Sus conteos/hallazgos describen la fase original. Para estado vigente y cierres específicos consultar [documentación actual](../README.md).

# Auditoría documental y técnica de DimaBug

Fecha: 6 de noviembre de 2026, America/Santiago. Proyecto APT / Capstone, Duoc UC.

**Alcance autorizado: fases 1, 2, 3 y 4.** Este informe no reorganiza ni reemplaza la documentación anterior. No constituye una aprobación académica oficial, una certificación de seguridad ni una autorización de despliegue.

## 1. Resumen ejecutivo

DimaBug contiene una aplicación Angular y un backend Spring Boot organizados como un monolito modular con puertos y adaptadores. La separación no se deduce únicamente de las carpetas: existen contratos de salida, implementaciones de infraestructura, modelos independientes y pruebas ArchUnit específicas de Tickets. Los demás módulos presentan reutilización y acoplamientos que impiden afirmar aislamiento total.

La documentación existente es extensa, pero mezcla antecedentes de distintas etapas y de árboles de trabajo que no corresponden completamente a este checkout. En particular, describe snapshots de ejecuciones, integración MySQL con Testcontainers, migraciones adicionales y CI cuyos archivos no están disponibles aquí. Esto demuestra falta de correspondencia documental local, no demuestra que aquellos ensayos históricos nunca hayan ocurrido.

Resultados reproducidos en esta auditoría:

- Backend: **920 pruebas, 0 fallos, 0 errores y 0 omitidas**, en 92 archivos XML de Surefire. `mvnw.cmd -o test`: BUILD SUCCESS, finalizado el 06-11-2026 a las 23:11:44 -03:00. Incluye pruebas unitarias, slices MVC con mocks, ArchUnit y dos clases de persistencia H2. No es una suite MySQL.
- Angular: **284 pruebas aprobadas** con ChromeHeadless, salida 0. La comprobación TypeScript de aplicación y pruebas también terminó con salida 0.
- Dependencias frontend: npm informó **54 entradas afectadas: 4 críticas, 20 altas, 28 moderadas y 2 bajas**. Las cuatro entradas críticas corresponden a herramientas transitivas de desarrollo. La clasificación del catálogo no demuestra por sí sola explotación de la aplicación desplegada.
- Datos: **31 tablas requeridas identificadas en el código**: 28 entidades JPA, dos tablas de unión y una tabla usada por JDBC. No se consultó una instancia MySQL ni se certificaron restricciones físicas.
- Documentación inicial: **43 archivos Markdown/TXT**, cinco SQL, un Draw.io, una PPTX y material PNG. No se localizaron duplicados exactos entre los 43 archivos de texto.

**Dictamen:** hay evidencia suficiente para describir el funcionamiento local y preparar la corrección documental. Todavía no hay evidencia suficiente para declarar completa y coherente la entrega académica ni para emitir un GO de despliegue. Priorizar la conciliación documental, la evaluación de dependencias y la reproducción controlada de base de datos. Las recomendaciones de corrección no se ejecutaron.

## 2. Método, procedencia y límites

### 2.1. Árbol auditado

- Carpeta: `C:/Proyectos/DimaBug-MVP`.
- Rama: `codex/integracion-mvp`.
- HEAD al comenzar: `e0d63d961b87c33d77937821c53802e6387a783e`.
- Cambios anteriores a esta auditoría: `application-staging.properties` modificado; `dima-bug-backend/docs/` y `entregables/` sin seguimiento. No se atribuyen esos cambios a esta auditoría.
- Se buscaron instrucciones AGENTS en el repositorio y sus directorios ascendentes revisados, sin encontrar archivos aplicables.
- El inventario examinó archivos ocultos e ignorados relevantes. `node_modules`, `.angular`, `target`, `.git`, `dist` y directorios privados de construcción se trataron como dependencias, caché o artefactos, no como documentación autoral. Se inspeccionaron las versiones instaladas y la existencia de reportes en esos directorios cuando correspondía.

Se revisaron los contenidos documentales, sus secciones de alcance, resultados y pendientes, sus referencias locales, los contratos de código y las diferencias de implementación. Se compararon hashes SHA-256 para detectar copias exactas de documentos. Las similitudes temáticas se distinguen de duplicados.

### 2.2. Significado de verificación

**Implementada y verificada** significa código encontrado y escenario cubierto por una comprobación local reproducida en esta auditoría. No implica validación integral en navegador conectado al backend y MySQL. **Implementada, pero no verificada** significa que existe el recorrido de código, pero falta ejecución de ese recorrido completo con sus dependencias reales.

Los estados documentales Vigente, Incompleto, Desactualizado, Duplicado y No encontrado se refieren al uso de cada documento dentro de este checkout. Un diagnóstico histórico puede ser Vigente como antecedente fechado aunque su fotografía ya no sea la implementación actual. No debe utilizarse como manual actualizado.

### 2.3. Límites de acceso y seguridad

No se conectó a DEV, STAGING ni PRODUCCIÓN, no se inició la aplicación contra bases reales y no se ejecutaron migraciones. No se inspeccionaron servidores, cuentas remotas, respaldos privados ni repositorios externos del equipo. No se instalaron paquetes, no se ejecutó `audit fix`, no se modificaron variables o credenciales ni se hicieron commits o push.

Se consultaron avisos públicos de seguridad y el registro npm. El primer intento npm dentro del sandbox falló por DNS; el intento de lectura fuera del sandbox funcionó. Se conservaron únicamente metadatos públicos de paquetes y avisos, sin credenciales. No se copiaron logs completos de Spring/Surefire porque pueden contener valores sintéticos de autenticación y datos de pruebas.

La evaluación académica es orientativa: la rúbrica, requisitos aprobados, actas de aceptación, responsables y acuerdos funcionales oficiales no fueron proporcionados. Esos elementos quedan **[PENDIENTE DE VERIFICACIÓN]**.

## 3. Inventario documental previo a las modificaciones

El inventario se elaboró primero, sin editar archivos existentes. Las únicas escrituras posteriores son este informe, evidencias nuevas de auditoría y archivos temporales generados por las herramientas de prueba.

### 3.1. Documentos de texto existentes

| Documento | Ubicación | Contenido | Estado | Acción recomendada |
|---|---|---|---|---|
| README.md | [Ubicación](../../README.md) | buscador_errores | Incompleto | Completar propósito, alcance y arranque reutilizando las guías existentes. |
| aceptacion-riesgo-archivos-skill4.md | [Ubicación](../../dima-bug-backend/docs/aceptacion-riesgo-archivos-skill4.md) | Propuesta de aceptación temporal del riesgo de recuperación de archivos | Incompleto | Conservar propuesta sin aprobación y validar correspondencia de evidencia. |
| ADR-001-arquitectura-backend.md | [Ubicación](../../dima-bug-backend/docs/adr/ADR-001-arquitectura-backend.md) | ADR-001: monolito modular con capas hexagonales | Desactualizado | Conservar decisión observada y corregir afirmación global sobre controllers y falta de pruebas. |
| ADR-002-almacenamiento-archivos.md | [Ubicación](../../dima-bug-backend/docs/adr/ADR-002-almacenamiento-archivos.md) | ADR-002: archivos en filesystem y referencias en MySQL | Vigente | Reutilizar patrón de materiales y distinguir almacenamiento de Tickets. |
| arquitectura.md | [Ubicación](../../dima-bug-backend/docs/arquitectura.md) | Arquitectura | Desactualizado | Incorporar Tickets, API públicas, puertos de entrada y ArchUnit acotado. |
| auditoria-cobertura-riesgos-skill4.md | [Ubicación](../../dima-bug-backend/docs/auditoria-cobertura-riesgos-skill4.md) | Auditoría de cobertura y riesgos residuales — Skill 4 | Desactualizado | Conciliar snapshot y suites MySQL que faltan en este árbol. |
| autorizacion-http-integracion.md | [Ubicación](../../dima-bug-backend/docs/autorizacion-http-integracion.md) | Autorización HTTP real del MVP | Incompleto | Falta clase IT citada; rehacer matriz actual sin borrar resultados históricos. |
| base-de-datos.md | [Ubicación](../../dima-bug-backend/docs/base-de-datos.md) | Persistencia y base de datos | Desactualizado | Añadir Tickets, organización de control, uniones, JDBC y cinco SQL actuales. |
| ci-pruebas-backend.md | [Ubicación](../../dima-bug-backend/docs/ci-pruebas-backend.md) | CI de pruebas del backend | Desactualizado | Workflow/perfil/IT citados no localizados; recuperar origen correcto. |
| concurrencia-integracion.md | [Ubicación](../../dima-bug-backend/docs/concurrencia-integracion.md) | Concurrencia selectiva con MySQL real | Desactualizado | Contrastar corrección de cierre/checklist no encontrada y mantener ensayos como historia. |
| configuracion-dev.md | [Ubicación](../../dima-bug-backend/docs/configuracion-dev.md) | Configuración de desarrollo | Desactualizado | Resolver resource/README ausentes y diferencias de perfil, puerto y defaults no acreditados. |
| decision-historial-procedimientos.md | [Ubicación](../../dima-bug-backend/docs/decision-historial-procedimientos.md) | RN-PROC-01 — snapshot histórico de pasos | Desactualizado | Snapshot descrito y migración ausentes en código actual. |
| decision-riesgos-residuales-skill4.md | [Ubicación](../../dima-bug-backend/docs/decision-riesgos-residuales-skill4.md) | Decisión sobre riesgos residuales de Skill 4 | Incompleto | Comparar decisiones con política actual de inmutabilidad y evidencia externa pendiente. |
| handoff-skill4-a-skill5.md | [Ubicación](../../dima-bug-backend/docs/handoff-skill4-a-skill5.md) | Transferencia de Skill 4 — Pruebas a Skill 5 — Despliegue | Desactualizado | Actualizar configuración, perfil/CI/snapshots y alcance actual antes de usar como transferencia. |
| integracion-mysql.md | [Ubicación](../../dima-bug-backend/docs/integracion-mysql.md) | Primera integración JDBC con MySQL | Desactualizado | Faltan clases, soporte, esquema y perfil citados en esta copia. |
| materiales-archivo.md | [Ubicación](../../dima-bug-backend/docs/materiales-archivo.md) | Material de Apoyo: enlaces y archivos | Desactualizado | Ajustar permisos SOPORTE/USUARIO y enlaces; conservar límites de recuperación. |
| materiales-integracion.md | [Ubicación](../../dima-bug-backend/docs/materiales-integracion.md) | Integración real de materiales de apoyo | Incompleto | Falta MaterialesArchivoIT y sus reportes; distinguir evidencia histórica de actual. |
| migracion-fk-usuarios.md | [Ubicación](../../dima-bug-backend/docs/migracion-fk-usuarios.md) | Migración controlada de FK hacia usuarios | Incompleto | Propuesta SQL no localizable; no ejecutar un archivo reconstruido sin revisión. |
| procedimientos-ejecuciones-resultados-integracion.md | [Ubicación](../../dima-bug-backend/docs/procedimientos-ejecuciones-resultados-integracion.md) | Integración de Procedimientos, Ejecuciones y Resultados | Incompleto | Falta clase IT; contraste con historia referencial y política de inmutabilidad. |
| seguridad-integracion.md | [Ubicación](../../dima-bug-backend/docs/seguridad-integracion.md) | Integración HTTP de autenticación y autorización | Desactualizado | No coincide con orden de login ni handlers actuales; /error tiene regla distinta. |
| seguridad.md | [Ubicación](../../dima-bug-backend/docs/seguridad.md) | Seguridad implementada | Desactualizado | Actualizar cuatro roles, caché y alcance real de logging. |
| LEEME.md | [Ubicación](../../entregables/modelo-relacional/LEEME.md) | Modelo relacional DimaBug | Desactualizado | Incluir tabla JDBC y separar relaciones JPA de FK SQL. |
| README.md | [Ubicación](../../frontend-dimabug/README.md) | Buscador de Errores (DimaBug) — Frontend Angular | Desactualizado | Actualizar puerto 9090, rutas completas y recuperación pendiente. |
| QA009_IMPLEMENTACION.md | [Ubicación](../../frontend-dimabug/docs/QA009_IMPLEMENTACION.md) | QA-009 — administración persistente de soluciones | Vigente | Conservar antecedente de Soluciones y contrastar despliegue físico pendiente. |
| QA010_SISTEMAS_HARDWARE.md | [Ubicación](../../frontend-dimabug/docs/QA010_SISTEMAS_HARDWARE.md) | QA-010 — Sistemas y asociaciones Hardware–Sistema | Vigente | Conservar antecedente de asociaciones y riesgos concurrentes. |
| QA011_DIAGNOSTICO_MODULOS.md | [Ubicación](../../frontend-dimabug/docs/QA011_DIAGNOSTICO_MODULOS.md) | QA-011 — Diagnóstico funcional y técnico de Módulos | Vigente | Antecedente histórico, remitir a implementación posterior. |
| QA011_IMPLEMENTACION_MODULOS.md | [Ubicación](../../frontend-dimabug/docs/QA011_IMPLEMENTACION_MODULOS.md) | QA-011 — Implementación de mejoras de Módulos | Vigente | Reutilizar explicación funcional, reemplazar conteos actuales solo en una síntesis nueva. |
| QA012_DIAGNOSTICO_DEPARTAMENTOS.md | [Ubicación](../../frontend-dimabug/docs/QA012_DIAGNOSTICO_DEPARTAMENTOS.md) | QA-012 — Diagnóstico de Departamentos, Contactos y Responsables | Vigente | Antecedente previo al CRUD, no usar como descripción actual. |
| QA012_DISENO_PERSISTENCIA.md | [Ubicación](../../frontend-dimabug/docs/QA012_DISENO_PERSISTENCIA.md) | QA-012 — Diseño de persistencia de Departamentos y Contactos | Vigente | Conservar como propuesta histórica y enlazar decisiones/implementaciones posteriores. |
| QA012_IMPLEMENTACION_DEPARTAMENTOS.md | [Ubicación](../../frontend-dimabug/docs/QA012_IMPLEMENTACION_DEPARTAMENTOS.md) | QA-012 — Implementación de Departamentos y Contactos | Desactualizado | Conservar alcance de su entrega y registrar que ahora existe CRUD de Responsables. |
| QA013_DIAGNOSTICO_PRUEBAS_PASOS.md | [Ubicación](../../frontend-dimabug/docs/QA013_DIAGNOSTICO_PRUEBAS_PASOS.md) | QA-013 — Diagnóstico funcional y técnico de Pruebas y Pasos | Vigente | Antecedente previo a administración de Pruebas, enlazar implementación. |
| QA013_IMPLEMENTACION_PRUEBAS_PASOS.md | [Ubicación](../../frontend-dimabug/docs/QA013_IMPLEMENTACION_PRUEBAS_PASOS.md) | QA-013 — Implementación local de Pruebas y Pasos | Vigente | Reutilizar contratos actuales y mantener pendientes de MySQL/UAT explícitos. |
| QA014_DIAGNOSTICO_NUEVO_PROCEDIMIENTO.md | [Ubicación](../../frontend-dimabug/docs/QA014_DIAGNOSTICO_NUEVO_PROCEDIMIENTO.md) | QA-014 — Diagnóstico de Nuevo Procedimiento | Vigente | Antecedente previo a mejoras, no presentar hallazgos ya tratados como estado final. |
| QA014_IMPLEMENTACION_NUEVO_PROCEDIMIENTO.md | [Ubicación](../../frontend-dimabug/docs/QA014_IMPLEMENTACION_NUEVO_PROCEDIMIENTO.md) | QA-014 — Implementación de Nuevo Procedimiento | Vigente | Conservar descripción del guardado incremental y pendientes funcionales. |
| QA015_AUDITORIA_INTEGRACION.md | [Ubicación](../../frontend-dimabug/docs/QA015_AUDITORIA_INTEGRACION.md) | QA-015 — Auditoría de integración previa al despliegue | Incompleto | Revisar alcance frente a Tickets y cambios posteriores; no convertir NO-GO histórico en estado del servidor. |
| QA016_1_DIAGNOSTICO_DOCKER.md | [Ubicación](../../frontend-dimabug/docs/QA016_1_DIAGNOSTICO_DOCKER.md) | QA-016.1 — Diagnóstico de Docker y preparación de MySQL 8 | Vigente | Conservar diagnóstico fechado, no inferir infraestructura actual. |
| QA016_2_PREPARACION_DOCKER_WSL2.md | [Ubicación](../../frontend-dimabug/docs/QA016_2_PREPARACION_DOCKER_WSL2.md) | QA-016 — Etapa de preparación Docker Desktop/WSL2 | Vigente | Conservar preparación histórica, servidor/esquema actual no verificados. |
| QA016_3_RECUPERACION_ESQUEMA_MYSQL.md | [Ubicación](../../frontend-dimabug/docs/QA016_3_RECUPERACION_ESQUEMA_MYSQL.md) | QA016.3 — Recuperación del esquema MySQL | Desactualizado | Actualizar inventario de 25 tablas frente a 31 requeridas por el código. |
| QA016_PLAN_MYSQL_AISLADO.md | [Ubicación](../../frontend-dimabug/docs/QA016_PLAN_MYSQL_AISLADO.md) | QA-016 — Plan de validación MySQL aislada | Vigente | Reutilizar cautelas del plan y revisar alcance antes de aprobar ejecución. |
| QA_ARCHIVOS.txt | [Ubicación](../../frontend-dimabug/docs/QA_ARCHIVOS.txt) | DimaBug — archivos nuevos, modificados o retirados de esta entrega | Vigente | Es manifiesto de una entrega histórica, no el estado Git actual. |
| QA_EMPAQUETADO_STAGING.md | [Ubicación](../../frontend-dimabug/docs/QA_EMPAQUETADO_STAGING.md) | DimaBug — corrección de configuración empaquetada | Desactualizado | Contrastar configuración compartida ausente y no atribuir checksum a JAR actual inexistente. |
| QA_MVP_ESTABILIZACION.md | [Ubicación](../../frontend-dimabug/docs/QA_MVP_ESTABILIZACION.md) | DimaBug — estabilización del MVP | Desactualizado | Marcar posteriores CRUD/Tests; la configuración y pendientes ya no describen el checkout completo. |
| QA_RESULTADOS.txt | [Ubicación](../../frontend-dimabug/docs/QA_RESULTADOS.txt) | DimaBug — resultados locales, 2026-10-12 America/Santiago | Vigente | Conservar conteos históricos con fecha, sin sumarlos a la nueva ejecución. |

### 3.2. Diagramas y presentación existentes

| Documento | Ubicación | Contenido | Estado | Acción recomendada |
|---|---|---|---|---|
| Modelo Draw.io | `entregables/modelo-relacional/DimaBug_modelo_relacional.drawio` | Seis páginas, resumen y detalle de 30 tablas mapeadas por JPA/uniones | Incompleto | Añadir la proyección JDBC de búsqueda, separar relaciones JPA de FK SQL y representar opcionalidad en una revisión autorizada |
| PNG para PPT | `entregables/modelo-relacional/DimaBug_modelo_relacional_PPT.png` | Resumen de 16 entidades | Vigente | Mantener explícita su condición de resumen, no usarlo como inventario completo |
| Cinco PNG de detalle | `entregables/modelo-relacional/DimaBug_detalle_*.png` | Identidad, Catálogos, Conocimiento, Procedimientos y Tickets | Incompleto | Actualizar junto con el Draw.io para incluir JDBC y la leyenda de evidencia de relaciones |
| Presentación de código | `entregables/presentacion-codigo/DimaBug_Estructura_del_Codigo.pptx` | Dos láminas editables de estructura y creación de ticket | Vigente | Adecuada como síntesis, no acredita despliegue ni todos los límites arquitectónicos |
| Generador del modelo | `entregables/modelo-relacional/generar_modelo.py` | Generación de Draw.io y PNG desde entidades y relaciones declaradas manualmente | Incompleto | Incorporar el uso JDBC y contrastar las relaciones inferidas antes de regenerar |

El ejemplo PPTX en Downloads y la imagen pegada previamente son referencias de presentación, no documentación versionada del proyecto ni evidencia de ejecución.

No se localizaron archivos Word (`.doc`/`.docx`) o PDF en el árbol autoral revisado. Los PNG y la PPTX existentes no sustituyen esos documentos ni se presupone que deban existir si la rúbrica no los exige.

### 3.3. Scripts SQL existentes

| Documento | Ubicación | Contenido | Estado | Acción recomendada |
|---|---|---|---|---|
| Estado de conocimiento | `dima-bug-backend/src/main/resources/db/migration/V20260925_01__agregar_estado_eliminado_conocimiento.sql` | ALTER del ENUM a BORRADOR, PUBLICADO y ELIMINADO | Incompleto | Relacionar con una línea base e identificar aplicación y versión MySQL verificadas |
| Ampliación de solución | `dima-bug-backend/src/main/resources/db/migration/V20261013_01__ampliar_solucion.sql` | Título, estado y autoservicio con ALTER INSTANT | Incompleto | Validar precondiciones y compatibilidad antes de cualquier ejecución |
| Organización persistente | `dima-bug-backend/src/main/resources/db/migration/V20261014_01__organizacion_persistente.sql` | Versiones de departamento/contacto, tabla y fila de control | Incompleto | Documentar aplicación por sentencia y prueba real de concurrencia |
| Módulo Tickets | `dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql` | Tablas de tickets, comentarios, historial y etiquetas, con índices y FK internas | Incompleto | Conciliar contra un esquema aprobado y conservar distinción de referencias externas sin FK |
| Adjuntos y nivel de etiqueta | `dima-bug-backend/src/main/resources/db/migration/V20261017_02__tickets_adjuntos_y_nivel_etiqueta.sql` | Nivel y tabla de adjuntos | Incompleto | Revisar el DROP de `ticket_adjunto` antes de autorizar su uso en un entorno que pueda contener datos |

Todos se leyeron como texto. No hay Flyway ni Liquibase declarados en el POM. Su ubicación en `db/migration` no prueba aplicación automática ni manual.

### 3.4. Evidencias y documentos no encontrados

| Documento | Ubicación buscada | Contenido esperado | Estado | Acción recomendada |
|---|---|---|---|---|
| README backend | `dima-bug-backend/README.md` | Entrada de instalación y documentación backend | No encontrado | Resolver referencias rotas y reutilizar las guías existentes al completar el índice |
| DDL base completo | Raíz, recursos, documentación y scripts | Esquema reproducible incluyendo FULLTEXT | No encontrado | Obtener estructura autorizada y revisada, sin datos personales |
| Diccionario de datos completo | Documentación existente | Columnas, tipos, nulabilidad, claves y origen de evidencia | No encontrado | Construir posteriormente con JPA, SQL y metadatos autorizados |
| OpenAPI / Swagger | POM, configuración, recursos y fuentes | Especificación o configuración `springdoc` | No encontrado | Inventariar contratos reales antes de elegir una herramienta |
| Colección Postman | Todo el árbol autoral | Requests y entornos saneados | No encontrado | No inventar su existencia, evaluar necesidad académica |
| Requisitos aprobados y matriz completa de HU/RF/RNF | Raíz y ambas carpetas docs | Alcance y aceptación trazables | No encontrado | Reunir documentación académica del equipo; los tests HU01–HU07 no sustituyen requisitos aprobados |
| Manual de usuario consolidado | Raíz y ambas carpetas docs | Uso por perfil de las funciones vigentes | No encontrado | Reutilizar explicaciones existentes y completar tras autorización |
| Manual técnico consolidado | Raíz y ambas carpetas docs | Arranque reproducible, mantenimiento y despliegue | No encontrado | Integrar referencias existentes sin duplicarlas |
| UAT y evidencias E2E | Reportes y documentación | Casos aceptados, ambiente, fecha y responsables | No encontrado | Mantener separadas pruebas locales, UAT y despliegue |
| Workflow de CI | `.github/workflows/`, backend y frontend | Automatización ejecutable | No encontrado | Conciliar `ci-pruebas-backend.md` con este checkout y evidencia remota |
| Soporte de integración MySQL documentado | `src/test/.../integration/`, `src/test/resources/` | Testcontainers, fixture SQL y cinco clases IT | No encontrado | Recuperar del origen correcto o marcar como evidencia externa no disponible |
| Migración FK de usuarios y diagnóstico | Recursos DB | Archivos de octubre 8 citados por las guías | No encontrado | Identificar rama o entrega original antes de reutilizar esos procedimientos |
| Migración de snapshot de pasos | Recursos DB | Archivo de octubre 9 citado por RN-PROC-01 | No encontrado | Resolver contradicción entre la decisión documentada y el modelo actual |
| Despliegue ejecutado y restauración probada | Documentación y artefactos | Run, release, configuración saneada y comprobaciones | No encontrado | Los planes/handoff existentes no son evidencia del despliegue actual |
| Rúbrica, actas y aceptaciones formales | Material disponible | Requisitos de evaluación Duoc y aprobación de riesgos | No encontrado | Solicitar al equipo en una fase posterior |

### 3.5. Duplicados y referencias rotas

No se encontraron documentos Markdown/TXT con SHA-256 idéntico. Los diagnósticos y sus implementaciones forman secuencias históricas y no deben eliminarse como duplicados. Las guías de integración, auditoría Skill 4, handoff y aceptación de riesgos comparten resultados y pendientes; hay redundancia temática y contradicciones de vigencia. Se recomienda un índice y referencias cruzadas, conservando los originales.

Se localizaron 22 referencias Markdown a destinos locales ausentes, distribuidas en diez documentos. El anexo de evidencia identifica documento, línea y destino. No se validaron enlaces privados ni resultados remotos de CI.

## 4. Tecnologías y versiones comprobadas

| Componente | Evidencia declarada | Evidencia local exacta | Alcance |
|---|---|---|---|
| Angular Core | `package.json`: `^19.2.0` | Lockfile e instalación: **19.2.25** | No confundir rango permitido con versión resuelta |
| Angular CLI / builder | `^19.2.27` | Lockfile: **19.2.27** | Herramientas de desarrollo |
| TypeScript | `~5.7.2` | Lockfile: **5.7.3** | Compilación aplicación y specs comprobada |
| RxJS | `~7.8.0` | Lockfile: **7.8.2** | HTTP y estado reactivo |
| Zone.js | `~0.15.0` | Lockfile: **0.15.1** | Runtime Angular |
| Chart.js | `^4.5.1` | Lockfile: **4.5.1** | Gráficos frontend |
| Node.js usado | No fijado mediante un archivo de versión en el árbol revisado | **24.15.0** | Herramienta local, no versión del servidor desplegado |
| Java | POM: **21** | Temurin **21.0.12.1**, comprobado por `java -version` y wrapper | No certifica JDK de STAGING |
| Spring Boot | Parent POM: **4.1.1** | La ejecución local de pruebas identifica **4.1.1** | No se dedujo de la carpeta ni del README |
| Maven Wrapper | wrapper **3.3.4** | Maven **3.9.16** | Distribución configurada y herramienta ejecutada |
| JJWT | POM: **0.13.0** | Dependencias declaradas api, impl y jackson | No equivale a una auditoría SCA Java completa |
| ArchUnit | POM: **1.4.1**, scope test | `TicketsArquitecturaTest` ejecutado | Control específico, no global |
| H2 | Scope test | Dos clases con DataSource `jdbc:h2:mem:` explícito | Compatibilidad de modo MySQL no certifica InnoDB/FULLTEXT |
| MySQL | Connector/J en POM | Servidor y esquema real **[PENDIENTE DE VERIFICACIÓN]** | La imagen 8.0.36 citada en guías no acredita el servidor actual |

No hay SCA Maven, SBOM ni perfil Testcontainers/Failsafe declarados en el POM actual. No se instalaron herramientas para suplirlos.

## 5. Frontend auditado

Se identificaron 39 archivos `*.component.ts` y 33 `*.spec.ts`. La estructura real incluye componentes standalone bajo `features`, servicios/modelos/guards/configuración en `core` y layout/UI en `shared`. `app.config.ts` registra Router, HttpClient con interceptor y gráficos. Las rutas de Tickets y Chatbot usan carga diferida de componentes; no se afirma que todas las pantallas sean lazy.

### 5.1. Funciones y comunicación

- Identidad: login y administración de usuarios, con rutas protegidas. La recuperación tiene interfaz y peticiones frontend, pero no endpoints backend correspondientes.
- Catálogos y organización: hardware, sistemas, módulos, frecuencias, departamentos, contactos, responsables y pruebas; administración persistente presente en código actual.
- Conocimiento: formulario, lista y detalle, síntomas/causas/pruebas/soluciones/materiales, búsqueda y favoritos locales.
- Procedimientos y ejecuciones: formulario incremental, pasos, materiales, estados y checklist; `cambiosProcedimientoGuard` controla salida con cambios sin guardar.
- Tickets: alta, detalle, bandeja, asignación, estado, comentarios, prioridad, etiquetas y adjuntos mediante `TicketService`.
- Notificaciones: composición de avisos a partir de consultas de datos, con caché y marcas leídas en el navegador. No se encontró un backend de notificaciones push, email o WebSocket.

`api.ts` construye URLs y reconoce únicamente el origen/base de la API para enviar JWT. Desarrollo usa `/api` con proxy a `localhost:9090`; producción usa mismo origen por defecto con `apiUrl` vacío. El proxy no toma dinámicamente su destino de `environment.ts`, aunque un comentario lo sugiera: ambos archivos deben mantenerse alineados.

### 5.2. Validación y acceso

`authGuard`, `adminGuard`, `staffGuard` y `guestGuard` están implementados. Protegen navegación, no sustituyen autorización del servidor. Los formularios usan validadores Angular y controles de envío/carga; hay pruebas de componentes y servicios. No se considera que la validación de frontend por sí sola impida requests manuales.

`AuthService` almacena token y sesión en localStorage. `isAuthenticated` solo comprueba presencia de token/usuario; no analiza expiración. La API sigue verificando el JWT. El interceptor restringe el destino de Authorization y maneja 401 consultando `/auth/me` en determinadas condiciones.

Los favoritos y ciertas cachés usan claves globales del navegador, mientras que las notificaciones de Tickets sí separan claves por ID de usuario. `logout` retira la sesión de autenticación, pero no limpia automáticamente todas las cachés de otras funciones. Esto requiere una política de aislamiento y privacidad en equipos compartidos, no un cambio improvisado de almacenamiento.

### 5.3. Comportamientos parciales

`ReportesComponent` incluye una serie mensual literal de vistas/útiles. No hay prueba de que esa serie proceda de métricas persistidas. El formulario `ErrorNuevoComponent` utiliza `CatalogoService.guardarError` con persistencia local y anuncia que soporte puede verlo; no se localizó un flujo backend compartido equivalente. Estos comportamientos deben identificarse como locales/demostrativos y distinguirse del módulo Tickets real.

## 6. Backend y comprobación arquitectónica

Se identificaron 454 fuentes Java de producción, 37 controladores, 28 entidades y 100 fuentes de prueba, de las cuales 92 terminan en `Test.java`. Los conteos de adaptadores, repositorios y servicios dependen del sufijo del archivo y no representan una medición de cobertura.

### 6.1. Arquitectura observada

Un POM y un arranque Spring Boot agrupan `identidad`, `catalogo`, `conocimiento`, `procedimiento`, `ejecucion`, `seguimiento`, `tickets` y `shared`. Los siete primeros representan áreas funcionales, y `shared` reúne servicios/contratos transversales.

Evidencias de separación:

1. `CrearTicketService` implementa `CrearTicketUseCase` y depende de `TicketRepositoryPort`, `CatalogoConsultaPort` y contexto del caso de uso.
2. `TicketRepositoryAdapter` implementa el puerto y convierte modelos de dominio mediante mappers hacia entidades/repositorios JPA.
3. Los adaptadores de integración de Tickets acceden a las API públicas de Identidad, Catálogos y Conocimiento, en lugar de importar sus interiores desde Application.
4. La inspección de imports de Domain no encontró dependencias de Application/Infrastructure ni Spring/JPA. Application no importa Infrastructure en la búsqueda realizada.
5. `TicketsArquitecturaTest` verifica seis reglas sobre dominio, capas, límites con otros módulos y uso de API públicas; las seis pasaron.

Limitaciones reales:

- Application sí usa Spring: se encontraron imports Spring en 48 archivos, por ejemplo servicios/transacciones/eventos. No es independencia completa del framework.
- Fuera de Tickets, varios controllers inyectan servicios concretos directamente; no existe un puerto de entrada uniforme en todo el backend.
- Procedimiento reutiliza DTO de infraestructura de Conocimiento. Entidades/mappers y adaptadores de distintos módulos comparten tipos de persistencia. La modularidad no implica aislamiento de despliegue o de esquema.
- Las reglas ArchUnit encontradas tienen alcance Tickets. No certifican por sí solas todo el monolito.

**Descripción defendible:** monolito modular con patrón hexagonal implementado de forma comprobable, más explícito y controlado en Tickets, y con acoplamientos transversales en otros módulos. No corresponde presentarlo como microservicios ni como hexagonal pura sin excepciones.

### 6.2. Contratos, validación y errores

Los controladores usan DTO, `@Valid` y Bean Validation. Se encontraron restricciones NotBlank/NotNull/Email/Size/Positive y reglas en servicios/dominio según el contrato. No todos los límites declarados en columnas JPA aparecen idénticos en cada DTO, por lo que la futura documentación debe distinguir validación HTTP, negocio y persistencia.

`GlobalExceptionHandler` y `TicketExceptionHandler` traducen errores a 400, 401, 403, 404, 409, 413, 500 o 503 según el caso. El handler de integridad evita devolver el mensaje SQL completo. Los mensajes de negocio y errores de archivos conservan texto controlado por la aplicación. Los estados de seguridad deben comprobarse también con el contenedor HTTP real, no solamente MockMvc.

El anexo enumera las rutas declaradas que se extrajeron de los controladores. Son contratos de código, no requests ejecutados contra el servidor.

## 7. Base de datos y persistencia

### 7.1. Inventario de tablas requeridas

| Tabla | Origen de evidencia | PK declarada o límite |
|---|---|---|
| `cl_frecuencia` | [dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/FrecuenciaEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/FrecuenciaEntity.java) | `frecuencia_id` |
| `cl_hardware` | [dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/HardwareEntity.java:8](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/HardwareEntity.java) | `hardware_id` |
| `cl_hardware_sistema` | Unión declarada en mapeo JPA | `hardware_id + sistema_id` |
| `cl_modulo` | [dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/ModuloEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/ModuloEntity.java) | `modulo_id` |
| `cl_sistema` | [dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/SistemaEntity.java:8](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/persistence/entity/SistemaEntity.java) | `sistema_id` |
| `co_causa` | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/CausaEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/CausaEntity.java) | `causa_id` |
| `co_conocimiento` | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/ConocimientoEntity.java:11](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/ConocimientoEntity.java) | `conocimiento_id` |
| `co_conocimiento_busqueda` | SQL JDBC, sin entidad JPA | `No certificada por DDL disponible` |
| `co_conocimiento_prueba` | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/ConocimientoPruebaEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/ConocimientoPruebaEntity.java) | `conocimiento_id + prueba_id (EmbeddedId)` |
| `co_material_apoyo` | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/MaterialApoyoEntity.java:7](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/MaterialApoyoEntity.java) | `material_id` |
| `co_prueba` | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/PruebaEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/PruebaEntity.java) | `prueba_id` |
| `co_sintoma` | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/SintomaEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/SintomaEntity.java) | `sintoma_id` |
| `co_solucion` | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/SolucionEntity.java:7](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/SolucionEntity.java) | `solucion_id` |
| `co_solucion_asignacion` | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/SolucionAsignacionEntity.java:8](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/entity/SolucionAsignacionEntity.java) | `asignacion_id` |
| `pr_paso` | [dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/persistence/entity/PasoEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/persistence/entity/PasoEntity.java) | `paso_id` |
| `pr_procedimiento` | [dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/persistence/entity/ProcedimientoEntity.java:9](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/persistence/entity/ProcedimientoEntity.java) | `procedimiento_id` |
| `se_ejecucion` | [dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/persistence/entity/EjecucionEntity.java:9](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/persistence/entity/EjecucionEntity.java) | `ejecucion_id` |
| `se_ejecucion_paso` | [dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/persistence/entity/EjecucionPasoEntity.java:7](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/persistence/entity/EjecucionPasoEntity.java) | `ejecucion_paso_id` |
| `se_resultado` | [dima-bug-backend/src/main/java/cl/casol/backend/seguimiento/infrastructure/persistence/entity/ResultadoSolucionEntity.java:7](../../dima-bug-backend/src/main/java/cl/casol/backend/seguimiento/infrastructure/persistence/entity/ResultadoSolucionEntity.java) | `resultado_id` |
| `ticket` | [dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/TicketEntity.java:14](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/TicketEntity.java) | `id` |
| `ticket_adjunto` | [dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/AdjuntoTicketEntity.java:8](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/AdjuntoTicketEntity.java) | `id` |
| `ticket_comentario` | [dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/ComentarioTicketEntity.java:8](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/ComentarioTicketEntity.java) | `id` |
| `ticket_etiqueta` | [dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/EtiquetaEntity.java:7](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/EtiquetaEntity.java) | `id` |
| `ticket_historial_estado` | [dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/HistorialEstadoTicketEntity.java:9](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/persistence/entity/HistorialEstadoTicketEntity.java) | `id` |
| `ticket_ticket_etiqueta` | Unión declarada en mapeo JPA | `ticket_id + etiqueta_id` |
| `u_departamento` | [dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/DepartamentoEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/DepartamentoEntity.java) | `departamento_id` |
| `u_departamento_contacto` | [dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/DepartamentoContactoEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/DepartamentoContactoEntity.java) | `contacto_id` |
| `u_organizacion_control` | [dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/OrganizacionControlEntity.java:3](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/OrganizacionControlEntity.java) | `control_id` |
| `u_responsable` | [dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/ResponsableEntity.java:6](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/ResponsableEntity.java) | `responsable_id` |
| `u_rol` | [dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/RolEntity.java:8](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/RolEntity.java) | `rol_id` |
| `u_usuario` | [dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/UsuarioEntity.java:8](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/persistence/entity/UsuarioEntity.java) | `usuario_id` |

Total lógico: **31**. Las 30 del modelo anterior corresponden a entidades y uniones; falta `co_conocimiento_busqueda`, utilizada por `BusquedaConocimientoJdbcAdapter`. Este total no certifica que todas existan físicamente ni descarta tablas adicionales del servidor.

### 7.2. Tres niveles de relación

**Relaciones JPA:** `@ManyToOne`/`@JoinColumn` en Usuario–Rol, Conocimiento–catálogos/autores, asignaciones de solución, autores de Procedimiento y usuario de Ejecución. Hardware–Sistema usa `@ManyToMany` y `cl_hardware_sistema`; la colección de etiquetas de Ticket usa `ticket_ticket_etiqueta`. Estas declaraciones no demuestran FK instaladas.

**FK explícitas en los SQL presentes:** se encontraron seis declaraciones FOREIGN KEY internas de Tickets: dos en `ticket_ticket_etiqueta`, una en comentarios, una en historial y dos en adjuntos. El anexo detalla cada declaración. No hay FK explícitas hacia usuarios/catálogos/conocimiento en el SQL de Tickets. No se afirma que los scripts se hayan aplicado.

**Identificadores escalares:** conocimiento de síntomas/causas/soluciones/materiales, procedimiento de pasos/ejecuciones, ejecución y paso del checklist, usuario/solución de resultados, departamento de responsables/contactos y las referencias externas de Tickets. Pueden tener validación de aplicación, pero eso no confirma una restricción SQL.

La tabla puente Conocimiento–Prueba usa clave compuesta con `ConocimientoPruebaId`; una parte referencia JPA a Prueba y la otra se conserva en la clave. La opcionalidad y pertenencia deben documentarse por columna, sin convertir cada ID en una FK probada.

### 7.3. Claves, restricciones e índices

- Las entidades definen PK `@Id` y mayoritariamente IDENTITY, más claves compuestas o tablas de unión según el caso.
- JPA declara unicidad para email de usuario, nombre de rol, hardware, sistema y descripción de prueba, entre otros. `pr_paso` declara UNIQUE(procedimiento_id, paso_orden) y `se_ejecucion_paso` UNIQUE(ejecucion_id, paso_id).
- Departamento y Contacto usan `@Version`; Responsable usa una revisión calculada y bloqueo del servicio, no una nueva columna `@Version`.
- Los scripts de Tickets incluyen PK, UNIQUE, índices de filtros y CHECK de estado/prioridad. `IF NOT EXISTS` no valida que una tabla preexistente tenga la misma estructura.
- La búsqueda JDBC usa `MATCH ... AGAINST`, parámetros y una proyección `co_conocimiento_busqueda`. Falta el DDL/FULLTEXT reproducible. No se afirma que el índice exista ni que el ranking haya sido probado en MySQL durante esta auditoría.
- No hay prueba local del motor, collation, acciones ON DELETE, privilegios, defaults ni restricciones instaladas para el resto del esquema.

### 7.4. Transacciones y concurrencia

Se observaron `@Transactional`, `TransactionTemplate`, bloqueos pesimistas y versiones, pero su mera presencia no acredita cobertura de todas las carreras.

- Organización y soluciones tienen protocolos de bloqueo y pruebas unitarias/contratos. Las pruebas H2 de copia de soluciones y materiales sin OSIV ejercitan persistencia real en memoria, no MySQL.
- `GestionarEjecucionService` obtiene la ejecución mediante `buscarPorId`; su repositorio no declara bloqueo/versionado de cabecera. Completar y actualizar un paso separan lectura del estado y escritura. La carrera descrita como corregida en las guías de integración no queda sustentada por este código actual. Riesgo estático; no se forzó una carrera en MySQL.
- Tickets genera el siguiente código mediante MAX(correlativo)+1 y guarda agregados sin versión/bloqueo de lectura declarado. Dos altas simultáneas pueden competir por el mismo código y dos ediciones pueden sobrescribirse. UNIQUE protege duplicación de código si la migración está aplicada, pero no sustituye asignación atómica ni gestión de reintentos. No se reprodujo el intercalado físico.
- Reindexación de organización/pruebas usa eventos AFTER_COMMIT y captura errores. Una escritura confirmada puede dejar la proyección pendiente; no se encontró una cola durable de reintentos.
- Filesystem y BD no comparten transacción. Los materiales implementan compensación/limpieza con límites explícitos. Tickets dispone de almacenamiento distinto con eliminación que ignora IOException. La recuperación tras crash y la reconciliación no están acreditadas.

### 7.5. Historia de procedimientos

La decisión RN-PROC-01 documenta copia de instrucciones, orden y criticidad al iniciar una ejecución. Sin embargo, el código actual de `EjecucionPasoEntity` solo guarda referencias, cumplimiento, observación y fecha; `listarPasos` recupera la definición actual de `pr_paso`. No se encontró la migración ni los campos de snapshot citados por la guía.

Existe una protección diferente: la definición de un procedimiento PUBLICADO es inmutable por los servicios revisados. Esa protección debe preservarse, pero no convierte el checklist en un snapshot ni reconstruye textos históricos. La contradicción debe resolverse con el equipo antes de presentar esa capacidad como finalizada.

## 8. Seguridad y dependencias

### 8.1. Controles encontrados

- Spring Security configura STATELESS y JWT HMAC, valida firma, expiración y subject. El adaptador exige clave de al menos 32 bytes y duración positiva. BCrypt se encuentra detrás de un puerto de aplicación.
- `/api/admin/**` exige ADMINISTRADOR. Hay permisos diferenciados para ADMINISTRADOR, TECNICO, SOPORTE y USUARIO, controles de propietario en ejecuciones/Tickets y verificación adicional de visibilidad de borradores y contenido eliminado.
- El rol se obtiene del usuario resuelto por repositorio/caché, no de un claim confiado al frontend. La caché dura hasta 300000 ms. El adaptador invalida por email en guardados, pero hay que verificar alias, cambios externos y múltiples instancias.
- El interceptor Angular limita el envío de Authorization a la API configurada.
- Materiales validan tamaño, tipo y firma de contenido; las descargas de materiales agregan Content-Disposition, nosniff y no-store. Las rutas físicas usan referencias UUID y validación de claves.
- No se encontraron secretos reales que deban reproducirse en este informe. La inspección de configuración no equivale a un análisis completo de historial Git o secretos externos.

### 8.2. Riesgos y límites con referencia OWASP

Se utiliza [OWASP Top 10:2025](https://top10.owasp.org/2025/0x00_2025-Introduction/) como taxonomía, no como certificación. Las observaciones siguientes no implican que todas las categorías sean vulnerabilidades explotables en DimaBug.

| Área revisada | Evidencia / límite | Categoría orientativa | Acción priorizada |
|---|---|---|---|
| Autorización | Matchers, visibilidad y propietario implementados; cobertura local parcial respecto de todos los métodos/roles | A01, control de acceso | Mantener pruebas negativas y validar HTTP real con las combinaciones restantes |
| Autenticación | Usuario inactivo se comprueba antes de BCrypt y recibe error distinto | A07, fallos de autenticación | Evaluar respuesta uniforme para contraseña incorrecta y ensayo de enumeración controlado |
| Sesión | JWT en localStorage; logout local sin revocación; no refresh tokens encontrados | A07 y A02 | Documentar ventana de vigencia, XSS, expiración e invalidación |
| Cachés | Claves de usuarios/favoritos globales y caché backend por email/alias | A01 y A02 | Ensayar cambio de usuario, desactivación/rol, varias instancias y limpieza acordada |
| CORS / CSRF | No se encontró configuración CORS explícita; proxy/mismo origen previstos; CSRF desactivado en el diseño Bearer | A02 | Verificar orígenes y modo de autenticación desplegado; no declarar CORS abierto ni un bypass CSRF sin evidencia |
| Entradas y SQL | DTO/negocio y JDBC parametrizado en recorridos examinados | A05, inyección | Conservar parámetros y revisar campos no cubiertos, sin afirmar ausencia universal de inyección |
| Archivos | Compensación existente, límites en recuperación, ancestros de ruta y adaptador Tickets | A02 y A10 | Almacenamiento privado y reconciliación con plan probado, más casos de fallo controlados |
| Logging | Hay llamadas DEBUG que incluyen email, URI y rol | A09 | Verificar nivel efectivo y políticas de datos; no consta DEBUG habilitado en los únicos properties fuente presentes |
| Dependencias | Avisos npm comprobados, sin SCA Maven actual | A03, cadena de suministro | Evaluar alcance y preparar actualización compatible en un trabajo autorizado |
| Producción | Perfil staging básico, sin evidencia de TLS, Nginx, copias o restauración actuales | A02 | Obtener evidencia saneada del entorno; no inferirla de los planes |

Las guías de seguridad describen mecanismos que difieren del código: `seguridad-integracion.md` afirma uso de `setStatus` y BCrypt antes de activo. El código conserva `sendError` y la comprobación de activo antes de contraseña. Además `/error` figura actualmente como público. Por ello no se afirma automáticamente que siga ocurriendo el 401 por redespacho que aquel documento relataba: exige prueba HTTP real con este árbol.

No se identificó rate limiting, bloqueo de intentos, MFA, revocación ni SCA Java configurados en las fuentes revisadas. Su ausencia local no demuestra que el proxy o infraestructura externa carezcan de controles equivalentes.

### 8.3. Auditoría npm reproducida

Comando de lectura: `npm.cmd audit --json --ignore-scripts`, desde `frontend-dimabug`. El resultado completo saneado está en [DEPENDENCIAS_NPM.json](evidencias/DEPENDENCIAS_NPM.json). Se distingue la versión del lockfile, el rango afectado y el carácter dev/prod. El código de salida 1 es compatible con avisos encontrados; no significa que la consulta al registro haya fallado en el intento final.

| Paquete | Versiones resueltas | Alcance en lockfile | Severidad npm | Evidencia pública |
|---|---|---|---|---|
| `@angular-devkit/build-angular` | 19.2.27 | dev | high | Propagación de dependencias, ver JSON |
| `@angular/cli` | 19.2.27 | dev | high | Propagación de dependencias, ver JSON |
| `@angular/common` | 19.2.25 | prod / runtime | high | [Aviso](https://github.com/advisories/GHSA-48r7-hpm6-gfxm) |
| `@angular/compiler` | 19.2.25 | prod / runtime | high | [Aviso](https://github.com/advisories/GHSA-58w9-8g37-x9v5) |
| `@angular/compiler-cli` | 19.2.25 | dev | moderate | Propagación de dependencias, ver JSON |
| `@angular/core` | 19.2.25 | prod / runtime | high | [Aviso](https://github.com/advisories/GHSA-rgjc-h3x7-9mwg) |
| `@angular/forms` | 19.2.25 | prod / runtime | moderate | Propagación de dependencias, ver JSON |
| `@angular/platform-browser` | 19.2.25 | prod / runtime | moderate | Propagación de dependencias, ver JSON |
| `@angular/platform-browser-dynamic` | 19.2.25 | prod / runtime | moderate | Propagación de dependencias, ver JSON |
| `@angular/router` | 19.2.25 | prod / runtime | high | [Aviso](https://github.com/advisories/GHSA-ff3f-86qr-9cv3) |
| `piscina` | 4.8.0 | dev | critical | [Aviso](https://github.com/advisories/GHSA-x9g3-xrwr-cwfg) |
| `proxy-addr` | 2.0.7 | dev | critical | [Aviso](https://github.com/advisories/GHSA-jqcg-44mw-7w3h) |
| `shell-quote` | 1.10.0 | dev | critical | [Aviso](https://github.com/advisories/GHSA-pqg4-j6r4-53mv) |
| `tar` | 6.2.1, 7.5.22 | dev | critical | [Aviso](https://github.com/advisories/GHSA-34x7-hfp2-rc4v) |

Las 54 entradas son paquetes afectados reportados por npm, con propagaciones por dependencia. No son 54 vulnerabilidades únicas independientes ni 54 fallos demostrados de la aplicación. Algunos avisos Angular requieren SSR/hidratación, que no se encontraron configurados en esta SPA. Los cuatro paquetes con severidad crítica están marcados dev en el lockfile, por lo que su exposición principal es desarrollo/construcción/CI y depende del uso y de entradas no confiables.

La tabla muestra todas las versiones resueltas de cada paquete seleccionado. En particular, `tar` tiene copias 6.2.1 y 7.5.22: el rango agregado del aviso npm es `<=7.5.20`, por lo que no debe afirmarse que ambas copias estén afectadas por ese conjunto de avisos. La revisión de actualización debe seguir cada ruta transitiva.

Se contrastaron avisos en el catálogo público: [Angular host bindings](https://github.com/advisories/GHSA-hh8m-fm6v-7cvg), [piscina](https://github.com/advisories/GHSA-67c8-pqhq-4rmx) y [shell-quote](https://github.com/advisories/GHSA-pqg4-j6r4-53mv). Las fuentes confirman versiones afectadas, no un exploit del proyecto. No se ejecutó ningún payload ni actualización automática. Un salto mayor sugerido por npm debe evaluarse con todo Angular/CLI y las pruebas, no aplicarse a ciegas.

El POM y la presencia de bibliotecas Java se revisaron, pero no se dispone de un escaneo SCA completo de transitivas Maven. Ese punto queda **[PENDIENTE DE VERIFICACIÓN]** y no permite declarar el backend libre de dependencias vulnerables.

## 9. Matriz entre funcionalidad, documentación y código

| Elemento | Documentación actual | Evidencia en código | Coincidencia / clasificación | Acción |
|---|---|---|---|---|
| Arquitectura hexagonal | Arquitectura/ADR describen capas, pero niegan pruebas automáticas identificadas | Puertos y adapters reales; seis reglas ArchUnit de Tickets | Parcial. Implementada y verificada dentro del alcance Tickets | Actualizar alcance y excepciones, no prometer pureza global |
| Angular | README lo identifica como 19, pero cubre pocas rutas | Core 19.2.25, rutas actuales, componentes y servicios | Implementada y verificada para compilación/pruebas locales | Documentar versión resuelta y navegación vigente |
| Login/JWT/BCrypt | Guías y README | AuthController, servicio y adaptadores; pruebas unitarias/MVC | Implementada y verificada localmente; E2E/MySQL pendiente | Corregir contradicción del orden de validación y actualizar alcance |
| Roles/permisos | Seguridad enumera principalmente dos roles | SecurityConfig incorpora cuatro, administración y controles de propietario | Implementada y verificada en escenarios de pruebas | Matriz actual por método/recurso, no solo por prefijo |
| Recuperación de contraseña | README la ofrece como ruta y endpoint esperado | AuthService/RecuperarComponent hacen requests, AuthController solo login/me | Parcialmente implementada | Etiquetar pendiente backend; no presentarla como operativa |
| Usuarios y catálogos | QA MVP, QA009–011 | Servicios, controllers, adapters y suites locales | Implementada y verificada en casos locales | Enlazar evidencias actuales sin borrar informes previos |
| Departamentos/contactos | QA012 implementación | Servicios administrativos, versiones, eventos y API | Implementada y verificada localmente; MySQL pendiente | Documentar requisitos de columnas y límites del índice |
| Responsables administrativos | QA012 dice no implementado en esa entrega | AdministrarResponsablesService y AdminResponsableController, tests | Implementada y verificada localmente | Marcar avance posterior y no usar QA012 como estado global vigente |
| Asignación con solo responsable | QA012/QA013 reconocen validación pendiente de padre activo | Servicio consulta responsable activo, sin verificar su departamento cuando no se envía | Parcialmente implementada | Mantener pendiente y decisión funcional, sin cambiar regla aquí |
| Base de conocimiento | QA/arquitectura | CRUD, estados, hijos, filtros y políticas de visibilidad | Implementada y verificada en escenarios locales | Completar requisitos y prueba integrada con MySQL |
| Búsqueda FULLTEXT | Base de datos e integración | Adaptador JDBC y proyección de búsqueda | Implementada, pero no verificada en MySQL aquí | Recuperar DDL/índice y ejecutar caso autorizado |
| Soluciones/autoservicio | QA009 | Modelo ampliado, administración, búsqueda y pruebas | Implementada y verificada localmente | Conciliar migración y evidencia física |
| Chatbot | No hay guía funcional consolidada | POST /api/chatbot/consultas, servicio de búsqueda autoservicio y UI | Implementada y verificada localmente como asistente de recuperación | No describirlo como LLM/IA generativa ni integración externa |
| Procedimientos | QA014 | BORRADOR/PUBLICADO, formulario incremental, pasos y materiales | Implementada y verificada en escenarios locales | Documentar guardado incremental y publicación sin mínimo de pasos |
| Ejecuciones/checklist | Guías de integración y QA MVP | Servicio de inicio/consulta/cierre con propietario | Implementada y verificada localmente en escenarios secuenciales | Probar carreras y persistencia real antes de cerrar riesgo |
| Snapshot histórico | RN-PROC-01 dice implementado | Campos y migración no encontrados; definición se lee de Paso actual | Documentada, pero no encontrada en el código | Resolver versión de origen; no afirmar finalización |
| Efectividad de soluciones | Guías de seguimiento | RegistrarEfectividadSolucionService, entidad y tests | Implementada y verificada localmente | Acreditar consultas/commit real y UAT |
| Materiales y archivos | ADR y guías detalladas | Servicios, MIME/tamaño, adapters, tests y H2 sin OSIV | Implementada y verificada en escenarios locales | Conservar límites de crash/limpieza, validar MySQL+filesystem |
| Tickets | Documentación general backend no los incorpora; PPT sí resume alta | Casos de uso, API, dominio, seis tablas SQL y controles locales | Implementada y verificada en escenarios de pruebas | Crear trazabilidad documental y ensayo físico de contratos |
| Notificaciones | Sin manual consolidado | NotificacionService/TicketAvisosService y marcas locales | Implementada, pero no verificada extremo a extremo | Explicar consulta/caché local y ausencia de entrega push/email acreditada |
| Favoritos | Sin manual consolidado | FavoritosService usa localStorage, sin API propia | Implementada, pero no verificada entre perfiles | Definir alcance local y aislamiento |
| Reportes | Pantalla disponible, sin trazabilidad de las métricas | Serie mensual literal en ReportesComponent | Parcialmente implementada | Identificar datos demostrativos y métricas reales separadamente |
| Nuevo error | QA MVP lo deja como persistencia pendiente | Guardado por CatalogoService local, con mensaje de entrega a soporte | Parcialmente implementada | Aclarar alcance y no confundir con Tickets |
| NFC | Sin contrato funcional localizado | No hay lector NDEF ni integración; NFC encontrado es normalización Unicode | No verificable con la evidencia disponible | Registrar ausencia; no clasificar como plan aprobado sin requisito |
| Integraciones LLM/email/push | Sin contrato implementado localizado | No hay proveedor/SDK/cliente de esos servicios en el recorrido revisado | No verificable con la evidencia disponible | No presentar integraciones externas como terminadas |
| CI backend | Guía dice workflow implementado | No hay workflow ni perfil integration-mysql ni IT citadas | Documentada, pero no encontrada en el código | Recuperar artefacto correcto y run real verificable |
| Despliegue | QA015/QA016 mantienen NO-GO y handoff condicionado | Perfil staging presente, sin evidencia del servidor actual | No verificable con la evidencia disponible | Mantener preparación/planes separados del despliegue ejecutado |
| QA/MySQL histórico | Documentos afirman 45 integraciones PASS en otro estado | No existen cinco IT ni fixture ni reportes Failsafe disponibles | No verificable con la evidencia disponible | No sumar esos conteos a las pruebas actuales |
| Versionado/duplicación de procedimientos | QA lo propone como evolución | No hay contrato general de versiones/snapshot vigente | Planificada en documentos históricos | Revalidar vigencia del plan con equipo |

## 10. Contradicciones localizadas

| Elemento | Afirmación documental y ubicación | Evidencia actual y ubicación | Diferencia / acción |
|---|---|---|---|
| Instalación frontend | [frontend-dimabug/README.md:8](../../frontend-dimabug/README.md) | [frontend-dimabug/proxy.conf.json:3](../../frontend-dimabug/proxy.conf.json) | README señala 8080, proxy y perfil actual usan 9090. |
| Configuración compartida | [frontend-dimabug/docs/QA_EMPAQUETADO_STAGING.md:15](../../frontend-dimabug/docs/QA_EMPAQUETADO_STAGING.md) | [.gitignore:29](../../.gitignore) | El archivo sigue ignorado y no existe en src/main/resources; solo staging está presente. |
| Arquitectura y pruebas | [dima-bug-backend/docs/arquitectura.md:8](../../dima-bug-backend/docs/arquitectura.md) | [dima-bug-backend/src/test/java/cl/casol/backend/tickets/arquitectura/TicketsArquitecturaTest.java:24](../../dima-bug-backend/src/test/java/cl/casol/backend/tickets/arquitectura/TicketsArquitecturaTest.java) | Sí hay control automático específico de Tickets, no global. |
| Puertos de entrada | [dima-bug-backend/docs/arquitectura.md:40](../../dima-bug-backend/docs/arquitectura.md) | [dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java:25](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) | Tickets inyecta interfaces de caso de uso, aunque no es uniforme en el monolito. |
| Permisos | [dima-bug-backend/docs/seguridad.md:34](../../dima-bug-backend/docs/seguridad.md) | [dima-bug-backend/src/main/java/cl/casol/backend/shared/infrastructure/security/SecurityConfig.java:49](../../dima-bug-backend/src/main/java/cl/casol/backend/shared/infrastructure/security/SecurityConfig.java) | Guía antigua de dos roles frente a reglas actuales de cuatro roles y excepciones. |
| Validación de inactivo | [dima-bug-backend/docs/seguridad-integracion.md:172](../../dima-bug-backend/docs/seguridad-integracion.md) | [dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/AutenticarUsuarioService.java:36](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/AutenticarUsuarioService.java) | El código comprueba activo antes de comparar contraseña. |
| Manejo 401/403 | [dima-bug-backend/docs/seguridad-integracion.md:162](../../dima-bug-backend/docs/seguridad-integracion.md) | [dima-bug-backend/src/main/java/cl/casol/backend/shared/infrastructure/security/SecurityConfig.java:39](../../dima-bug-backend/src/main/java/cl/casol/backend/shared/infrastructure/security/SecurityConfig.java) | Se conserva sendError y existe excepción pública /error. No equivale al mecanismo documentado. |
| Snapshot de pasos | [dima-bug-backend/docs/decision-historial-procedimientos.md:8](../../dima-bug-backend/docs/decision-historial-procedimientos.md) | [dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/application/service/GestionarEjecucionService.java:64](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/application/service/GestionarEjecucionService.java) | Se consultan definiciones actuales y faltan campos de captura/migración. |
| Concurrencia de ejecución | [dima-bug-backend/docs/concurrencia-integracion.md:226](../../dima-bug-backend/docs/concurrencia-integracion.md) | [dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/persistence/repository/EjecucionJpaRepository.java:5](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/persistence/repository/EjecucionJpaRepository.java) | No hay bloqueo/versionado de cabecera en el repositorio actual. |
| Cantidad de tablas | [frontend-dimabug/docs/QA016_3_RECUPERACION_ESQUEMA_MYSQL.md:37](../../frontend-dimabug/docs/QA016_3_RECUPERACION_ESQUEMA_MYSQL.md) | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java:80](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java) | Código actual requiere 31 tablas contando Tickets y proyección JDBC. |
| Modelo anterior | [entregables/modelo-relacional/LEEME.md:3](../../entregables/modelo-relacional/LEEME.md) | [dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java:80](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java) | El modelo de 30 tablas omite la tabla que no tiene entidad JPA. |
| Responsables | [frontend-dimabug/docs/QA012_IMPLEMENTACION_DEPARTAMENTOS.md:13](../../frontend-dimabug/docs/QA012_IMPLEMENTACION_DEPARTAMENTOS.md) | [dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminResponsableController.java:11](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminResponsableController.java) | Actualmente sí existe API administrativa. La afirmación debe quedar limitada a su entrega histórica. |
| Recuperación de contraseña | [frontend-dimabug/README.md:39](../../frontend-dimabug/README.md) | [dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AuthController.java:19](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AuthController.java) | AuthController solo tiene login y me; frontend solicita endpoints ausentes. |
| CI e integración MySQL | [dima-bug-backend/docs/ci-pruebas-backend.md:28](../../dima-bug-backend/docs/ci-pruebas-backend.md) | [dima-bug-backend/pom.xml:32](../../dima-bug-backend/pom.xml) | No hay perfil integration-mysql, Failsafe, Testcontainers ni workflow/IT citadas. Ver destinos ausentes en manifiesto. |

Las ubicaciones son líneas del archivo observado, no garantías de que el contenido no cambie en futuras ediciones. El manifiesto de evidencias conserva hashes de fuentes y documentos relevantes.

## 11. Hallazgos priorizados

Se distingue severidad del hallazgo, severidad del catálogo SCA y nivel de comprobación. **CRÍTICO** se reserva para una exposición demostrada de impacto inmediato grave; **ALTO** para bloqueantes de integridad, reproducibilidad o evidencia de capacidades importantes; **MEDIO** para riesgos acotados o inconsistencias que afectan uso/mantenimiento; **BAJO** para mejoras de presentación o trazabilidad menor.

No se confirmó un incidente ni una vulnerabilidad explotada CRÍTICA de DimaBug. Sí hay cuatro entradas con severidad crítica en npm, cuya evaluación contextual se trata en H-01. No deben rebajarse ni eliminarse los avisos, pero tampoco equipararse automáticamente con un compromiso del sistema.

| ID | Severidad | Hallazgo | Evidencia / condición | Corrección propuesta, no ejecutada |
|---|---|---|---|---|
| H-01 | ALTO | Dependencias con avisos conocidos y revisión SCA Java incompleta | 54 entradas npm; cuatro críticas dev; lock y avisos conservados | Evaluar alcance real, actualizar conjunto compatible y repetir pruebas; revisar Maven sin asumir ausencia de CVE |
| H-02 | ALTO | Evidencia MySQL/CI declarada no reproducible en este checkout | Faltan IT, fixture, perfil, workflow y reportes citados | Conciliar rama/procedencia y recuperar evidencia válida; no presentar una documentación como resultado actual |
| H-03 | ALTO | Snapshot histórico afirmado sin implementación disponible | RN-PROC-01 frente a entidad, servicio y migración ausente | Resolver con equipo si se recupera implementación o se corrige el alcance documental |
| H-04 | ALTO | Provisión de BD no reproducible desde los archivos actuales | Cinco scripts parciales; falta esquema/FULLTEXT y no hay servidor verificado | Obtener DDL autorizado saneado y validar en instancia aislada |
| H-05 | ALTO | Carrera potencial cierre/checklist y discrepancia con una corrección documentada | Lectura normal de cabecera, sin @Version ni bloqueo en repositorio de ejecución | Reproducir intercalado determinista en entorno aislado y decidir corrección bajo alcance autorizado |
| H-06 | ALTO | Tickets usa MAX+1 y escrituras sin control de edición concurrente identificado | Query de correlativo, servicio transaccional, entidad sin versión | Diseñar asignación atómica/idempotencia y control de conflictos; validar primero en MySQL aislado |
| H-07 | ALTO | Script de adjuntos contiene DROP de una tabla potencialmente existente | V20261017_02, línea 7 | Separar script de ensayo de migración de conservación; verificar contenido/destino antes de aprobación DBA |
| H-08 | ALTO | Configuración y guía de arranque difieren | `application.properties` ausente y aún ignorado; solo perfil staging con mapeos; guía dice compartido empaquetado | Revisar contrato de configuración y crear guía reproducible con placeholders tras autorización |
| H-09 | MEDIO | Orden de login permite distinguir cuenta inactiva sin comprobar contraseña | AutenticarUsuarioService y handler, contradicen corrección descrita | Revisar respuesta y coste temporal bajo pruebas de seguridad controladas |
| H-10 | MEDIO | Modelo relacional previo incompleto y leyenda FK ambigua | Falta tabla JDBC; etiqueta combina JPA y FK SQL; sin opcionalidad | Revisar Draw.io/PNG y separar tres orígenes de relación |
| H-11 | MEDIO | Documentación de permisos y arquitectura quedó atrasada | Cuatro roles y ArchUnit/Tickets no reflejados en guías | Actualizar desde contrato actual, conservando historia |
| H-12 | MEDIO | Reportes y Nuevo error pueden confundirse con datos/entrega persistida | Serie literal y guardado local con mensaje de soporte | Aclarar estado funcional y criterio de aceptación, sin inventar backend |
| H-13 | MEDIO | Recuperación de contraseña ofrece recorrido sin endpoints implementados | Frontend/README frente a AuthController | Marcar pendiente e informar comportamiento al usuario |
| H-14 | MEDIO | Cachés frontend no tienen aislamiento uniforme entre cuentas | Usuarios/favoritos globales; logout solo elimina auth | Definir política de privacidad/frescura y probar cambio de sesión sin borrado indiscriminado |
| H-15 | MEDIO | Nueva asignación con solo responsable puede omitir estado del departamento | Rama de validarDestinatarios y repositorio de responsable | Revalidar decisión pendiente y coordinación concurrente; no alterar regla sin acuerdo |
| H-16 | MEDIO | Consistencia BD/archivos e índice depende de recuperación manual | Compensación, AFTER_COMMIT sin cola, limpieza Tickets que ignora I/O | Plan de reconciliación, fallos controlados y aceptación formal con responsable/vigencia |
| H-17 | MEDIO | Evidencia académica incompleta | Falta rúbrica, requisitos aprobados, manuales, UAT y trazabilidad consolidada | Reunir/reutilizar documentos existentes antes de crear los necesarios |
| H-18 | MEDIO | Caché de autenticación y logs necesitan contrato operativo | Hasta cinco minutos, aliases y cambios externos no ensayados; DEBUG contiene email | Probar revocación/cambio de rol y política de logging, sin asumir DEBUG activo en producción |
| H-19 | BAJO | Enlaces rotos y README mínimo dificultan consulta | 22 referencias locales ausentes; raíz de tres líneas | Crear índice y corregir enlaces después de autorizar cambios documentales |
| H-20 | BAJO | Configuración Karma omite assets usados por los tests | Suite pasa con 404 de `assets/img/logo_dimabug.png` | Evaluar ajuste de assets del runner en una tarea de mantenimiento |

Los H-05 y H-06 son riesgos deducidos del código, no fallos físicos reproducidos en esta auditoría. H-09 verifica la secuencia del programa y sus contratos locales, sin efectuar enumeración de cuentas reales. No se da por resuelto un hallazgo solo porque las pruebas secuenciales actuales pasen.

## 12. Pruebas y comprobaciones de esta auditoría

| Comprobación | Resultado observado | Qué acredita | Qué no acredita |
|---|---|---|---|
| `java -version` y `mvnw.cmd -o -version` | Java 21.0.12.1, Maven 3.9.16 | Herramientas de la sesión | Versiones del servidor desplegado |
| `node node_modules/typescript/bin/tsc --noEmit -p tsconfig.app.json` | Salida 0 | Tipado/compilación TypeScript aplicación | Render completo, MySQL o comportamiento E2E |
| `node node_modules/typescript/bin/tsc --noEmit -p tsconfig.spec.json` | Salida 0 | Tipado/compilación de specs | Que cada escenario haya corrido |
| `mvnw.cmd -o test` | 920 / 0 / 0 / 0, BUILD SUCCESS | Suite backend local, mocks/MVC, ArchUnit y H2 definidos por sus fuentes | MySQL, InnoDB, FULLTEXT, HTTP real completo, STAGING |
| `npm.cmd test -- --watch=false --browsers=ChromeHeadless` | 284 SUCCESS, salida 0 | Specs Angular en Chrome Headless 157 | UAT, accesibilidad completa o integración con backend real |
| `npm.cmd audit --json --ignore-scripts` | 54 entradas afectadas, sin instalar ni corregir | Coincidencia con catálogo npm del momento | Explotabilidad real o ausencia de riesgos no catalogados |
| Hashes y enlaces | Sin duplicados exactos; destinos rotos inventariados | Estado documental local | Validez de actas externas o links privados |

Los primeros intentos de Maven y Chrome dentro del sandbox se interrumpieron tras problemas del agente JVM/lanzador de navegador. No produjeron una suite completa y no cuentan como PASS ni como defecto del producto. Se repitieron los mismos comandos fuera del sandbox mediante la revisión de permisos disponible y terminaron correctamente.

Maven funcionó en modo offline con dependencias ya existentes; no se descargaron ni instalaron paquetes. El comando informó clases al día: se ejecutó la suite sobre el target incremental existente, **no se certifica una compilación limpia desde un checkout vacío**. Se evitó limpiar el trabajo del usuario. Las pruebas revisadas no usan MySQL externo; los dos DataSource de integración presentes usan H2 en memoria. Surefire generó reportes nuevos en `target/surefire-reports`.

La salida contiene warnings esperables de Mockito/autocarga y escenarios con errores simulados que las pruebas afirman. No se contabilizaron esas trazas como fallos cuando el XML/resultado final indica éxito. Angular informó 404 de un logo en Karma sin fallos de tests. No se ejecutó build de producción, empaquetado JAR, cobertura JaCoCo/Istanbul, carga, pentest, UAT ni despliegue nuevo.

Los resultados anteriores de QA009–QA016 y Skill 4 se mantienen como historia documental. Sus conteos no se suman a los 920/284 ni prueban el estado de los archivos que faltan.

## 13. Plan de corrección para revisión

| Prioridad | Trabajo propuesto | Responsable sugerido, sin designación formal | Evidencia de cierre | Dependencia / autorización |
|---|---|---|---|---|
| 1 | Conciliar rama, documentos y capacidades de snapshot/CI/MySQL | Arquitectura y documentación | Cada afirmación enlaza a archivo actual o evidencia externa identificada | Autorización para corregir documentación; no mover originales todavía |
| 1 | Evaluar avisos npm y transitivas Maven | Seguridad y desarrollo | Alcance por aviso, plan compatible, SCA actualizado y regresión | Actualización/instalación de dependencias en tarea aparte autorizada |
| 1 | Obtener DDL completo saneado y plan seguro de adjuntos | DBA | Estructura, índices, FK, procedencia, checksum y revisión de DROP | Permiso separado para acceso/exportación/SQL; no incluido en esta tarea |
| 1 | Validar carreras de ejecución y Tickets | Backend y QA | Ensayos deterministas de lectura obsoleta, correlativo y escritura simultánea | Entorno aislado y posterior aprobación de cambio funcional si corresponde |
| 2 | Corregir descripción de configuración, puertos y permisos | Backend y documentación | Arranque reproducible con placeholders, matriz por método y perfiles | Revisión documental autorizada, sin usar credenciales reales |
| 2 | Corregir alcance del modelo Draw.io/PNG | Datos y documentación | 31 tablas lógicas, tabla JDBC, relaciones con origen y opcionalidad | Edición del entregable previa autorización |
| 2 | Definir estado de recuperación, reportes, avisos y Nuevo error | Producto y QA | Criterios aprobados por función y mensajes consistentes | Decisión funcional antes de cualquier programación |
| 2 | Acordar cachés, archivos, índices y logging | Seguridad y operaciones | Casos de cambio de cuenta/rol, reconciliación, recuperación y aceptación trazable | No purgar datos ni tocar infraestructura sin alcance específico |
| 2 | Completar requisitos y manuales reutilizando QA/ADR | Equipo académico | RF/RNF/HU, escenarios, manuales y matriz de evidencias coherentes | Fases 5–7 solo después de autorización |
| 3 | Revisar rutas de enlaces y configuración de assets de tests | Documentación y frontend | Enlaces válidos y ausencia de 404 del runner | Mantenimiento acotado aprobado |

No se promete duración ni se asigna aceptación a personas no identificadas. El plan no autoriza migraciones, despliegues, commits, push ni cambios de lógica/endpoints/esquema. Cada actividad técnica fuera de documentación necesita un alcance posterior claro.

## 14. Registro de cambios y condición de parada

Se crearon exclusivamente estos entregables documentales de auditoría:

1. `docs/auditoria/AUDITORIA_DOCUMENTAL.md`.
2. `docs/auditoria/evidencias/DEPENDENCIAS_NPM.json`: respuesta pública saneada de paquetes y avisos.
3. `docs/auditoria/evidencias/COMPROBACIONES.json`: resumen de pruebas, inventario, versiones, hashes y referencias rotas, sin stdout de pruebas ni secretos.

Las herramientas de pruebas generaron/actualizaron `dima-bug-backend/target/` y caché temporal Angular. No son documentos originales ni evidencia de un nuevo despliegue. No se movieron o borraron documentos, no se regeneró el modelo/PPT y no se modificaron fuentes, SQL, properties, variables o dependencias.

La comparación SHA-256 de los **811 archivos originales** inventariados confirma **cero cambios y cero eliminaciones**. Los cambios previos en `application-staging.properties`, las guías backend sin seguimiento y los entregables anteriores se conservaron. La verificación excluye cachés, dependencias, target, Git y directorios privados de construcción, cuyo estado no se presenta como documentación fuente.

**Parada de fase 4:** informe y plan entregados para revisión. No se inicia organización documental, documentación complementaria, carpeta de entrega académica ni reporte final de fases 5–8 hasta recibir autorización expresa de la usuaria. La solicitud original exige: “DETENTE AL FINALIZAR ESTA FASE” y “Solicita autorización antes de modificar o reorganizar la documentación”.

## Anexo A. Rutas extraídas de controladores

La tabla combina el prefijo de clase con el mapping de método y conserva archivo/línea. La extracción no inventa responses ni valida rutas de despliegue. Los parámetros query, DTO y variantes de permisos se deben detallar en una futura referencia API autorizada.

| Método | Ruta declarada | Controlador y línea |
|---|---|---|
| GET | `/api/admin/catalogos/{tipo}` | [AdminCatalogoController.java:14](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/AdminCatalogoController.java) |
| GET | `/api/admin/catalogos/{tipo}/{id}` | [AdminCatalogoController.java:17](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/AdminCatalogoController.java) |
| POST | `/api/admin/catalogos/{tipo}` | [AdminCatalogoController.java:20](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/AdminCatalogoController.java) |
| PUT | `/api/admin/catalogos/{tipo}/{id}` | [AdminCatalogoController.java:24](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/AdminCatalogoController.java) |
| PATCH | `/api/admin/catalogos/{tipo}/{id}/estado` | [AdminCatalogoController.java:27](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/AdminCatalogoController.java) |
| GET | `/api/frecuencias` | [FrecuenciaController.java:17](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/FrecuenciaController.java) |
| GET | `/api/hardware` | [HardwareController.java:16](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/HardwareController.java) |
| GET | `/api/hardware/{id}` | [HardwareController.java:21](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/HardwareController.java) |
| GET | `/api/hardware/{id}/sistemas` | [HardwareController.java:24](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/HardwareController.java) |
| GET | `/api/sistemas` | [SistemaController.java:17](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/SistemaController.java) |
| GET | `/api/sistemas/{id}` | [SistemaController.java:22](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/SistemaController.java) |
| GET | `/api/sistemas/{id}/modulos` | [SistemaController.java:25](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/SistemaController.java) |
| GET | `/api/sistemas/{id}/hardware` | [SistemaController.java:30](../../dima-bug-backend/src/main/java/cl/casol/backend/catalogo/infrastructure/web/SistemaController.java) |
| GET | `/api/admin/pruebas` | [AdminPruebaController.java:14](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminPruebaController.java) |
| GET | `/api/admin/pruebas/{id}` | [AdminPruebaController.java:15](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminPruebaController.java) |
| POST | `/api/admin/pruebas` | [AdminPruebaController.java:16](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminPruebaController.java) |
| PUT | `/api/admin/pruebas/{id}` | [AdminPruebaController.java:17](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminPruebaController.java) |
| PATCH | `/api/admin/pruebas/{id}/estado` | [AdminPruebaController.java:18](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminPruebaController.java) |
| GET | `/api/admin/conocimientos/{conocimientoId}/soluciones` | [AdminSolucionController.java:13](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminSolucionController.java) |
| PATCH | `/api/admin/conocimientos/{conocimientoId}/soluciones/{solucionId}/estado` | [AdminSolucionController.java:17](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminSolucionController.java) |
| PATCH | `/api/admin/conocimientos/{conocimientoId}/soluciones/{solucionId}/autoservicio` | [AdminSolucionController.java:22](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminSolucionController.java) |
| POST | `/api/admin/conocimientos/{conocimientoId}/soluciones/copias` | [AdminSolucionCopiaController.java:12](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/AdminSolucionCopiaController.java) |
| GET | `/api/conocimientos/{conocimientoId}/causas` | [CausaController.java:20](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/CausaController.java) |
| POST | `/api/conocimientos/{conocimientoId}/causas` | [CausaController.java:25](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/CausaController.java) |
| PUT | `/api/conocimientos/{conocimientoId}/causas/{causaId}` | [CausaController.java:32](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/CausaController.java) |
| POST | `/api/chatbot/consultas` | [ChatbotController.java:15](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ChatbotController.java) |
| GET | `/api/conocimientos/buscar` | [ConocimientoController.java:29](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoController.java) |
| GET | `/api/conocimientos` | [ConocimientoController.java:40](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoController.java) |
| GET | `/api/conocimientos/{id}` | [ConocimientoController.java:49](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoController.java) |
| POST | `/api/conocimientos` | [ConocimientoController.java:58](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoController.java) |
| PUT | `/api/conocimientos/{id}` | [ConocimientoController.java:67](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoController.java) |
| PATCH | `/api/conocimientos/{id}/estado` | [ConocimientoController.java:75](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoController.java) |
| DELETE | `/api/conocimientos/{id}` | [ConocimientoController.java:88](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoController.java) |
| GET | `/api/conocimientos/{conocimientoId}/pruebas` | [ConocimientoPruebaController.java:17](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoPruebaController.java) |
| POST | `/api/conocimientos/{conocimientoId}/pruebas` | [ConocimientoPruebaController.java:22](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoPruebaController.java) |
| PUT | `/api/conocimientos/{conocimientoId}/pruebas/{pruebaId}` | [ConocimientoPruebaController.java:29](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ConocimientoPruebaController.java) |
| GET | `/api/conocimientos/{conocimientoId}/materiales` | [MaterialApoyoController.java:23](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/MaterialApoyoController.java) |
| POST | `/api/conocimientos/{conocimientoId}/materiales` | [MaterialApoyoController.java:28](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/MaterialApoyoController.java) |
| PUT | `/api/conocimientos/{conocimientoId}/materiales/{materialId}` | [MaterialApoyoController.java:37](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/MaterialApoyoController.java) |
| DELETE | `/api/conocimientos/{conocimientoId}/materiales/{materialId}` | [MaterialApoyoController.java:45](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/MaterialApoyoController.java) |
| POST | `/api/conocimientos/{conocimientoId}/materiales/archivo` | [MaterialConocimientoArchivoController.java:16](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/MaterialConocimientoArchivoController.java) |
| GET | `/api/conocimientos/{conocimientoId}/materiales/{materialId}/archivo` | [MaterialConocimientoArchivoController.java:22](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/MaterialConocimientoArchivoController.java) |
| GET | `/api/pruebas` | [PruebaController.java:15](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/PruebaController.java) |
| POST | `/api/conocimientos/{conocimientoId}/materiales/reutilizaciones` | [ReutilizarMaterialController.java:12](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/ReutilizarMaterialController.java) |
| GET | `/api/conocimientos/{conocimientoId}/sintomas` | [SintomaController.java:20](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SintomaController.java) |
| POST | `/api/conocimientos/{conocimientoId}/sintomas` | [SintomaController.java:25](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SintomaController.java) |
| PUT | `/api/conocimientos/{conocimientoId}/sintomas/{sintomaId}` | [SintomaController.java:32](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SintomaController.java) |
| GET | `/api/conocimientos/{conocimientoId}/soluciones/{solucionId}/asignaciones` | [SolucionAsignacionController.java:15](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SolucionAsignacionController.java) |
| POST | `/api/conocimientos/{conocimientoId}/soluciones/{solucionId}/asignaciones` | [SolucionAsignacionController.java:19](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SolucionAsignacionController.java) |
| PUT | `/api/conocimientos/{conocimientoId}/soluciones/{solucionId}/asignaciones/{asignacionId}` | [SolucionAsignacionController.java:25](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SolucionAsignacionController.java) |
| GET | `/api/conocimientos/{conocimientoId}/soluciones` | [SolucionController.java:15](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SolucionController.java) |
| POST | `/api/conocimientos/{conocimientoId}/soluciones` | [SolucionController.java:18](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SolucionController.java) |
| PUT | `/api/conocimientos/{conocimientoId}/soluciones/{solucionId}` | [SolucionController.java:23](../../dima-bug-backend/src/main/java/cl/casol/backend/conocimiento/infrastructure/web/SolucionController.java) |
| POST | `/api/procedimientos/{procedimientoId}/ejecuciones` | [EjecucionController.java:14](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/web/EjecucionController.java) |
| GET | `/api/ejecuciones/{id}` | [EjecucionController.java:17](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/web/EjecucionController.java) |
| GET | `/api/ejecuciones` | [EjecucionController.java:19](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/web/EjecucionController.java) |
| GET | `/api/ejecuciones/{id}/pasos` | [EjecucionController.java:21](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/web/EjecucionController.java) |
| PATCH | `/api/ejecuciones/{id}/pasos/{ejecucionPasoId}` | [EjecucionController.java:23](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/web/EjecucionController.java) |
| PATCH | `/api/ejecuciones/{id}/completar` | [EjecucionController.java:28](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/web/EjecucionController.java) |
| PATCH | `/api/ejecuciones/{id}/cancelar` | [EjecucionController.java:30](../../dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/web/EjecucionController.java) |
| GET | `/api/admin/departamentos/{departamentoId}/contactos` | [AdminDepartamentoContactoController.java:13](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoContactoController.java) |
| GET | `/api/admin/departamentos/{departamentoId}/contactos/{id}` | [AdminDepartamentoContactoController.java:17](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoContactoController.java) |
| POST | `/api/admin/departamentos/{departamentoId}/contactos` | [AdminDepartamentoContactoController.java:20](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoContactoController.java) |
| PUT | `/api/admin/departamentos/{departamentoId}/contactos/{id}` | [AdminDepartamentoContactoController.java:25](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoContactoController.java) |
| PATCH | `/api/admin/departamentos/{departamentoId}/contactos/{id}/estado` | [AdminDepartamentoContactoController.java:28](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoContactoController.java) |
| GET | `/api/admin/departamentos` | [AdminDepartamentoController.java:14](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoController.java) |
| GET | `/api/admin/departamentos/{id}` | [AdminDepartamentoController.java:15](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoController.java) |
| POST | `/api/admin/departamentos` | [AdminDepartamentoController.java:16](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoController.java) |
| PUT | `/api/admin/departamentos/{id}` | [AdminDepartamentoController.java:19](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoController.java) |
| PATCH | `/api/admin/departamentos/{id}/estado` | [AdminDepartamentoController.java:22](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminDepartamentoController.java) |
| GET | `/api/admin/responsables` | [AdminResponsableController.java:15](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminResponsableController.java) |
| GET | `/api/admin/responsables/{id}` | [AdminResponsableController.java:16](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminResponsableController.java) |
| POST | `/api/admin/responsables` | [AdminResponsableController.java:17](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminResponsableController.java) |
| PUT | `/api/admin/responsables/{id}` | [AdminResponsableController.java:21](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminResponsableController.java) |
| PATCH | `/api/admin/responsables/{id}/estado` | [AdminResponsableController.java:24](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AdminResponsableController.java) |
| POST | `/api/auth/login` | [AuthController.java:33](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AuthController.java) |
| GET | `/api/auth/me` | [AuthController.java:56](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/AuthController.java) |
| GET | `/api/departamentos` | [DepartamentoController.java:16](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/DepartamentoController.java) |
| GET | `/api/departamentos/{id}/responsables` | [DepartamentoController.java:21](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/DepartamentoController.java) |
| GET | `/api/departamentos/{id}/contactos` | [DepartamentoController.java:26](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/DepartamentoController.java) |
| GET | `/api/roles` | [RolController.java:21](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/RolController.java) |
| GET | `/api/usuarios` | [UsuarioController.java:35](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/UsuarioController.java) |
| GET | `/api/usuarios/{id}` | [UsuarioController.java:40](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/UsuarioController.java) |
| POST | `/api/usuarios` | [UsuarioController.java:45](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/UsuarioController.java) |
| PUT | `/api/usuarios/{id}` | [UsuarioController.java:53](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/UsuarioController.java) |
| PATCH | `/api/usuarios/{id}/password` | [UsuarioController.java:61](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/UsuarioController.java) |
| PATCH | `/api/usuarios/{id}/estado` | [UsuarioController.java:67](../../dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/web/UsuarioController.java) |
| POST | `/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales/archivo` | [MaterialPasoArchivoController.java:16](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/MaterialPasoArchivoController.java) |
| GET | `/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales/{materialId}/archivo` | [MaterialPasoArchivoController.java:22](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/MaterialPasoArchivoController.java) |
| GET | `/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales` | [MaterialPasoController.java:20](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/MaterialPasoController.java) |
| POST | `/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales` | [MaterialPasoController.java:21](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/MaterialPasoController.java) |
| PUT | `/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales/{materialId}` | [MaterialPasoController.java:23](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/MaterialPasoController.java) |
| DELETE | `/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales/{materialId}` | [MaterialPasoController.java:24](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/MaterialPasoController.java) |
| GET | `/api/procedimientos/{procedimientoId}/pasos` | [PasoController.java:15](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/PasoController.java) |
| POST | `/api/procedimientos/{procedimientoId}/pasos` | [PasoController.java:16](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/PasoController.java) |
| PUT | `/api/procedimientos/{procedimientoId}/pasos/{pasoId}` | [PasoController.java:18](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/PasoController.java) |
| GET | `/api/procedimientos` | [ProcedimientoController.java:20](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/ProcedimientoController.java) |
| GET | `/api/procedimientos/{id}` | [ProcedimientoController.java:21](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/ProcedimientoController.java) |
| POST | `/api/procedimientos` | [ProcedimientoController.java:22](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/ProcedimientoController.java) |
| PUT | `/api/procedimientos/{id}` | [ProcedimientoController.java:24](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/ProcedimientoController.java) |
| PATCH | `/api/procedimientos/{id}/estado` | [ProcedimientoController.java:25](../../dima-bug-backend/src/main/java/cl/casol/backend/procedimiento/infrastructure/web/ProcedimientoController.java) |
| POST | `/api/conocimientos/{conocimientoId}/soluciones/{solucionId}/resultados` | [ResultadoSolucionController.java:16](../../dima-bug-backend/src/main/java/cl/casol/backend/seguimiento/infrastructure/web/ResultadoSolucionController.java) |
| GET | `/api/conocimientos/{conocimientoId}/soluciones/{solucionId}/resultados` | [ResultadoSolucionController.java:21](../../dima-bug-backend/src/main/java/cl/casol/backend/seguimiento/infrastructure/web/ResultadoSolucionController.java) |
| GET | `/api/conocimientos/{conocimientoId}/soluciones/{solucionId}/efectividad` | [ResultadoSolucionController.java:24](../../dima-bug-backend/src/main/java/cl/casol/backend/seguimiento/infrastructure/web/ResultadoSolucionController.java) |
| GET | `/api/health` | [HealthController.java:13](../../dima-bug-backend/src/main/java/cl/casol/backend/shared/infrastructure/web/HealthController.java) |
| GET | `/` | [RootController.java:11](../../dima-bug-backend/src/main/java/cl/casol/backend/shared/infrastructure/web/RootController.java) |
| POST | `/api/tickets/{ticketId}/adjuntos` | [AdjuntoTicketController.java:29](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/AdjuntoTicketController.java) |
| GET | `/api/tickets/{ticketId}/adjuntos/{adjuntoId}` | [AdjuntoTicketController.java:43](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/AdjuntoTicketController.java) |
| GET | `/api/etiquetas` | [EtiquetaController.java:24](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/EtiquetaController.java) |
| POST | `/api/etiquetas` | [EtiquetaController.java:31](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/EtiquetaController.java) |
| PUT | `/api/etiquetas/{id}` | [EtiquetaController.java:37](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/EtiquetaController.java) |
| PATCH | `/api/etiquetas/{id}/estado` | [EtiquetaController.java:43](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/EtiquetaController.java) |
| PATCH | `/api/tickets/{id}/prioridad` | [PrioridadTicketController.java:19](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/PrioridadTicketController.java) |
| POST | `/api/tickets` | [TicketController.java:45](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| GET | `/api/tickets` | [TicketController.java:53](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| GET | `/api/tickets/conteo` | [TicketController.java:69](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| GET | `/api/tickets/sugerencias` | [TicketController.java:74](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| GET | `/api/tickets/{id}` | [TicketController.java:79](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| PATCH | `/api/tickets/{id}/asignacion` | [TicketController.java:84](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| PATCH | `/api/tickets/{id}/estado` | [TicketController.java:90](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| PATCH | `/api/tickets/{id}/resolucion` | [TicketController.java:97](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| POST | `/api/tickets/{id}/conocimiento` | [TicketController.java:104](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| POST | `/api/tickets/{id}/comentarios` | [TicketController.java:110](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |
| PUT | `/api/tickets/{id}/etiquetas` | [TicketController.java:118](../../dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/TicketController.java) |

## Anexo B. Restricciones SQL explícitas presentes

Solo se enumeran declaraciones FOREIGN KEY leídas de los cinco scripts disponibles; no son metadatos del servidor. El número de relaciones JPA o escalares no debe sumarse como FK físicas.

| Fuente y línea | Declaración SQL |
|---|---|
| [V20261017_01__modulo_tickets.sql:48](../../dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql) | `CONSTRAINT fk_tte_ticket   FOREIGN KEY (ticket_id)   REFERENCES ticket (id) ON DELETE CASCADE,` |
| [V20261017_01__modulo_tickets.sql:49](../../dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql) | `CONSTRAINT fk_tte_etiqueta FOREIGN KEY (etiqueta_id) REFERENCES ticket_etiqueta (id)` |
| [V20261017_01__modulo_tickets.sql:61](../../dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql) | `CONSTRAINT fk_ticket_comentario_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE` |
| [V20261017_01__modulo_tickets.sql:74](../../dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql) | `CONSTRAINT fk_ticket_historial_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE` |
| [V20261017_02__tickets_adjuntos_y_nivel_etiqueta.sql:21](../../dima-bug-backend/src/main/resources/db/migration/V20261017_02__tickets_adjuntos_y_nivel_etiqueta.sql) | `CONSTRAINT fk_ticket_adjunto_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE,` |
| [V20261017_02__tickets_adjuntos_y_nivel_etiqueta.sql:22](../../dima-bug-backend/src/main/resources/db/migration/V20261017_02__tickets_adjuntos_y_nivel_etiqueta.sql) | `CONSTRAINT fk_ticket_adjunto_comentario FOREIGN KEY (comentario_id) REFERENCES ticket_comentario (id) ON DELETE CASCADE` |

## Anexo C. Fuentes y evidencias

- Las ubicaciones internas se describen en inventario, matriz, contradicciones y anexos. El manifiesto `evidencias/COMPROBACIONES.json` registra SHA-256 de los documentos originales, POM/lock/configuración no sensible y fuentes utilizadas para los hallazgos.
- Los reportes Surefire se consultaron como XML para extraer conteos, estado y hash. No se copiaron sus secciones de logging al entregable.
- [OWASP Top 10:2025](https://top10.owasp.org/2025/0x00_2025-Introduction/): taxonomía general, sin acreditar auditoría OWASP completa.
- [GHSA-hh8m-fm6v-7cvg](https://github.com/advisories/GHSA-hh8m-fm6v-7cvg), [GHSA-67c8-pqhq-4rmx](https://github.com/advisories/GHSA-67c8-pqhq-4rmx) y [GHSA-pqg4-j6r4-53mv](https://github.com/advisories/GHSA-pqg4-j6r4-53mv): avisos contrastados sobre versiones afectadas. El resto de URLs públicas del scanner se conserva en DEPENDENCIAS_NPM.json.
- No se dispone de evidencia física MySQL, E2E/UAT, run de CI remoto o estado actual de producción. Todo cierre dependiente de esos elementos permanece pendiente.
