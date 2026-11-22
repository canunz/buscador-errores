package cl.casol.backend.tickets.application.port.out;

import cl.casol.backend.tickets.domain.AdjuntoTicket;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida de adjunto.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface AdjuntoRepositoryPort {
    // Guarda el adjunto.
    AdjuntoTicket guardar(AdjuntoTicket adjunto);
    // Lista por ticket.
    List<AdjuntoTicket> listarPorTicket(Long ticketId);
    // Busca el ticket indicado.
    Optional<AdjuntoTicket> buscar(Long ticketId, Long adjuntoId);
}
