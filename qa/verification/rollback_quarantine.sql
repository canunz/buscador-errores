-- Isolated verification/proposal only. DO NOT execute remotely without explicit authorization.
-- Preconditions: maintenance, stopped staging, zero application sessions, Gmail/dispatch disabled.
START TRANSACTION;
SELECT usuario_id FROM u_usuario WHERE registro_estado <> 'ADMINISTRADO' ORDER BY usuario_id FOR UPDATE;
UPDATE u_codigo_acceso c
JOIN u_usuario u ON u.usuario_id=c.usuario_id
SET c.usado=1
WHERE u.registro_estado <> 'ADMINISTRADO';
UPDATE u_correo_codigo m
JOIN u_codigo_acceso c ON c.codigo_id=m.codigo_id
JOIN u_usuario u ON u.usuario_id=c.usuario_id
SET m.estado='EXPIRADO',m.contenido_cifrado=NULL,m.actualizado_en=NOW()
WHERE u.registro_estado <> 'ADMINISTRADO'
  AND (m.estado IN ('PENDIENTE','ERROR_TEMPORAL','ENVIANDO') OR m.contenido_cifrado IS NOT NULL);
UPDATE u_usuario
SET usuario_estado=0,
    registro_estado=CASE WHEN registro_estado='AUTO_PENDIENTE' THEN 'AUTO_CANCELADO' ELSE registro_estado END
WHERE registro_estado <> 'ADMINISTRADO';
-- Operator must check these counts are zero before an authorized COMMIT.
SELECT COUNT(*) AS autorregistros_activos FROM u_usuario
WHERE registro_estado <> 'ADMINISTRADO' AND usuario_estado<>0;
SELECT COUNT(*) AS codigos_autorregistro_utilizables FROM u_codigo_acceso c
JOIN u_usuario u ON u.usuario_id=c.usuario_id
WHERE u.registro_estado <> 'ADMINISTRADO' AND c.usado=0;
-- No unconditional COMMIT here: the executor commits only after assertions succeed.
