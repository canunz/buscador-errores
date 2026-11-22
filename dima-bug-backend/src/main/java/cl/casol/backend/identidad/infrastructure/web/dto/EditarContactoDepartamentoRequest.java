package cl.casol.backend.identidad.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
/**
 * Datos que llegan en la petición para editar contacto departamento.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record EditarContactoDepartamentoRequest(@NotBlank @Size(max=50) String tipo, @NotBlank @Size(max=100) String valor, @NotNull @Min(0) Long version) {
    @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido: "+campo); }
}
