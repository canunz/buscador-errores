package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.application.port.out.ConocimientoSugerido;
import cl.casol.backend.tickets.domain.ConteoTickets;
import cl.casol.backend.tickets.domain.FiltroTickets;
import cl.casol.backend.tickets.domain.PaginaTickets;
import cl.casol.backend.tickets.domain.TicketsPorUsuario;

import java.util.List;

/**
 * Puerto de entrada: define la acción "consultar tickets" que ofrece el módulo de tickets.
 * El controlador usa esta interfaz y el servicio la implementa.
 */
public interface ConsultarTicketsUseCase {
    /** El rol USUARIO solo recibe sus propios tickets, aunque envíe otros filtros. */
    PaginaTickets<TicketVista> listar(FiltroTickets filtro, int pagina, int tamano, String emailAutenticado);

    /** Contadores en una sola consulta. El rol USUARIO solo cuenta sus tickets. */
    ConteoTickets conteo(String emailAutenticado);

    /** Cuántos tickets creó cada persona. Solo lo puede ver el administrador. */
    List<TicketsPorUsuario> ticketsPorUsuario(String emailAutenticado);

    // Devuelve detalle.
    TicketVista detalle(Long ticketId, String emailAutenticado);

    /** Conocimientos publicados parecidos al texto (usa el buscador existente). */
    List<ConocimientoSugerido> sugerencias(String texto);
}
