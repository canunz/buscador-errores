package cl.casol.backend.identidad.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Datos que llegan en la petición para verificar código.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record VerificarCodigoRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Ingrese un correo válido")
        @jakarta.validation.constraints.Size(max = 120)
        @JsonAlias({"correo"})
        String email,

        @NotBlank(message = "El código es obligatorio")
        @jakarta.validation.constraints.Size(max = 16)
        @JsonAlias({"code"})
        String codigo
) {
    public String identificador() {
        return email == null ? "" : email.trim();
    }
}
