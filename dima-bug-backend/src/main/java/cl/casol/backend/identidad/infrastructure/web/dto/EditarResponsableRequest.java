package cl.casol.backend.identidad.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public record EditarResponsableRequest(@NotBlank @Size(max=160) String nombre, @Size(max=120) String cargo, @Size(max=160) String contacto, @NotBlank @Pattern(regexp="[0-9a-f]{64}") String revision) {
    @JsonAnySetter public void desconocido(String campo, Object valor) { throw new IllegalArgumentException("Campo no permitido: " + campo); }
}
