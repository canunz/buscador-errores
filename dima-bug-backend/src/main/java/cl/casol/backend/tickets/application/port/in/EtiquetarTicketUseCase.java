package cl.casol.backend.tickets.application.port.in;

import java.util.Set;

public interface EtiquetarTicketUseCase {
    TicketVista etiquetar(Long ticketId, Set<Long> etiquetaIds, String emailAutenticado);
}