package cl.casol.backend.identidad.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Datos que llegan en la petición para modificar usuario.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record ModificarUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido") String email,
        @NotNull(message = "El rol es obligatorio") Integer rolId,
        @JsonAlias({"usuarioPassword", "newPassword", "clave", "contrasena"})
        String password
) {
}
