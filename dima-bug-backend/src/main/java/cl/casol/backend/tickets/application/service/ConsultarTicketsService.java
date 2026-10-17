package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.ConsultarTicketsUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.ConocimientoPort;
import cl.casol.backend.tickets.application.port.out.ConocimientoSugerido;
import cl.casol.backend.tickets.application.port.out.DetalleTicketConsultaPort;
import cl.casol.backend.tickets.application.port.out.DetalleTicketPlano;
import cl.casol.backend.tickets.application.port.out.ResumenTicketFila;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.application.port.out.UsuarioTicket;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.AdjuntoTicket;
import cl.casol.backend.tickets.domain.ComentarioTicket;
import cl.casol.backend.tickets.domain.ConteoTickets;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.FiltroTickets;
import cl.casol.backend.tickets.domain.PaginaTickets;
import cl.casol.backend.tickets.domain.RolTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ConsultarTicketsService implements ConsultarTicketsUseCase {
    static final int TAMANO_POR_DEFECTO = 10;
    static final int TAMANO_MAXIMO = 100;
    static final int MAX_SUGERENCIAS = 5;
    static final int MIN_LARGO_SUGERENCIA = 3;

    private final TicketRepositoryPort tickets;
    private final ConocimientoPort conocimiento;
    private final ContextoTicketService contexto;
    private final DetalleTicketConsultaPort detalleRapido;

    @Autowired
    public ConsultarTicketsService(TicketRepositoryPort tickets, ConocimientoPort conocimiento,
            ContextoTicketService contexto, DetalleTicketConsultaPort detalleRapido) {
        this.tickets = tickets;
        this.conocimiento = conocimiento;
        this.contexto = contexto;
        this.detalleRapido = detalleRapido;
    }

    /** Los tests siguen el camino anterior cuando no hay lectura directa. */
    ConsultarTicketsService(TicketRepositoryPort tickets, ConocimientoPort conocimiento,
            ContextoTicketService contexto) {
        this(tickets, conocimiento, contexto, id -> java.util.Optional.empty());
    }

    @Override
    public PaginaTickets<TicketVista> listar(FiltroTickets filtro, int pagina, int tamano, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        FiltroTickets base = filtro == null ? new FiltroTickets(null, null, null, null, null, null) : filtro;
        String texto = base.texto() == null || base.texto().isBlank() ? null : base.texto().strip();
        // Regla de visibilidad en el caso de uso: el rol USUARIO siempre queda limitado a sus tickets.
        Integer solicitanteId = actor.esStaff() ? base.solicitanteId() : actor.id();
        FiltroTickets efectivo = new FiltroTickets(base.estado(), base.prioridad(), base.etiquetaId(),
                base.asignadoId(), solicitanteId, texto);

        int paginaUtil = Math.max(pagina, 0);
        int tamanoUtil = tamano <= 0 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
        if (paginaUtil == 0 && efectivo.estado() == null && efectivo.prioridad() == null
                && efectivo.etiquetaId() == null && efectivo.asignadoId() == null && texto == null) {
            List<TicketVista> vistas = tickets.listarLigero(solicitanteId, tamanoUtil).stream()
                    .map(ConsultarTicketsService::vistaLigera).toList();
            return new PaginaTickets<>(vistas, 0, tamanoUtil, vistas.size());
        }
        PaginaTickets<Ticket> resultado = tickets.buscar(efectivo, paginaUtil, tamanoUtil);
        return new PaginaTickets<>(contexto.resumenes(resultado.contenido()), resultado.pagina(),
                resultado.tamano(), resultado.totalElementos());
    }

    @Override
    public ConteoTickets conteo(String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        return tickets.contar(actor.esStaff() ? null : actor.id());
    }

    @Override
    public TicketVista detalle(Long ticketId, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        return detalleRapido.buscar(ticketId)
                .map(plano -> vista(plano, actor, ticketId))
                .orElseGet(() -> contexto.detalle(contexto.ticketVisible(ticketId, actor), actor));
    }

    private static TicketVista vista(DetalleTicketPlano plano, ActorTicket actor, Long ticketId) {
        Ticket ticket = plano.ticket();
        if (!ticket.puedeVer(actor)) throw new TicketNoEncontradoException(ticketId);
        List<ComentarioTicket> comentarios = ticket.comentariosVisiblesPara(actor);
        List<AdjuntoTicket> adjuntos = plano.adjuntos().stream()
                .filter(adjunto -> adjunto.visiblePara(actor, ticket)).toList();
        return new TicketVista(ticket, plano.etiquetas(), plano.usuarios(), plano.clasificacion(),
                plano.conocimiento(), comentarios, adjuntos);
    }

    private static TicketVista vistaLigera(ResumenTicketFila fila) {
        Ticket ticket = new Ticket(fila.id(), fila.codigo(), fila.titulo(), "", fila.estado(), fila.prioridad(),
                fila.solicitanteId(), fila.asignadoId(), null, null, null, null, null,
                fila.fechaCreacion(), fila.fechaActualizacion(), null, null,
                fila.etiquetas().stream().map(Etiqueta::getId).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll),
                List.of(), List.of());
        Map<Integer, UsuarioTicket> usuarios = new HashMap<>();
        if (fila.solicitanteId() != null) {
            usuarios.put(fila.solicitanteId(), new UsuarioTicket(fila.solicitanteId(), fila.solicitanteNombre(), null,
                    RolTicket.USUARIO, true));
        }
        if (fila.asignadoId() != null) {
            usuarios.put(fila.asignadoId(), new UsuarioTicket(fila.asignadoId(), fila.asignadoNombre(), null,
                    RolTicket.SOPORTE, true));
        }
        return new TicketVista(ticket, fila.etiquetas(), usuarios, null, null, List.of());
    }

    @Override
    public List<ConocimientoSugerido> sugerencias(String texto) {
        if (texto == null || texto.strip().length() < MIN_LARGO_SUGERENCIA) return List.of();
        return conocimiento.sugerir(texto.strip(), MAX_SUGERENCIAS);
    }
}
