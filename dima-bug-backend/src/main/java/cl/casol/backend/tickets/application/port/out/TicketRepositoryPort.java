package cl.casol.backend.tickets.application.port.out;

import cl.casol.backend.tickets.domain.ConteoTickets;
import cl.casol.backend.tickets.domain.FiltroTickets;
import cl.casol.backend.tickets.domain.PaginaTickets;
import cl.casol.backend.tickets.domain.Ticket;

import java.util.List;
import java.util.Optional;

public interface TicketRepositoryPort {
    /** Guarda el ticket, sus etiquetas y los comentarios/historial nuevos (los que aún no tienen id). */
    Ticket guardar(Ticket ticket);

    /** Detalle completo: etiquetas, comentarios e historial. */
    Optional<Ticket> buscarPorId(Long id);

    /** Solo el solicitante, para autorizar una descarga sin armar el ticket. */
    Optional<Integer> solicitanteDe(Long ticketId);

    /** Vacío si el comentario no pertenece al ticket. */
    Optional<Boolean> comentarioInterno(Long ticketId, Long comentarioId);

    /** Listado paginado (sin comentarios ni historial), ordenado del más reciente al más antiguo. */
    PaginaTickets<Ticket> buscar(FiltroTickets filtro, int pagina, int tamano);

    /** Listado liviano: una sola consulta, sin descripción ni consultas extra de usuarios. */
    List<ResumenTicketFila> listarLigero(Integer solicitanteId, int limite);

    /** Totales por estado, críticos activos y sin técnico. solicitanteId null = todos los tickets. */
    ConteoTickets contar(Integer solicitanteId);

    /** Siguiente número para el código TK-año-NNNN. */
    int siguienteCorrelativo(int anio);
}
