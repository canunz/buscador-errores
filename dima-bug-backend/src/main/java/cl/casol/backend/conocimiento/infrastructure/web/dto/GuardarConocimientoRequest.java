package cl.casol.backend.conocimiento.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan en la petición para guardar conocimiento.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record GuardarConocimientoRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título no puede superar 150 caracteres") String titulo,
        @NotBlank(message = "La descripción es obligatoria") String descripcion,
        Integer hardwareId,
        Integer sistemaId,
        Integer moduloId,
        Integer frecuenciaId,
        @Size(max = 500) String comentario) {
}
