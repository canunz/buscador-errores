package cl.casol.backend.identidad.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public record EstadoResponsableRequest(@NotNull Boolean activo, @NotBlank @Pattern(regexp="[0-9a-f]{64}") String revision) {
    @JsonAnySetter public void desconocido(String campo, Object valor) { throw new IllegalArgumentException("Campo no permitido: " + campo); }
}
