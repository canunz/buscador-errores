package cl.casol.backend.procedimiento.infrastructure.web.dto;

import cl.casol.backend.procedimiento.domain.EstadoProcedimiento;
import jakarta.validation.constraints.NotNull;

/**
 * Datos que llegan en la petición para cambiar estado procedimiento.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CambiarEstadoProcedimientoRequest(
        @NotNull(message="El estado es obligatorio") EstadoProcedimiento estado) { }
