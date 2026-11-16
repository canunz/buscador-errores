package cl.casol.backend.identidad.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public record CrearResponsableRequest(@NotNull @Positive Integer departamentoId, @NotBlank @Size(max=160) String nombre, @Size(max=120) String cargo, @Size(max=160) String contacto) {
    @JsonAnySetter public void desconocido(String campo, Object valor) { throw new IllegalArgumentException("Campo no permitido: " + campo); }
}
