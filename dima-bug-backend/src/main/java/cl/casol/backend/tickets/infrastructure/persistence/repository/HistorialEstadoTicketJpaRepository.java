package cl.casol.backend.tickets.infrastructure.persistence.repository;

import cl.casol.backend.tickets.infrastructure.persistence.entity.HistorialEstadoTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio Spring Data de historial estado ticket.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface HistorialEstadoTicketJpaRepository extends JpaRepository<HistorialEstadoTicketEntity, Long> {
    // Busca todos los registros por ticket id, ordenados por fecha de menor a mayor id de menor a mayor.
    List<HistorialEstadoTicketEntity> findAllByTicketIdOrderByFechaAscIdAsc(Long ticketId);
}