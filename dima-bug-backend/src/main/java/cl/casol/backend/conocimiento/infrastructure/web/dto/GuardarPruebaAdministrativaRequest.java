package cl.casol.backend.conocimiento.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
/**
 * Datos que llegan en la petición para guardar prueba administrativa.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record GuardarPruebaAdministrativaRequest(@NotBlank @Size(max=500) String descripcion,@Size(max=500) String resultadoEsperado){@JsonAnySetter public void desconocido(String clave,Object valor){throw new IllegalArgumentException("Campo no permitido: "+clave);}}