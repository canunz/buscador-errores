package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Datos que llegan en la petición para etiquetar ticket.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record EtiquetarTicketRequest(@NotNull(message = "Debe enviar la lista de etiquetas") List<Long> etiquetaIds) {
}