package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.application.port.out.ConocimientoSugerido;
import cl.casol.backend.tickets.domain.ConteoTickets;
import cl.casol.backend.tickets.domain.FiltroTickets;
import cl.casol.backend.tickets.domain.PaginaTickets;

import java.util.List;

public interface ConsultarTicketsUseCase {
    /** El rol USUARIO solo recibe sus propios tickets, aunque envíe otros filtros. */
    PaginaTickets<TicketVista> listar(FiltroTickets filtro, int pagina, int tamano, String emailAutenticado);

    /** Contadores en una sola consulta. El rol USUARIO solo cuenta sus tickets. */
    ConteoTickets conteo(String emailAutenticado);

    TicketVista detalle(Long ticketId, String emailAutenticado);

    /** Conocimientos publicados parecidos al texto (usa el buscador existente). */
    List<ConocimientoSugerido> sugerencias(String texto);
}
