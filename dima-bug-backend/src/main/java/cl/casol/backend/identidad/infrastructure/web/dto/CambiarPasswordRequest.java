package cl.casol.backend.identidad.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan en la petición para cambiar password.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CambiarPasswordRequest(
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        @JsonAlias({"usuarioPassword", "newPassword", "clave", "contrasena"})
        String password
) {
}
