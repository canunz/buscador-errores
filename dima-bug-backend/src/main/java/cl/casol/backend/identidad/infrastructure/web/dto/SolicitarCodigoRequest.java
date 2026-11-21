package cl.casol.backend.identidad.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Datos que llegan en la petición para solicitar código.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record SolicitarCodigoRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Ingrese un correo válido")
        @jakarta.validation.constraints.Size(max = 120)
        @JsonAlias({"correo"})
        String email
) {
    public String identificador() {
        return email == null ? "" : email.trim();
    }
}
