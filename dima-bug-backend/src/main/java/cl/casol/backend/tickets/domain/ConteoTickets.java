package cl.casol.backend.tickets.domain;

import java.util.EnumMap;
import java.util.Map;

/** Totales para los contadores de la bandeja y el panel de inicio, en una sola consulta. */
public record ConteoTickets(Map<EstadoTicket, Long> porEstado, long criticosActivos, long sinTecnico) {

    public ConteoTickets {
        Map<EstadoTicket, Long> completo = new EnumMap<>(EstadoTicket.class);
        for (EstadoTicket estado : EstadoTicket.values()) {
            completo.put(estado, porEstado == null ? 0L : porEstado.getOrDefault(estado, 0L));
        }
        porEstado = Map.copyOf(completo);
    }

    public long total() {
        return porEstado.values().stream().mapToLong(Long::longValue).sum();
    }
}
