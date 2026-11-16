package cl.casol.backend.conocimiento.infrastructure.web.dto;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public record EstadoPruebaRequest(@NotNull Boolean activa){@JsonAnySetter public void desconocido(String clave,Object valor){throw new IllegalArgumentException("Campo no permitido: "+clave);}}