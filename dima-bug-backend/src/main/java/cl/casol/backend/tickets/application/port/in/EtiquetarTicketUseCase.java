package cl.casol.backend.tickets.application.port.in;

import java.util.Set;

/**
 * Puerto de entrada: define la acción "etiquetar ticket" que ofrece el módulo de tickets.
 * El controlador usa esta interfaz y el servicio la implementa.
 */
public interface EtiquetarTicketUseCase {
    // Etiqueta el ticket indicado.
    TicketVista etiquetar(Long ticketId, Set<Long> etiquetaIds, String emailAutenticado);
}