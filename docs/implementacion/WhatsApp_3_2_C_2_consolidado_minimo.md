# WhatsApp QA — Consolidado y pruebas mínimas pendientes

Fecha: 15 de noviembre de 2026. Complementa C.1 y el informe de evidencias
pendientes C.2; conserva ambos como antecedentes. Incluye las salidas filtradas
que el operador compartió en el chat. No supone una nueva inspección remota.
No se modificaron servicios, infraestructura, certificados, bases ni plantillas.

## Decisión actual

**Proponer Snap como administrador único futuro de Certbot. No ejecutar todavía
el relevo ni desplegar WhatsApp QA.** Hay suficiente inventario para dejar de
pedir comandos repetitivos; la aprobación requiere las cinco pruebas siguientes.
La falta de historial completo no se resuelve repitiendo búsquedas indefinidas:
se conserva como limitación y se exige validación prospectiva controlada.

## Evidencias disponibles

| Área | Evidencia y alcance |
|---|---|
| Instalaciones | APT 2.9.0 en `/usr/bin/certbot`; Snap 5.10.0; `/usr/local/bin/certbot` apunta a Snap. Son dos instalaciones, no tres. |
| Programación | Ambos timers habilitados. Los servicios invocan sus respectivos ejecutables. Las ejecuciones del 15/11 revisaron los mismos tres certificados y no renovaron porque no correspondía. |
| Configuración | Los tres certificados usan autenticador e instalador nginx. Configuración guardada: avanza 2.9.0; buscadorerrores y staging 5.8.0. No equivale a identificar el último renovador. |
| Plugin y hooks | Directorio `certbot_nginx` presente en Snap. Sin hooks declarados en los tres archivos; sin archivos regulares ni enlaces encontrados hasta profundidad 2 en renewal-hooks. No prueba ausencia de hooks/opciones en otras configuraciones ni funcionamiento del plugin. |
| Historial | Indicio de éxito para staging con 5.8.0 el 11/10/2026. Otros filtros mostraron indicios con 2.9.0 y 5.8.0 sin asociación fiable por dominio. No atribuir todas las renovaciones a Snap. |
| Certificados locales | SAN de cada archivo coincide con su dominio. Vencimientos UTC: avanza 07/01/2027, buscadorerrores 27/12/2026, staging 09/01/2027. Vigentes a la fecha de revisión; no valida cadena ni certificado servido. |
| Host | 3915 MiB RAM; captura: 2261 MiB disponibles, swap usada 4 MiB. Presupuesto QA candidato de 1024 MiB dejaría unos 1237 MiB en esa captura, sin garantizar capacidad sostenida. |
| Aislamiento de recursos actual | Staging y MySQL sin límites explícitos en sus unidades. Slices raíz, system y system-dimabug sin MemoryHigh/MemoryMax ni cuota CPU finitos; MemoryLow/MemoryMin en cero. Contabilidad CPU/memoria habilitada. |
| Pico observado | system.slice registra MemoryPeak 3641704448 bytes, unos 3473 MiB. Es un pico histórico del cgroup, con posible caché y sin correlación temporal con MemAvailable; no restarlo directamente de la RAM ni interpretarlo como OOM. |
| Diseño QA | Separación prevista de usuarios, secretos, puertos, datadir y base; loopback; DDL deshabilitado; controles apagados por defecto. Diseño no instalado ni demostrado en Ubuntu. |
| Pruebas anteriores | HTTPS con proxy JDK y persistencia aislada son antecedentes. No sustituyen una prueba con Nginx real. |

El comando anterior situó opciones después de `--`, haciendo que systemctl
mostrara propiedades completas e interpretara opciones como unidades. Fue de
lectura: no creó ni modificó servicios. Se extrajeron únicamente las propiedades
de las slices reales. No es necesario repetirlo.

## Cinco pruebas mínimas para decidir

No se ejecutan en esta entrega. Cada paquete produce un resultado breve:
fecha, artefactos/configuración identificados por hash, APROBADO/FALLIDO,
métricas y desviaciones; sin secretos ni volcados completos.

| Prueba | Comprobación mínima y aceptación | Permiso/entorno |
|---|---|---|
| 1. Certbot y recuperación | Cerrar configuración efectiva (overrides, cli.ini, hooks y programadores adicionales) con un inventario filtrado único. Validar Snap para las tres lineages y contrastar certificado servido con el local. Respaldo privado restaurable, responsable, ventana y retorno compatible definidos. Tras relevo autorizado: un solo programador, próximo disparo y alerta verificados. | Inventario de lectura; respaldo, ensayo de renovación y cambio de timer requieren autorización específica. Un dry-run contacta ACME y puede alterar temporalmente Nginx: no es lectura. |
| 2. Capacidad del host | Un informe de al menos 24 h representativas con mínimo MemAvailable, CPU, I/O, swap y latencias de las aplicaciones. Objetivos candidatos C.1: mínimo 2 GiB disponibles antes de QA y reserva 1 GiB durante QA. Revisar disco: reserva host 5 GiB y presupuesto completo QA/backup/logs. Si no cumple, separar o ampliar capacidad; no subir cuotas a ciegas. | Priorizar métricas existentes. No instalar monitores ni iniciar recogida persistente sin autorización. Si no hay datos, registrar esta prueba como pendiente. |
| 3. Arranque y aislamiento QA | Artefacto mínimo identificado: no usar el JAR general contra tres tablas. Alinear plantillas con C.1 y verificar en entorno aislado usuarios, secretos, puertos, red, permisos SQL/TLS, cgroups y almacenamiento. QA no accede a bases ajenas ni inicia transportes/trabajadores productivos. Controles de Meta/envío real apagados. | Preparación y ejecución local aislada por autorizar; ninguna aplicación en Ubuntu en esta fase. |
| 4. HTTPS y carga con Nginx real | Con certificado sintético y eventos sintéticos: ruta/métodos exactos, firma sobre bytes originales, límites, bloqueo de API interna y privacidad de logs globales. Duplicados concurrentes sin efectos duplicados; ACK solo tras commit. Arranque/calentamiento/carga con 512 MiB por servicio: sostenido bajo 384 MiB, pico menor al 90% de 512 MiB, sin OOM. Objetivos ACK p95 <=1 s y p99 <=3 s al ritmo aprobado (2 r/s, burst 4); sin degradación material de aplicaciones existentes respecto al baseline, con tolerancia acordada antes. | Primero entorno local aislado. Validación de impacto en host compartido solo en una posterior prueba acotada autorizada, antes de exposición pública. Sin conexión con Meta. |
| 5. Fallo y reversión | En el entorno QA aislado: caída SQL y reinicio recuperan eventos/reservas sin pérdida ni duplicación de efectos; pausa y alertas operativas verificadas. Reversión conserva datos y claves y afecta exclusivamente QA; no detiene Nginx/MySQL/staging existentes. Verificar restauración privada y presupuesto de disco aplicable. | Ensayo local aislado por autorizar; no simular fallos en servicios existentes. |

## Bloqueos de preparación, no más diagnósticos remotos

- Las plantillas siguen proponiendo 1 GiB por servicio frente a 512 MiB de C.1;
  también difieren MemoryHigh, CPU, heap, JDBC, tareas y límites Nginx.
  No instalarlas hasta disponer de un conjunto coherente revisado.
- Falta el bootstrap/artefacto QA aislado. Debe prepararse antes de las pruebas
  de arranque y carga, respetando arquitectura hexagonal y sin tocar backend
  productivo ni bases existentes durante esta revisión.
- Cerrar inventario Certbot no equivale a aprobar renovación o relevo.
- DNS, certificado nuevo, exposición HTTPS y conexión con Meta son decisiones
  posteriores independientes; no quedan autorizadas por pasar pruebas locales.

## Qué está listo / qué falta / qué debe hacer el usuario / Skill

- **Listo:** inventario suficiente para proponer Snap, vigencia local y estado de
  recursos y límites conocidos; diseño de separación QA documentado.
- **Falta:** superar los cinco paquetes, corregir coherencia de plantillas y
  preparar el artefacto aislado mediante una fase autorizada. No declarar todas
  las pruebas pendientes de cero si ya existen evidencias reutilizables.
- **Usuario:** no ejecutar más comandos ahora. Revisar este listado. El próximo
  paso propuesto es preparar localmente un paquete revisable de configuración,
  artefacto y pruebas; su ejecución y cualquier cambio remoto se autorizan aparte.
- **Skill:** C.2 consolidada documentalmente; riesgos de despliegue abiertos.
  La definición de Skill 4.5 no está disponible y no se certifica su cumplimiento.

**Conclusión operativa: despliegue pendiente, sin cambios remotos ni conexión Meta.**
