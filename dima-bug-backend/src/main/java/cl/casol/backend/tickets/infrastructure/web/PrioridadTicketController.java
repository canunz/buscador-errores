package cl.casol.backend.tickets.infrastructure.web;

import cl.casol.backend.tickets.application.port.in.CambiarPrioridadTicketUseCase;
import cl.casol.backend.tickets.infrastructure.web.dto.CambiarPrioridadRequest;
import cl.casol.backend.tickets.infrastructure.web.dto.TicketDetalleResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets/{id}/prioridad")
public class PrioridadTicketController {
    private final CambiarPrioridadTicketUseCase cambiarPrioridad;

    public PrioridadTicketController(CambiarPrioridadTicketUseCase cambiarPrioridad) {
        this.cambiarPrioridad = cambiarPrioridad;
    }

    @PatchMapping
    public TicketDetalleResponse cambiar(@PathVariable Long id, @Valid @RequestBody CambiarPrioridadRequest request,
            Authentication authentication) {
        return TicketDetalleResponse.from(cambiarPrioridad.cambiarPrioridad(id, request.prioridad(), request.motivo(),
                authentication.getName()));
    }
}
