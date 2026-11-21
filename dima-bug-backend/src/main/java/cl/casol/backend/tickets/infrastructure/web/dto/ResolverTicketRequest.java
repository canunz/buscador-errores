package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos que llegan en la petición para resolver ticket.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record ResolverTicketRequest(
        @NotBlank(message = "Debe describir la solución aplicada") String solucion,
        Integer conocimientoId) {
}