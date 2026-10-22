package cl.casol.backend.identidad.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public record EditarDepartamentoRequest(@NotBlank @Size(max=100) String nombre, @NotNull @Min(0) Long version) {
    @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido: "+campo); }
}
