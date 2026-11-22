package cl.casol.backend.tickets.infrastructure.web;

import cl.casol.backend.tickets.application.port.in.CambiarPrioridadTicketUseCase;
import cl.casol.backend.tickets.infrastructure.web.dto.CambiarPrioridadRequest;
import cl.casol.backend.tickets.infrastructure.web.dto.TicketDetalleResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST de prioridad ticket. Atiende las rutas que empiezan con /api/tickets/{id}/prioridad.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/tickets/{id}/prioridad")
public class PrioridadTicketController {
    private final CambiarPrioridadTicketUseCase cambiarPrioridad;

    public PrioridadTicketController(CambiarPrioridadTicketUseCase cambiarPrioridad) {
        this.cambiarPrioridad = cambiarPrioridad;
    }

    // PATCH /api/tickets/{id}/prioridad: cambiar.
    @PatchMapping
    public TicketDetalleResponse cambiar(@PathVariable Long id, @Valid @RequestBody CambiarPrioridadRequest request,
            Authentication authentication) {
        return TicketDetalleResponse.from(cambiarPrioridad.cambiarPrioridad(id, request.prioridad(), request.motivo(),
                authentication.getName()));
    }
}
