package cl.casol.backend.identidad.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrearUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password,
        @NotNull(message = "El rol es obligatorio") Integer rolId
) {
}
