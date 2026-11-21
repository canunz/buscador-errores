package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import jakarta.validation.constraints.NotNull;

/**
 * Datos que llegan en la petición para cambiar estado conocimiento.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CambiarEstadoConocimientoRequest(
        @NotNull(message = "El estado es obligatorio") EstadoConocimiento estado) {
}
