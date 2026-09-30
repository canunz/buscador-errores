package cl.casol.backend.identidad.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
//Paso I de Endpoint: Validation, Spring podrá rechazar la solicitud antes de llegar siquiera
// al caso de uso.
public record LoginRequest(

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password

) {
}