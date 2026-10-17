package cl.casol.backend.conocimiento.infrastructure.web.dto;
import jakarta.validation.constraints.NotNull;
public record CambiarEstadoSolucionRequest(@NotNull Boolean activo) { }
