package cl.casol.backend.ejecucion.domain;

import cl.casol.backend.identidad.domain.Usuario;
import java.time.LocalDateTime;

public record Ejecucion(Integer id, Integer procedimientoId, Usuario usuario,
        LocalDateTime fechaInicio, LocalDateTime fechaFin, EstadoEjecucion estado,
        String observaciones) { }
