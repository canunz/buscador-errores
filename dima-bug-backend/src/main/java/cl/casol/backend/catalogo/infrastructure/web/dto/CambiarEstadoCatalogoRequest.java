package cl.casol.backend.catalogo.infrastructure.web.dto;
import jakarta.validation.constraints.NotNull;
public record CambiarEstadoCatalogoRequest(@NotNull Boolean activo) { }
