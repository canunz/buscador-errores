package cl.casol.backend.conocimiento.infrastructure.web.dto;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAnySetter;
/**
 * Datos que llegan en la petición para estado prueba.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record EstadoPruebaRequest(@NotNull Boolean activa){@JsonAnySetter public void desconocido(String clave,Object valor){throw new IllegalArgumentException("Campo no permitido: "+clave);}}