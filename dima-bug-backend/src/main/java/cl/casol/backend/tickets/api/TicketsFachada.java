package cl.casol.backend.tickets.api;

import cl.casol.backend.tickets.application.port.in.CrearTicketUseCase;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.Ticket;
import org.springframework.stereotype.Service;

/** API pública de tickets para canales: conserva permisos y validaciones del caso de uso. */
@Service
public class TicketsFachada {
    public static final int MAX_TITULO = Ticket.MAX_TITULO;
    public record Solicitud(String titulo, String descripcion) { }
    private final CrearTicketUseCase crear;
    public TicketsFachada(CrearTicketUseCase crear) { this.crear = crear; }
    public String crear(Solicitud solicitud, String actorEmail) {
        return crear.crear(new CrearTicketUseCase.Comando(solicitud.titulo(), solicitud.descripcion(),
                PrioridadTicket.MEDIA, null, null, null), actorEmail).ticket().getCodigo();
    }
}
