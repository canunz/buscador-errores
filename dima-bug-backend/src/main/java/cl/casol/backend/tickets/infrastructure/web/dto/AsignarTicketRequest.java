package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Datos que llegan en la petición para asignar ticket.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record AsignarTicketRequest(@NotNull(message = "Debe indicar el técnico asignado") Integer asignadoId) {
}