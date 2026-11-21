package cl.casol.backend.seguimiento.domain;

import java.time.LocalDateTime;

/**
 * Modelo de dominio de resultado solución. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record ResultadoSolucion(Integer id, Integer solucionId, Integer usuarioId,
        boolean funciono, String comentario, LocalDateTime fecha) { }
