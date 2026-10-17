# QA-016.1 — Diagnóstico de Docker y preparación de MySQL 8

Fecha: 14-10-2026. Alcance: consultas locales y este informe. Referencia: `QA016_PLAN_MYSQL_AISLADO.md`. No se inició Docker Desktop, servicios o distribuciones, ni se crearon contenedores/bases. No se ejecutó SQL, instalaciones, migraciones, pruebas de aplicación, commits ni despliegues.

## 1. Dictamen y causa probable

Docker Desktop está instalado, pero no está ejecutándose en la sesión consultada. Esta es la causa probable de la ausencia de Docker Engine: no aparecen procesos Docker, `docker desktop status` devuelve «Could not retrieve status. Is Docker Desktop running?», y `docker --context desktop-linux version` devuelve únicamente el cliente y un error por ausencia de `npipe:////./pipe/dockerDesktopLinuxEngine`.

WSL2 está disponible: Ubuntu y docker-desktop están registradas como versión 2, ambas detenidas. El servicio wslservice está ejecutándose; eso no significa que una distribución o el motor Docker esté iniciado. No se ha comprobado todavía que Docker Desktop pueda arrancar satisfactoriamente. No hay evidencia suficiente para atribuir el problema a corrupción, firewall, credenciales o falta de virtualización.

Recomendación: reutilizar Docker Desktop con contenedores Linux y WSL2, previa autorización para iniciarlo. GO condicionado para ese siguiente diagnóstico de arranque; todavía no hay un MySQL 8 operativo ni un esquema base listo. Se conserva el NO-GO para STAGING.

## 2. Herramientas y recursos comprobados

| Elemento | Resultado |
| --- | --- |
| Docker CLI | 29.8.2, build 7fc2dff; API cliente 1.56 |
| Docker Desktop | Ejecutable instalado; versión de archivo/producto 4.94.0.241994 |
| Ejecutable Desktop | `C:\Users\carol\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe` |
| Contexto activo | desktop-linux, endpoint local `npipe:////./pipe/dockerDesktopLinuxEngine` |
| Otro contexto | default, endpoint `npipe:////./pipe/docker_engine`; no se cambió contexto |
| Docker Engine | No accesible; versión del servidor no obtenida |
| WSL | 2.6.3.0, kernel 6.6.87.2-1; versión predeterminada 2 |
| Distribuciones | Ubuntu: Stopped, versión 2; docker-desktop: Stopped, versión 2 |
| Windows | Windows 11 Home Single Language, 64 bits, versión 10.0.26200; WSL informa build 26200.9457 |
| Virtualización | HypervisorPresent=true; VirtualMachinePlatform habilitada (InstallState=1) |
| Característica WSL clásica | Microsoft-Windows-Subsystem-Linux deshabilitada (InstallState=2); WSL avisa sobre WSL1, no sobre la ausencia de WSL2 |
| Memoria | Aproximadamente 31,5 GiB físicos; 19,4 GiB disponibles durante la consulta |
| CPU | 10 procesadores lógicos |
| Disco C: | Aproximadamente 315,3 GiB libres; no se midió capacidad interna del disco virtual Docker |
| Servicio Windows Server | LanmanServer Running, inicio Auto |
| Servicios MySQL/MariaDB/Docker | Sin coincidencias en la consulta de servicios; no prueba ausencia de instalaciones portables |
| MySQL Shell | 26.7.0, en `C:\Program Files\MySQL\MySQL Shell 26.7\bin\mysqlsh.exe`; es cliente, no confirma servidor MySQL 8 |
| Herramientas tradicionales | mysql, mysqld, mysqldump y mysqladmin no localizados en PATH |
| Carpeta Program Files/MySQL | Solo se encontró MySQL Shell 26.7 |
| Puertos consultados | Sin listeners 3306, 3307, 33060, 13316 ni 19090 en ese momento; no quedan reservados |

La documentación oficial requiere WSL 2.1.5 o posterior y al menos 8 GB RAM para el backend WSL2. Windows Home admite contenedores Linux. La instalación por usuario no requiere el servicio privilegiado de la instalación para todos los usuarios; por tanto, la ausencia de un servicio Docker no prueba una instalación defectuosa. Los recursos observados permiten intentar el arranque, pero la validación definitiva requiere que el Engine responda. Fuentes: [requisitos e instalación Windows](https://docs.docker.com/desktop/setup/install/windows-install/) y [backend WSL2](https://docs.docker.com/desktop/features/wsl/).

## 3. Alternativas

| Alternativa | Evaluación |
| --- | --- |
| Docker Desktop + MySQL 8 | Recomendada: herramientas y distribución Docker ya instaladas; instancia, volumen y puerto independientes, sin sustituir bases existentes. Falta comprobar arranque y elegir una imagen exacta. |
| MySQL Server 8 nativo | Servidor no confirmado. Probablemente requeriría instalación, configuración de servicio y almacenamiento separados; mayor intervención en Windows. No recomendar instalarlo antes de probar Desktop. |
| Máquina virtual dedicada con MySQL 8 | Aislamiento claro si Desktop resulta inviable, pero exige sistema invitado, instalación y recursos adicionales. No reutilizar una base o distribución con datos existentes para ahorrar pasos. |

## 4. Pasos propuestos — no ejecutados

Todos se realizan en el PC. No hay pasos en servidores STAGING/PRODUCCIÓN.

1. Autorizar por separado el inicio de Docker Desktop existente. Abrirlo manualmente desde Inicio y esperar el estado de Engine preparado. No reinstalar, actualizar ni cambiar características Windows como primer intento.
2. Confirmar en la interfaz que se utiliza el backend WSL2 y contenedores Linux. Si la configuración requiere cambios o el arranque falla, registrar el error exacto y diagnosticarlo antes de modificarla. No es necesario iniciar Ubuntu para estas consultas.
3. Ejecutar las consultas siguientes y exigir que `docker version` muestre también Server y que `docker info` termine correctamente:

```powershell
docker desktop status
docker context ls
docker --context desktop-linux version
docker --context desktop-linux info
wsl --status
wsl --list --verbose
```

4. Una vez disponible el Engine, inventariar recursos existentes mediante consultas, antes de autorizar su creación:

```powershell
docker --context desktop-linux ps -a
docker --context desktop-linux volume ls
docker --context desktop-linux image ls
Get-NetTCPConnection -State Listen |
    Where-Object { $_.LocalPort -in @(13316, 19090) } |
    Select-Object LocalAddress, LocalPort
```

5. Confirmar una versión exacta MySQL 8 y fijar etiqueta/digest. Diseñar un contenedor exclusivo `dimabug-qa016-mysql`, volumen exclusivo `dimabug-qa016-mysql-data` y publicación únicamente en `127.0.0.1:13316`. Estos nombres no han sido verificados contra el inventario Docker porque el Engine no responde. Usar secretos nuevos fuera de Git y almacenamiento fuera de OneDrive; no reutilizar credenciales ni uploads reales.
6. Obtener o preparar un DDL base revisado. Preferir estructura sin datos personales y datos sintéticos; cualquier exportación de un entorno existente requiere autorización independiente. No usar generación JPA como evidencia de equivalencia con las restricciones físicas reales.
7. Solo con autorización posterior, crear la instancia y consultar primero si existe `dima_bug_qa_integracion`. Su ausencia aún no está confirmada; si existe, detenerse y elegir otro nombre, sin borrarla. Restauración, creación y migraciones permanecen pendientes.
8. Seguir QA-016 para validar estructura, migraciones y Backend con `ddl-auto=validate`, sin inicialización SQL automática. Reservar inicialmente, como propuesta y no requisito de Docker, unos 4 GiB para MySQL y espacio adicional para esquema, logs y respaldo; ajustar mediante observación. Mantener el Backend QA en loopback:19090 con uploads independientes.

No se incluyen comandos de creación o arranque automático en este diagnóstico. Si Desktop falla, el siguiente insumo es su mensaje de error y la salida de las consultas anteriores; recopilar logs selectivamente sin publicar secretos.

## 5. Esquema base y SQL localizado

Se buscaron archivos SQL, DDL, schema, dumps, backups, Compose y Dockerfile en ambos proyectos bajo `C:\Users\carol\OneDrive\Desktop\cata-front\buscador-errores`. La carpeta cata-front contiene ese proyecto. La consulta de nombres SQL en el historial Git no devolvió archivos adicionales. No se encontró DDL completo, exportación de estructura, dump ni migración inicial que cree todas las tablas. Esto describe el ámbito revisado, no demuestra que no exista un respaldo en otra ubicación.

Directorio de los tres archivos encontrados:

`C:\Users\carol\OneDrive\Desktop\cata-front\buscador-errores\dima-bug-backend\src\main\resources\db\migration`

| Archivo | Efecto previsto | Dependencia |
| --- | --- | --- |
| V20260925_01__agregar_estado_eliminado_conocimiento.sql | Amplía el ENUM conocimiento_estado con ELIMINADO | co_conocimiento ya debe existir; comprobar si ya fue aplicado |
| V20261013_01__ampliar_solucion.sql | Agrega título nullable, estado activo por defecto y autoservicio deshabilitado | co_solucion existente; columnas ausentes; soporte ALTER INSTANT |
| V20261014_01__organizacion_persistente.sql | Agrega versiones a departamento/contacto y crea u_organizacion_control con fila mutex | Ambas tablas existentes; columnas/tabla ausentes; soporte INSTANT |

Ninguno reconstruye el esquema completo. Orden futuro: esquema base revisado → comprobación de migración de estado de conocimiento → QA-009 → QA-012 → validación JPA y pruebas. QA-009 y QA-012 afectan tablas distintas, pero deben registrarse individualmente. No repetir scripts ciegamente: no son idempotentes. QA-012 limita espera de metadata lock a 5 segundos; sus varias sentencias pueden quedar parcialmente aplicadas. INSTANT no elimina todo riesgo de espera por bloqueos. No sustituir automáticamente por COPY/INPLACE. No se verificaron físicamente claves foráneas, índices o duplicados MySQL.

## 6. Riesgos y decisiones pendientes

- Arranque real del Engine pendiente: el estado detenido explica el error observado, pero podría aparecer otro problema al abrir Desktop. No se diagnostica una avería adicional sin evidencia.
- Falta el DDL base: bloquea reconstrucción fiel y validación de migraciones. Decidir su fuente y revisión antes de ejecutar SQL.
- Imagen exacta, recursos internos WSL, nombres y volumen Docker por confirmar. Los puertos libres son una observación puntual.
- MySQL nativo no confirmado; MySQL Shell no lo sustituye. No se conectó a ningún servidor para comprobar bases existentes.
- Credenciales nuevas, respaldo y persistencia del volumen deben definirse antes de crear recursos. No copiar datos personales ni importar automáticamente almacenamiento local Angular.
- El ajuste de asignaciones por responsable de departamento inactivo sigue pendiente por decisión funcional previa de QA-012; no se modifica aquí.
- Migraciones QA-009/QA-012 y pruebas con MySQL real siguen sin ejecutar. Este diagnóstico no cambia el NO-GO para STAGING.

## 7. Preservación y cierre

Se consultó Git en la rama catalina y se confirmó que existen numerosos cambios locales anteriores de Backend, Angular y documentación. No se realizaron operaciones que los alteren. La única escritura de esta actividad es este informe. QA-009 a QA-014 se mantienen sin cambios de código y no se reejecutaron sus pruebas.

Pendiente de autorización: iniciar Desktop y confirmar Engine; posteriormente, aprobar por separado la creación del entorno y la ejecución SQL según QA-016. Se detiene QA-016.1 en este punto.
