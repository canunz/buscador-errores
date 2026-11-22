package cl.casol.backend.ejecucion.domain;

import cl.casol.backend.identidad.domain.Usuario;
import java.time.LocalDateTime;

/**
 * Modelo de dominio de ejecución. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Ejecucion(Integer id, Integer procedimientoId, Usuario usuario,
        LocalDateTime fechaInicio, LocalDateTime fechaFin, EstadoEjecucion estado,
        String observaciones) { }
