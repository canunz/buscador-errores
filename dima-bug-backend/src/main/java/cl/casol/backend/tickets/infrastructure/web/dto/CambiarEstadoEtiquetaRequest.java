package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoEtiquetaRequest(@NotNull(message = "Debe indicar si la etiqueta queda activa") Boolean activa) {
}