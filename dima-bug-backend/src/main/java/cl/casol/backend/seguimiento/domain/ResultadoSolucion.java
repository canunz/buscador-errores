package cl.casol.backend.seguimiento.domain;

import java.time.LocalDateTime;

public record ResultadoSolucion(Integer id, Integer solucionId, Integer usuarioId,
        boolean funciono, String comentario, LocalDateTime fecha) { }
