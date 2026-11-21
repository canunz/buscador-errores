-- Apply manually after a verified backup. MySQL 8.0.16+; no user/role/status/hash updates.
-- Abort on duplicate normalized emails; review duplicates WITHOUT deleting/merging accounts.
-- This DDL is not transactionally reversible. Do not automatically rerun.
ALTER TABLE u_usuario
    ADD COLUMN registro_estado VARCHAR(24) NOT NULL DEFAULT 'ADMINISTRADO',
    ADD CONSTRAINT chk_usuario_registro_estado CHECK
        (registro_estado IN ('ADMINISTRADO','AUTO_PENDIENTE','AUTO_CONFIRMADO','AUTO_CANCELADO')),
    ADD CONSTRAINT chk_usuario_registro_inactivo CHECK
        (registro_estado NOT IN ('AUTO_PENDIENTE','AUTO_CANCELADO') OR usuario_estado = 0);

-- Server-side canonicalization plus a unique DB key protects concurrent administrative creation.
CREATE UNIQUE INDEX uq_usuario_email_normalizado ON u_usuario ((LOWER(TRIM(usuario_email))));

CREATE INDEX idx_usuario_registro_creacion ON u_usuario (registro_estado,usuario_fecha_creacion);
