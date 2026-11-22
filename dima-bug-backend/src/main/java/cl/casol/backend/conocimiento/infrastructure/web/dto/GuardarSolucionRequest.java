package cl.casol.backend.conocimiento.infrastructure.web.dto;
import cl.casol.backend.conocimiento.domain.TipoSolucion;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Datos que llegan en la petición para guardar solución.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record GuardarSolucionRequest(
        @NotBlank(message = "La descripción es obligatoria") String descripcion,
        TipoSolucion tipo,
    // Rechaza campo.
        @Min(value = 1, message = "orden debe ser mayor o igual a 1") Integer orden,
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título admite máximo 150 caracteres") String titulo) {
    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido: " + nombre);
    }
}
