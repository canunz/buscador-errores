package cl.casol.backend.tickets.infrastructure.persistence.adapter;

import cl.casol.backend.tickets.application.port.out.AdjuntoRepositoryPort;
import cl.casol.backend.tickets.domain.AdjuntoTicket;
import cl.casol.backend.tickets.infrastructure.persistence.entity.AdjuntoTicketEntity;
import cl.casol.backend.tickets.infrastructure.persistence.repository.AdjuntoTicketJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de base de datos para adjunto.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
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

    // Lista por ticket.
    @Override
    public List<AdjuntoTicket> listarPorTicket(Long ticketId) {
        return repository.findByTicketIdOrderByIdAsc(ticketId).stream().map(AdjuntoRepositoryAdapter::toDomain).toList();
    }

    // Busca el ticket indicado.
    @Override
    public Optional<AdjuntoTicket> buscar(Long ticketId, Long adjuntoId) {
        return repository.findByIdAndTicketId(adjuntoId, ticketId).map(AdjuntoRepositoryAdapter::toDomain);
    }

    // Convierte la entidad al modelo del dominio.
    private static AdjuntoTicket toDomain(AdjuntoTicketEntity e) {
        return new AdjuntoTicket(e.getId(), e.getTicketId(), e.getComentarioId(), e.getNombre(), e.getMime(),
                e.getTamano(), e.getReferencia(), e.getAutorId(), e.getFecha());
    }
}
