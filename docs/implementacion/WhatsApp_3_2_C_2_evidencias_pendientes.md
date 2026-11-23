# Etapa 3.2-C.2 — Evidencias pendientes para WhatsApp QA

Revisión documental del 15 de noviembre de 2026. No se ejecutaron conexiones
remotas, comandos Certbot, pruebas de carga, renovaciones, commits o despliegues.
No se modificaron Ubuntu, servicios, DNS, Nginx, MySQL o certificados. El informe
se basa en C.1, sus evidencias fechadas y las plantillas actuales del repositorio;
no presenta el estado remoto anterior como una nueva lectura en vivo.

## Skill solicitada

No se encontró una definición disponible de **Skill 4.5** en el catálogo de esta
sesión, las carpetas locales de skills revisadas ni las referencias buscadas en
el proyecto. No se inventa su contenido ni se certifica su cumplimiento.
La revisión siguiente responde a las instrucciones explícitas del usuario.
Se necesita la ruta o definición de esa Skill para evaluar su checklist propio.

## 1. Certbot: selección pendiente de evidencia

La evidencia de C.1 identifica APT 2.9.0 y Snap 5.10.0, con ambos timers habilitados
y programados. Las lineages registran 2.9.0 para avanza y 5.8.0 para buscadorerrores
y staging. Son datos históricos/configurados: no identifican por sí mismos al
último renovador efectivo de cada certificado. Salida 0 tampoco prueba renovación.

**Decisión recomendada con la evidencia actual:** mantener Snap como candidato
a validar, sin seleccionarlo definitivamente ni retirar APT. No se puede aprobar
la eliminación de un programador hasta demostrar compatibilidad con las tres
lineages y un procedimiento de recuperación que mantenga su renovación.

Comprobaciones exactas pendientes:

1. Registros filtrados de renovación efectiva para cada lineage, con fecha,
   versión/binario y resultado. No compartir logs completos o claves privadas.
2. Config-dir/work-dir/logs-dir efectivos, overrides, cron y wrappers adicionales.
3. Inventario de plugins y hooks y qué servicios/sites pueden modificar o recargar.
4. SAN, vigencia, cadena y fingerprint público del certificado que carga cada site.
5. Compatibilidad del candidato con las tres configuraciones mediante una prueba
   separadamente autorizada; un dry-run no es lectura y queda fuera de esta etapa.
6. Respaldo privado verificable, ventana, responsable y retorno a un programador
   compatible. Si una versión nueva cambia renewalparams, no reactivar una antigua
   a ciegas ni sobrescribir certificados nuevos con copias anteriores.

El usuario disponible no podía leer los logs detallados. La lectura privilegiada
acotada es la siguiente evidencia a obtener; no se necesitan contraseñas en el chat.

## 2. Memoria: candidatos, no capacidad validada

Los 512 MiB Spring + 512 MiB MySQL son un **presupuesto propuesto de 1 GiB**, no
un resultado de carga. La instantánea C.1 observó unos 2,18 GiB disponibles sobre
3,82 GiB físicos: restar 1 GiB deja aproximadamente 1,18 GiB en esa instantánea,
sin probar que alcance durante picos. No contar swap como RAM de despliegue.

Comprobar antes de aprobar:

- Baseline mínimo de 24 horas con carga representativa, renovación y demás sitios:
  memoria disponible mínima, CPU, I/O, actividad de swap y latencias existentes.
- Arranque y calentamiento del artefacto QA mínimo y de MySQL con esos límites,
  midiendo cgroup completo, no solo heap o buffer pool.
- Picos, carga sintética al ritmo aprobado, mensajes límite y duplicados. Cero
  OOM/reinicios inesperados y cero ACK previos al commit o eventos perdidos.
- Recuperación después de fallo SQL y reinicio; margen de memoria, tareas y FDs.
- Umbrales C.1 confirmados por el operador: baseline mínimo >=2 GiB disponibles,
  reserva objetivo >=1 GiB, sostenido bajo MemoryHigh y pico <90% de MemoryMax.
- No degradación material de staging/avanza/buscadorerrores. Acordar métricas y
  tolerancias antes de la prueba, no después de ver los resultados.
- Disk budget completo con datadir/redo/índices, backup, temporales y logs; cuota
  realmente aplicable y reserva del host, no únicamente un número en JSON.

Si el artefacto/MySQL no cabe, bloquear el despliegue o separar/ampliar capacidad
mediante otro cambio autorizado; no subir cuotas sobre el host compartido sin prueba.

## 3. Discrepancias de configuración — BLOQUEADOR

| Parámetro | Plantillas actuales | Candidato C.1 |
|---|---:|---:|
| MemoryMax por servicio | 1 GiB | 512 MiB |
| MemoryHigh por servicio | 768 MiB | 384 MiB |
| CPUQuota por servicio | 100% de una CPU | 50% de una CPU |
| Heap Spring máximo | 384 MiB | 192 MiB |
| Pool JDBC | 4 | 2 |
| TasksMax por unidad | 128 | 96 |
| Rate Nginx | 10 solicitudes/s | 2 solicitudes/s |
| Burst Nginx | 20 | 4 |
| Conexiones Nginx QA | 16 | 8 |

No instalar las plantillas actuales suponiendo que aplican el candidato reducido.
Se requiere un conjunto coherente renderizado, diff aprobado, validación de
systemd/Nginx y prueba en entorno aislado. En esta etapa no se editaron plantillas.

## 4. Protección de staging y otras aplicaciones

**Bien diseñado:** nombres, usuarios, directorios, base y puerto QA separados;
loopback; secretos propios; validate sin DDL automático; módulos productivos
excluidos del artefacto mínimo; reversión sin borrar datos ni parar otras unidades.

**No demostrado aún:**

1. El artefacto mínimo existe, tiene hash y arranca con solo las entidades de QA.
   El JAR general no es sustituto válido para una base de tres tablas.
2. La configuración efectiva no hereda secretos, perfiles, transportes o colas
   de staging; los grants SQL no permiten acceso a otras bases.
3. Puertos, rutas, usuarios, datadir, socket y recursos realmente no colisionan.
4. Los controles de systemd/cgroups/red son efectivos; permisos TLS y políticas
   del host (incluido AppArmor si aplica) permiten QA sin relajar la protección
   general del mysqld existente.
5. La carga y el I/O de QA no afectan los tres sitios. CPUWeight y MemoryMax no
   prueban por sí solos ausencia de impacto sobre recursos compartidos.
6. Los hooks y desafíos nginx de Certbot no producen cambios inesperados en los
   otros vhosts; hay respaldo y comprobación antes/después de todos los afectados.
7. Nginx real prueba ruta cruda exacta, métodos, HMAC, tamaño y rechazo de API,
   Actuator/Swagger; privacidad incluye logs globales, no solo el vhost QA.
8. Hay un mecanismo operativo para pausar recepción y alertar; una API Java local
   y un umbral escrito no constituyen todavía automatización o procedimiento probado.

## 5. Matriz de autorización

| Evidencia | Estado de revisión | Acción necesaria |
|---|---|---|
| Inventario de duplicidad Certbot | Disponible y fechado | Actualizar antes de cualquier cambio |
| Última renovación por certificado | Falta | Lectura privilegiada filtrada |
| Candidato único compatible | Falta | Validación separadamente autorizada |
| Respaldo y recuperación Certbot | Falta prueba | Custodia y ensayo aprobado |
| Presupuesto reducido | Diseñado | Medición real y plantillas coherentes |
| Baseline sostenido del host | Falta | 24 h representativas, sin instalar monitores sin permiso |
| Artefacto Spring QA aislado | Pendiente | Construcción y pruebas en fase autorizada |
| MySQL separado/permisos/TLS | Diseñado | Aprovisionamiento y prueba autorizados posteriormente |
| Integración con Nginx real | Preparada, no ejecutada | Entorno local autorizado; el proxy JDK previo no la sustituye |
| Pausa, alertas y reversión | Diseñado | Responsable, mecanismo y ensayo |
| Skill 4.5 | Definición no disponible | Obtener su ruta/documento |
| Despliegue | NO AUTORIZABLE aún | Cerrar gates y pedir aprobación específica |

## Qué debe hacer el usuario

Primero indicar dónde está definida la Skill 4.5. Para avanzar Certbot puede
ejecutar, uno por vez y en Ubuntu, un comando de lectura privilegiada filtrada
que se le proporcione. No enviar contraseña, claves, tokens o logs sin filtrar.
No deshabilitar timers, renovar certificados o ejecutar dry-run como diagnóstico.

Los pasos posteriores se darán según la salida, sin entregar un lote de comandos
de cambio. Ninguna evidencia de esta revisión concede autorización implícita
para desplegar, publicar el hostname o conectar Meta.
