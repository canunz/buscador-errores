package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Datos que llegan en la petición para cambiar estado etiqueta.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CambiarEstadoEtiquetaRequest(@NotNull(message = "Debe indicar si la etiqueta queda activa") Boolean activa) {
}