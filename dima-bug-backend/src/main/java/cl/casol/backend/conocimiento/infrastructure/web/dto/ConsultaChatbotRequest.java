package cl.casol.backend.conocimiento.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConsultaChatbotRequest(
        @NotBlank(message = "La consulta es obligatoria")
        @Size(max = 300, message = "La consulta no puede superar 300 caracteres") String consulta) { }
