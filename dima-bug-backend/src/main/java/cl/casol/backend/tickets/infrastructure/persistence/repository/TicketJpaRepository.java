package cl.casol.backend.tickets.infrastructure.persistence.repository;

import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.infrastructure.persistence.entity.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketJpaRepository extends JpaRepository<TicketEntity, Long>, JpaSpecificationExecutor<TicketEntity> {

    @Query("select t.solicitanteId from TicketEntity t where t.id = :id")
    Optional<Integer> solicitanteDe(@Param("id") Long id);

    @Query("select t.estado, count(t) from TicketEntity t where (:sol is null or t.solicitanteId = :sol) group by t.estado")
    List<Object[]> contarPorEstado(@Param("sol") Integer solicitanteId);

    @Query("select count(t) from TicketEntity t where t.prioridad = :prioridad and t.estado in :estados"
            + " and (:sol is null or t.solicitanteId = :sol)")
    long contarPrioridadEnEstados(@Param("prioridad") PrioridadTicket prioridad,
            @Param("estados") Collection<EstadoTicket> estados, @Param("sol") Integer solicitanteId);

    @Query("select count(t) from TicketEntity t where t.asignadoId is null and t.estado = :estado"
            + " and (:sol is null or t.solicitanteId = :sol)")
    long contarSinAsignar(@Param("estado") EstadoTicket estado, @Param("sol") Integer solicitanteId);

    /** Mayor correlativo usado en el año (TK-2026-0001 → 1). */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(codigo, :inicio) AS UNSIGNED)), 0)
              FROM ticket
             WHERE codigo LIKE CONCAT(:prefijo, '%')
            """, nativeQuery = true)
    Number maximoCorrelativo(@Param("prefijo") String prefijo, @Param("inicio") int inicio);
}
