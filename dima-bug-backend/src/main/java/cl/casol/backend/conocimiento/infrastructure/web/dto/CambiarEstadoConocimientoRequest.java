package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoConocimientoRequest(
        @NotNull(message = "El estado es obligatorio") EstadoConocimiento estado) {
}
