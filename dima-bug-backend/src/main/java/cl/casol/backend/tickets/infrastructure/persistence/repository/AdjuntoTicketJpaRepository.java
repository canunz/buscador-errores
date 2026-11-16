package cl.casol.backend.tickets.infrastructure.persistence.repository;

import cl.casol.backend.tickets.infrastructure.persistence.entity.AdjuntoTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdjuntoTicketJpaRepository extends JpaRepository<AdjuntoTicketEntity, Long> {
    List<AdjuntoTicketEntity> findByTicketIdOrderByIdAsc(Long ticketId);
    Optional<AdjuntoTicketEntity> findByIdAndTicketId(Long id, Long ticketId);
}
