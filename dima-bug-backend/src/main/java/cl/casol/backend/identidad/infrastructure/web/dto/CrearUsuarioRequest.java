package cl.casol.backend.identidad.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Datos que llegan en la petición para crear usuario.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CrearUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido") String email,
        String password,
        @NotNull(message = "El rol es obligatorio") Integer rolId
) {
}
