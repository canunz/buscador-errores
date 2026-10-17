package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.EtiquetarTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.EtiquetaRepositoryPort;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class EtiquetarTicketService implements EtiquetarTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final EtiquetaRepositoryPort etiquetas;
    private final ContextoTicketService contexto;

    public EtiquetarTicketService(TicketRepositoryPort tickets, EtiquetaRepositoryPort etiquetas,
            ContextoTicketService contexto) {
        this.tickets = tickets;
        this.etiquetas = etiquetas;
        this.contexto = contexto;
    }

    @Override
    public TicketVista etiquetar(Long ticketId, Set<Long> etiquetaIds, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        contexto.exigirSoporte(actor, "etiquetar tickets");
        Ticket ticket = contexto.ticketVisible(ticketId, actor);

        Set<Long> pedidas = etiquetaIds == null ? Set.of()
                : etiquetaIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!pedidas.isEmpty()) {
            Map<Long, Etiqueta> encontradas = etiquetas.buscarPorIds(pedidas).stream()
                    .collect(Collectors.toMap(Etiqueta::getId, Function.identity()));
            List<Long> faltantes = pedidas.stream().filter(id -> !encontradas.containsKey(id)).toList();
            if (!faltantes.isEmpty()) throw new TicketInvalidoException("No existen las etiquetas " + faltantes);
            // Una etiqueta desactivada puede quedarse en un ticket que ya la tenía, pero no se puede agregar.
            boolean agregaInactiva = pedidas.stream()
                    .anyMatch(id -> !encontradas.get(id).isActiva() && !ticket.getEtiquetaIds().contains(id));
            if (agregaInactiva) throw new TicketInvalidoException("No se pueden agregar etiquetas desactivadas");
        }

        ticket.etiquetar(pedidas, LocalDateTime.now());
        return contexto.detalle(tickets.guardar(ticket), actor);
    }
}
