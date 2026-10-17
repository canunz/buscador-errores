package cl.casol.backend.conocimiento.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public record GuardarPruebaAdministrativaRequest(@NotBlank @Size(max=500) String descripcion,@Size(max=500) String resultadoEsperado){@JsonAnySetter public void desconocido(String clave,Object valor){throw new IllegalArgumentException("Campo no permitido: "+clave);}}