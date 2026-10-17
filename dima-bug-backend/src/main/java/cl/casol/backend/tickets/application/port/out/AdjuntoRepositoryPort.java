package cl.casol.backend.tickets.application.port.out;

import cl.casol.backend.tickets.domain.AdjuntoTicket;

import java.util.List;
import java.util.Optional;

public interface AdjuntoRepositoryPort {
    AdjuntoTicket guardar(AdjuntoTicket adjunto);
    List<AdjuntoTicket> listarPorTicket(Long ticketId);
    Optional<AdjuntoTicket> buscar(Long ticketId, Long adjuntoId);
}
