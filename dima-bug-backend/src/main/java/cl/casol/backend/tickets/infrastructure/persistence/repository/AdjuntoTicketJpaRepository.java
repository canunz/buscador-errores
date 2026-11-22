package cl.casol.backend.tickets.infrastructure.persistence.repository;

import cl.casol.backend.tickets.infrastructure.persistence.entity.AdjuntoTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de adjunto ticket.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface AdjuntoTicketJpaRepository extends JpaRepository<AdjuntoTicketEntity, Long> {
    // Busca registros por ticket id, ordenados por id de menor a mayor.
    List<AdjuntoTicketEntity> findByTicketIdOrderByIdAsc(Long ticketId);
    // Busca registros por id y ticket id.
    Optional<AdjuntoTicketEntity> findByIdAndTicketId(Long id, Long ticketId);
}
