package cl.casol.backend.conocimiento.infrastructure.web.dto;
import cl.casol.backend.conocimiento.domain.TipoSolucion;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public record GuardarSolucionRequest(
        @NotBlank(message = "La descripción es obligatoria") String descripcion,
        TipoSolucion tipo,
        @Min(value = 1, message = "orden debe ser mayor o igual a 1") Integer orden,
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título admite máximo 150 caracteres") String titulo) {
    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido: " + nombre);
    }
}
