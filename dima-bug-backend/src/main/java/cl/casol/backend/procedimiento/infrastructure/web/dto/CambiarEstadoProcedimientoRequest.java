package cl.casol.backend.procedimiento.infrastructure.web.dto;

import cl.casol.backend.procedimiento.domain.EstadoProcedimiento;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoProcedimientoRequest(
        @NotNull(message="El estado es obligatorio") EstadoProcedimiento estado) { }
