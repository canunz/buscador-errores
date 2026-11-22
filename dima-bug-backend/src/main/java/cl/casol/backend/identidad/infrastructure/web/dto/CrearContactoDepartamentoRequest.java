package cl.casol.backend.identidad.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
/**
 * Datos que llegan en la petición para crear contacto departamento.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CrearContactoDepartamentoRequest(@NotBlank @Size(max=50) String tipo, @NotBlank @Size(max=100) String valor, Boolean activo) {
    @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido: "+campo); }
}
