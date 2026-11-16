-- Nivel de las etiquetas (Normal / Importante / Crítica) para agruparlas y filtrarlas.
ALTER TABLE ticket_etiqueta
    ADD COLUMN nivel VARCHAR(15) NOT NULL DEFAULT 'NORMAL' AFTER color;

-- Archivos de los tickets (imágenes, videos y PDF). Si comentario_id viene, el archivo va dentro de ese mensaje.
-- Si quedó una tabla de una prueba anterior con otras columnas, se reemplaza (solo tenía datos de prueba).
DROP TABLE IF EXISTS ticket_adjunto;
CREATE TABLE ticket_adjunto (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id     BIGINT       NOT NULL,
    comentario_id BIGINT       NULL,
    nombre        VARCHAR(150) NOT NULL,
    mime          VARCHAR(60)  NOT NULL,
    tamano        BIGINT       NOT NULL,
    referencia    VARCHAR(80)  NOT NULL,
    autor_id      INT          NOT NULL,
    fecha         DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_adjunto_referencia (referencia),
    KEY idx_ticket_adjunto_ticket (ticket_id),
    CONSTRAINT fk_ticket_adjunto_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_adjunto_comentario FOREIGN KEY (comentario_id) REFERENCES ticket_comentario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
