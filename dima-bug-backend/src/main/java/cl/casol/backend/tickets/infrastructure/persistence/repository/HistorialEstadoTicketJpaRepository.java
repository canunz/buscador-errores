package cl.casol.backend.tickets.infrastructure.persistence.repository;

import cl.casol.backend.tickets.infrastructure.persistence.entity.HistorialEstadoTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialEstadoTicketJpaRepository extends JpaRepository<HistorialEstadoTicketEntity, Long> {
    List<HistorialEstadoTicketEntity> findAllByTicketIdOrderByFechaAscIdAsc(Long ticketId);
}