package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolverTicketRequest(
        @NotBlank(message = "Debe describir la solución aplicada") String solucion,
        Integer conocimientoId) {
}