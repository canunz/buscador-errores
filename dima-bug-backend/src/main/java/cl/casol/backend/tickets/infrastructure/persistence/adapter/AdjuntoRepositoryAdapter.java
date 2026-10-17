package cl.casol.backend.tickets.infrastructure.persistence.adapter;

import cl.casol.backend.tickets.application.port.out.AdjuntoRepositoryPort;
import cl.casol.backend.tickets.domain.AdjuntoTicket;
import cl.casol.backend.tickets.infrastructure.persistence.entity.AdjuntoTicketEntity;
import cl.casol.backend.tickets.infrastructure.persistence.repository.AdjuntoTicketJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AdjuntoRepositoryAdapter implements AdjuntoRepositoryPort {
    private final AdjuntoTicketJpaRepository repository;

    public AdjuntoRepositoryAdapter(AdjuntoTicketJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AdjuntoTicket guardar(AdjuntoTicket a) {
        return toDomain(repository.save(new AdjuntoTicketEntity(a.id(), a.ticketId(), a.comentarioId(), a.nombre(),
                a.mime(), a.tamano(), a.referencia(), a.autorId(), a.fecha())));
    }

    @Override
    public List<AdjuntoTicket> listarPorTicket(Long ticketId) {
        return repository.findByTicketIdOrderByIdAsc(ticketId).stream().map(AdjuntoRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<AdjuntoTicket> buscar(Long ticketId, Long adjuntoId) {
        return repository.findByIdAndTicketId(adjuntoId, ticketId).map(AdjuntoRepositoryAdapter::toDomain);
    }

    private static AdjuntoTicket toDomain(AdjuntoTicketEntity e) {
        return new AdjuntoTicket(e.getId(), e.getTicketId(), e.getComentarioId(), e.getNombre(), e.getMime(),
                e.getTamano(), e.getReferencia(), e.getAutorId(), e.getFecha());
    }
}
