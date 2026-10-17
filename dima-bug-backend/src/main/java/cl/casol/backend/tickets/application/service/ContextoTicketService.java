package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.AdjuntoRepositoryPort;
import cl.casol.backend.tickets.application.port.out.CatalogoConsultaPort;
import cl.casol.backend.tickets.application.port.out.ClasificacionTicket;
import cl.casol.backend.tickets.application.port.out.ConocimientoPort;
import cl.casol.backend.tickets.application.port.out.ConocimientoReferencia;
import cl.casol.backend.tickets.application.port.out.EtiquetaRepositoryPort;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.application.port.out.UsuarioConsultaPort;
import cl.casol.backend.tickets.application.port.out.UsuarioTicket;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.AdjuntoTicket;
import cl.casol.backend.tickets.domain.ComentarioTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.HistorialEstadoTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Apoyo común de los casos de uso: identifica a la persona autenticada, aplica la regla
 * "el usuario solo ve sus tickets" y arma la vista con nombres y etiquetas.
 */
@Service
public class ContextoTicketService {
    private final TicketRepositoryPort tickets;
    private final EtiquetaRepositoryPort etiquetas;
    private final UsuarioConsultaPort usuarios;
    private final CatalogoConsultaPort catalogo;
    private final ConocimientoPort conocimiento;
    private final AdjuntoRepositoryPort adjuntos;

    public ContextoTicketService(TicketRepositoryPort tickets, EtiquetaRepositoryPort etiquetas,
            UsuarioConsultaPort usuarios, CatalogoConsultaPort catalogo, ConocimientoPort conocimiento,
            AdjuntoRepositoryPort adjuntos) {
        this.tickets = tickets;
        this.etiquetas = etiquetas;
        this.usuarios = usuarios;
        this.catalogo = catalogo;
        this.conocimiento = conocimiento;
        this.adjuntos = adjuntos;
    }

    public ActorTicket actor(String emailAutenticado) {
        UsuarioTicket usuario = usuarios.buscarPorEmail(emailAutenticado)
                .filter(UsuarioTicket::activo)
                .orElseThrow(() -> new AccesoTicketDenegadoException("Tu usuario no está activo"));
        return new ActorTicket(usuario.id(), usuario.nombre(), usuario.rol());
    }

    /** Autoriza ver el ticket con una sola columna, sin comentarios ni historial. */
    public void exigirVisible(Long ticketId, ActorTicket actor) {
        Integer solicitante = tickets.solicitanteDe(ticketId)
                .orElseThrow(() -> new TicketNoEncontradoException(ticketId));
        if (!actor.esStaff() && !Objects.equals(actor.id(), solicitante)) {
            throw new TicketNoEncontradoException(ticketId);
        }
    }

    /** Nota interna: quien no es soporte no puede ver el archivo de ese mensaje. */
    public boolean comentarioOculto(Long ticketId, Long comentarioId, ActorTicket actor) {
        if (comentarioId == null || actor.esStaff()) return false;
        return tickets.comentarioInterno(ticketId, comentarioId).orElse(true);
    }

    /** Un usuario que pide un ticket ajeno recibe 404, igual que si no existiera. */
    public Ticket ticketVisible(Long ticketId, ActorTicket actor) {
        Ticket ticket = tickets.buscarPorId(ticketId).orElseThrow(() -> new TicketNoEncontradoException(ticketId));
        if (!ticket.puedeVer(actor)) throw new TicketNoEncontradoException(ticketId);
        return ticket;
    }

    public void exigirStaff(ActorTicket actor, String accion) {
        if (!actor.esStaff()) throw new AccesoTicketDenegadoException("Solo soporte puede " + accion);
    }

    /** Acciones de atención: solo soporte. El administrador supervisa, pero no modifica tickets. */
    public void exigirSoporte(ActorTicket actor, String accion) {
        if (actor.esAdministrador()) {
            throw new AccesoTicketDenegadoException("El administrador supervisa los tickets, pero no puede " + accion);
        }
        exigirStaff(actor, accion);
    }

    public void exigirSolicitante(ActorTicket actor, Ticket ticket, String accion) {
        if (!actor.esSolicitanteDe(ticket)) {
            throw new AccesoTicketDenegadoException("Solo quien creó el ticket puede " + accion);
        }
    }

    /** Cierre: lo confirma el solicitante o, si nunca responde, el técnico que lo resolvió. */
    public void exigirSolicitanteOTecnicoAsignado(ActorTicket actor, Ticket ticket, String accion) {
        if (!actor.esSolicitanteDe(ticket) && !(actor.esSoporte() && actor.esAsignadoDe(ticket))) {
            throw new AccesoTicketDenegadoException("Solo quien creó el ticket o el técnico a cargo puede " + accion);
        }
    }

    public void exigirAdministrador(ActorTicket actor, String accion) {
        if (!actor.esAdministrador()) throw new AccesoTicketDenegadoException("Solo un administrador puede " + accion);
    }


    public TicketVista detalle(Ticket ticket, ActorTicket actor) {
        List<ComentarioTicket> comentarios = ticket.comentariosVisiblesPara(actor);
        List<AdjuntoTicket> archivos = ticket.getId() == null ? List.of()
                : adjuntos.listarPorTicket(ticket.getId()).stream().filter(a -> a.visiblePara(actor, ticket)).toList();
        Set<Integer> ids = new HashSet<>();
        archivos.forEach(a -> ids.add(a.autorId()));
        ids.add(ticket.getSolicitanteId());
        ids.add(ticket.getAsignadoId());
        comentarios.forEach(c -> ids.add(c.autorId()));
        ticket.getHistorial().stream().map(HistorialEstadoTicket::usuarioId).forEach(ids::add);
        ids.remove(null);

        ClasificacionTicket clasificacion = ticket.getHardwareId() == null && ticket.getSistemaId() == null
                && ticket.getModuloId() == null
                ? ClasificacionTicket.VACIA
                : catalogo.nombres(ticket.getHardwareId(), ticket.getSistemaId(), ticket.getModuloId());
        ConocimientoReferencia referencia = ticket.getConocimientoId() == null ? null
                : conocimiento.buscar(ticket.getConocimientoId()).orElse(null);

        return new TicketVista(ticket, etiquetasOrdenadas(ticket.getEtiquetaIds()), usuarios.buscarPorIds(ids),
                clasificacion, referencia, comentarios, archivos);
    }

    public List<TicketVista> resumenes(List<Ticket> lista) {
        Set<Integer> idsUsuarios = new HashSet<>();
        Set<Long> idsEtiquetas = new HashSet<>();
        for (Ticket ticket : lista) {
            idsUsuarios.add(ticket.getSolicitanteId());
            idsUsuarios.add(ticket.getAsignadoId());
            idsEtiquetas.addAll(ticket.getEtiquetaIds());
        }
        idsUsuarios.remove(null);
        Map<Integer, UsuarioTicket> nombres = idsUsuarios.isEmpty() ? Map.of() : usuarios.buscarPorIds(idsUsuarios);
        Map<Long, Etiqueta> porId = idsEtiquetas.isEmpty() ? Map.of()
                : etiquetas.buscarPorIds(idsEtiquetas).stream()
                .collect(Collectors.toMap(Etiqueta::getId, Function.identity()));

        return lista.stream().map(ticket -> new TicketVista(ticket,
                ticket.getEtiquetaIds().stream().map(porId::get).filter(Objects::nonNull)
                        .sorted(Comparator.comparing(Etiqueta::getNombre, String.CASE_INSENSITIVE_ORDER)).toList(),
                nombres, null, null, List.of())).toList();
    }

    private List<Etiqueta> etiquetasOrdenadas(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return etiquetas.buscarPorIds(ids).stream()
                .sorted(Comparator.comparing(Etiqueta::getNombre, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
