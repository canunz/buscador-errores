# Etapa 2.2-A: MySQL exclusivo para el esquema WhatsApp

Alcance autorizado: consolidar las tres tablas de prueba y validar integridad,
concurrencia y recuperación. No implementa persistencia Spring Boot ni modifica
las bases, perfiles, contenedores o volúmenes existentes de DimaBug.

## Archivos

- schema.sql: definición consolidada, no una migración productiva.
- run.py: crea recursos nuevos, aplica el esquema y ejecuta casos reales MySQL.
- Evidencias: carpetas nuevas qa/runtime/mysql-wa22a-20261114-<id>.

El esquema corrige FK compuesta evento/conversación en respuestas, índice de orden
duplicado, tipos BIGINT firmados compatibles con Long, estados ASCII binarios,
reservas coherentes, propuesta completa, secuencias y vencimientos. Se separan
intentos de preparación/envío y se registra la fase de reintento. No hay imágenes,
teléfonos ni secretos de API en las tablas de prueba.

SQL no implementa por sí solo todas las reglas de dominio: unicidad de identidad
HMAC, referencia canónica, crecimiento monotónico de cercado, transición completa
permitida, límite de bytes y política de purga requieren el adaptador/caso de uso.
La prueba de cercado usa actualizaciones condicionadas por token/versión. El
puntero ultima_imagen_evento sigue siendo lógico para evitar una FK circular;
la limpieza debe anularlo bajo bloqueo antes de purgar el evento.

## Aislamiento y credenciales

Cada ejecución usa nombres nuevos de contenedor, volumen y base, con etiqueta
propia. Contexto Docker desktop-linux local por named pipe, imagen existente
fijada por ID, network=none y ningún puerto publicado. No descarga imágenes.
El acceso SQL se hace por socket Unix mediante docker exec. Los clientes de las
pruebas abren conexiones independientes; no se conectan a 13318, 3307 ni staging.

Root y cuenta qa_wa son exclusivamente sintéticos y nuevos. Las contraseñas se
generan en memoria, se materializan en la carpeta secrets ignorada por Git con ACL
restringida a usuario/SYSTEM y se montan read-only. Se utilizan archivos cliente;
no se pasan contraseñas en argumentos ni se guardan en evidencias. El SQL de
bootstrap de credenciales solo viaja por stdin y no se registra. La cuenta de
pruebas dispone solo de SELECT/INSERT/UPDATE/DELETE en su base; DDL usa root solo
dentro de este contenedor nuevo. No se usa custodia de otros entornos.

El volumen es exclusivo y persiste entre reinicios. No se publica MySQL en red ni
se introduce otra aplicación. Las tablas de usuario creadas son únicamente
wa_conversacion, wa_evento y wa_respuesta; las tablas internas de MySQL son las
propias de la inicialización de una instancia nueva.

## Ejecución explícita

```powershell
python -I -B qa/whatsapp/mysql-stage22a/run.py
```

Requiere autorización para crear recursos Docker y ejecutarlo fuera del aislamiento
que restringe el named pipe. No modifica el contexto Docker global. Valida su
identidad, imagen, red y volumen antes de detener o reiniciar. No ejecuta prune ni
borra recursos. Al finalizar deja el contenedor nuevo detenido y conserva volumen,
secretos y evidencia; no usa --rm. No publicar ni versionar la carpeta secrets.
Retirar esos recursos requerirá una decisión explícita de limpieza.

La imagen MySQL 8.0.46 es la candidata local ya disponible para compatibilidad.
No constituye recomendación de producción: la decisión de una rama LTS soportada
continúa pendiente y debe validarse sin reemplazar silenciosamente este entorno.

## Validación

Comprueba variables reales (incluido flush=1, sync_binlog=1 y binlog ROW), tres
objetos InnoDB, CHECK/UNIQUE/FK y denegación DDL a la cuenta limitada. Prueba
SKIP LOCKED con dos sesiones, exclusión de la misma fila, recepción concurrente
del mismo identificador, deadlock y reintento ordenado.

Después provoca SIGKILL solo en la instancia nueva con un evento confirmado y una
transacción sin commit que contiene finalización y outbox. Reinicia el mismo
volumen y comprueba conservación/rollback. Recupera una reserva naturalmente
vencida, cambia token/cercado y demuestra que el propietario anterior afecta cero
filas. Confirma la finalización/outbox en una transacción y vuelve a reiniciar.

Los datos de prueba son sintéticos. Las huellas SHA-256 usadas como claves fixture
no son una implementación del HMAC de producción. X'01' representa contenido
binario sintético, no una certificación de cifrado. No valida JPA/Hibernate,
replicación, pérdida eléctrica, restauración de backups, carga sostenida ni Meta.
Los resultados ejecutados, errores y limitaciones se documentan en cada informe
final; este README no declara pruebas satisfactorias por adelantado.
