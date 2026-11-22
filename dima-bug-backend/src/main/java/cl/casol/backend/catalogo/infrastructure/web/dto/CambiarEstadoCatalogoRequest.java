package cl.casol.backend.catalogo.infrastructure.web.dto;
import jakarta.validation.constraints.NotNull;
/**
 * Datos que llegan en la petición para cambiar estado catálogo.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CambiarEstadoCatalogoRequest(@NotNull Boolean activo) { }
