-- Autoatención WhatsApp: conocimiento y solución del POS para la base QA completa (wa_durable_*).
-- No crea cuentas. Solicitante de tickets: soporte@qa.dimabug.test (fixture QA con rol SOPORTE).
-- Solo modifica datos si DATABASE() es wa_durable_<hex>; en cualquier otra base no cambia nada.
-- Se compara siempre contra literales (no variables @): evita conflictos de intercalación.
-- La búsqueda de autoservicio usa: título del conocimiento, título de la solución y descripción de la solución.

-- 0. Base actual y prerrequisitos (deben ser: es_base_qa=1, roles_soporte>=1, actor_valido=1).
SELECT DATABASE() AS base_actual,
       DATABASE() REGEXP '^wa_durable_[a-f0-9]+$' AS es_base_qa,
       (SELECT COUNT(*) FROM u_rol WHERE rol_nombre = 'SOPORTE' AND rol_estado = 1) AS roles_soporte,
       (SELECT COUNT(*) FROM u_usuario u JOIN u_rol r ON r.rol_id = u.rol_id
         WHERE u.usuario_email = 'soporte@qa.dimabug.test' AND u.usuario_estado = 1
           AND u.registro_estado = 'ADMINISTRADO' AND r.rol_nombre = 'SOPORTE' AND r.rol_estado = 1) AS actor_valido;

START TRANSACTION;

INSERT INTO co_conocimiento (conocimiento_titulo, conocimiento_descripcion, conocimiento_estado, creado_por, fecha_creacion)
SELECT 'Error de conexión del POS de venta: caja sin conexión',
       'El POS de venta muestra un error de conexión con la caja o el servidor, o deja de responder.',
       'PUBLICADO', u.usuario_id, NOW(6)
FROM u_usuario u
WHERE DATABASE() REGEXP '^wa_durable_[a-f0-9]+$'
  AND u.usuario_email = 'soporte@qa.dimabug.test'
  AND NOT EXISTS (SELECT 1 FROM co_conocimiento c
                  WHERE c.conocimiento_titulo = 'Error de conexión del POS de venta: caja sin conexión');

INSERT INTO co_solucion (conocimiento_id, solucion_descripcion, solucion_tipo, solucion_orden,
                         solucion_titulo, solucion_estado, solucion_autoservicio)
SELECT c.conocimiento_id,
       'Error del POS de venta o caja sin conexión:\n1. Guarda o cancela la venta en curso.\n2. Cierra la aplicación de caja del POS.\n3. Apaga el POS de venta, espera 30 segundos y vuelve a encenderlo.\n4. Abre la aplicación de caja e intenta una venta de prueba.\nSi el error de conexión continúa, responde NO y se creará un ticket para Soporte TI.',
       'PASOS', 1, 'Reiniciar el POS de venta', 1, 1
FROM co_conocimiento c
WHERE DATABASE() REGEXP '^wa_durable_[a-f0-9]+$'
  AND c.conocimiento_titulo = 'Error de conexión del POS de venta: caja sin conexión'
  AND NOT EXISTS (SELECT 1 FROM co_solucion s WHERE s.conocimiento_id = c.conocimiento_id
                  AND s.solucion_titulo = 'Reiniciar el POS de venta');

COMMIT;

-- 1. Verificación de estado (no solo de existencia). Resultado esperado: una fila con listo_para_autoservicio = 1.
--    Si una fila ya existía con otro estado, este script NO la modifica: listo_para_autoservicio = 0 lo indica.
SELECT c.conocimiento_id, c.conocimiento_estado, s.solucion_id, s.solucion_estado, s.solucion_autoservicio,
       (c.conocimiento_estado = 'PUBLICADO' AND s.solucion_estado = 1 AND s.solucion_autoservicio = 1) AS listo_para_autoservicio
FROM co_conocimiento c
LEFT JOIN co_solucion s ON s.conocimiento_id = c.conocimiento_id AND s.solucion_titulo = 'Reiniciar el POS de venta'
WHERE c.conocimiento_titulo = 'Error de conexión del POS de venta: caja sin conexión';
