package cl.casol.backend.identidad.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Datos que llegan en la petición para cambiar estado usuario.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CambiarEstadoUsuarioRequest(
        @NotNull(message = "El estado es obligatorio") Boolean activo
) {
}
