package cl.casol.backend.conocimiento.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public record CopiarSolucionRequest(@NotNull @Positive Integer conocimientoOrigenId,
        @NotNull @Positive Integer solucionOrigenId, Boolean confirmarAutoservicio) {
    @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido: "+campo); }
}
