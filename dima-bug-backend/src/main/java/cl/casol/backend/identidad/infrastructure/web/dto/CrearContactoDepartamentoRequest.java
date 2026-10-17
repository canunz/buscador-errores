package cl.casol.backend.identidad.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public record CrearContactoDepartamentoRequest(@NotBlank @Size(max=50) String tipo, @NotBlank @Size(max=100) String valor, Boolean activo) {
    @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido: "+campo); }
}
