package cl.casol.backend.tickets.domain;

/** Criterios de búsqueda del listado. solicitanteId se fuerza en el caso de uso para el rol USUARIO. */
public record FiltroTickets(EstadoTicket estado, PrioridadTicket prioridad, Long etiquetaId,
        Integer asignadoId, Integer solicitanteId, String texto) {
}