-- Login sin contraseña: códigos de acceso de un solo uso enviados al correo del usuario.
-- Solo se guarda el hash del código. Vence a los 5 minutos y cada nueva solicitud invalida la anterior.
CREATE TABLE IF NOT EXISTS u_codigo_acceso (
    codigo_id   BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id  INT          NOT NULL,
    codigo_hash VARCHAR(100) NOT NULL,
    creado_en   DATETIME     NOT NULL,
    expira_en   DATETIME     NOT NULL,
    intentos    INT          NOT NULL DEFAULT 0,
    usado       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (codigo_id),
    KEY idx_codigo_acceso_usuario (usuario_id, usado),
    CONSTRAINT fk_codigo_acceso_usuario FOREIGN KEY (usuario_id) REFERENCES u_usuario (usuario_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
