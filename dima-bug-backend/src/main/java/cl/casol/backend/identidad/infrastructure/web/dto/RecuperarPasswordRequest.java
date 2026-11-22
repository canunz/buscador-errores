package cl.casol.backend.identidad.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan en la petición para recuperar password.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record RecuperarPasswordRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Ingrese un correo válido")
        @JsonAlias({"correo"})
        String email,

        @NotBlank(message = "El código es obligatorio")
        @JsonAlias({"code"})
        String codigo,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
        @JsonAlias({"usuarioPassword", "newPassword", "clave", "contrasena"})
        String password
) {
    public String identificador() {
        return email == null ? "" : email.trim();
    }

    public String clave() {
        return password == null ? "" : password.trim();
    }
}
