package cl.casol.backend.tickets.infrastructure.persistence.repository;

import cl.casol.backend.tickets.infrastructure.persistence.entity.ComentarioTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComentarioTicketJpaRepository extends JpaRepository<ComentarioTicketEntity, Long> {
    List<ComentarioTicketEntity> findAllByTicketIdOrderByFechaAscIdAsc(Long ticketId);

    @Query("select c.interno from ComentarioTicketEntity c where c.id = :id and c.ticketId = :ticketId")
    Optional<Boolean> internoDe(@Param("ticketId") Long ticketId, @Param("id") Long id);
}