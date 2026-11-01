package cl.casol.backend.identidad.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerificarCodigoRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Ingrese un correo válido")
        @JsonAlias({"correo"})
        String email,

        @NotBlank(message = "El código es obligatorio")
        @JsonAlias({"code"})
        String codigo
) {
    public String identificador() {
        return email == null ? "" : email.trim();
    }
}
