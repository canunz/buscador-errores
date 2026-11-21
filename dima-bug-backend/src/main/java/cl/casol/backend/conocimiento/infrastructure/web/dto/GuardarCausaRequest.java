package cl.casol.backend.conocimiento.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan en la petición para guardar causa.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record GuardarCausaRequest(
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 300, message = "La descripción no puede superar 300 caracteres")
        String descripcion,
        Integer orden) { }
