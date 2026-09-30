package cl.casol.backend.conocimiento.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuardarConocimientoRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 255, message = "El título no puede superar 255 caracteres") String titulo,
        @NotBlank(message = "La descripción es obligatoria") String descripcion,
        Integer hardwareId,
        Integer sistemaId,
        Integer moduloId,
        Integer frecuenciaId,
        String comentario) {
}
