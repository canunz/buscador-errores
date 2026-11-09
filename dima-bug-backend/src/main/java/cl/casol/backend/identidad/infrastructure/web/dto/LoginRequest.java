package cl.casol.backend.identidad.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "El usuario o correo es obligatorio")
        @JsonAlias({"usuario", "username", "correo"})
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password

) {
    public String identificador() {
        return email == null ? "" : email.trim();
    }

    public String clave() {
        return password == null ? "" : password.trim();
    }
}