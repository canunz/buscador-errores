package cl.casol.backend.identidad.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
/**
 * Datos que llegan en la petición para estado organización.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record EstadoOrganizacionRequest(@NotNull Boolean activo, @NotNull @Min(0) Long version) {
    @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido: "+campo); }
}
