package cl.casol.backend.tickets.application.port.out;

import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.PrioridadTicket;

import java.time.LocalDateTime;
import java.util.List;

/** Fila del listado, ya con nombres, sin la descripción ni la solución. */
public record ResumenTicketFila(Long id, String codigo, String titulo, EstadoTicket estado, PrioridadTicket prioridad,
        Integer solicitanteId, String solicitanteNombre, Integer asignadoId, String asignadoNombre,
        LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion, List<Etiqueta> etiquetas) {
}
