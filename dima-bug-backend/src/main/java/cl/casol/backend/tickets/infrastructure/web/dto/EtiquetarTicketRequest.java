package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EtiquetarTicketRequest(@NotNull(message = "Debe enviar la lista de etiquetas") List<Long> etiquetaIds) {
}