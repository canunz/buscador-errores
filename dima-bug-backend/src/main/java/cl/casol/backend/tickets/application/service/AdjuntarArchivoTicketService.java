package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.AdjuntarArchivoTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.AdjuntoRepositoryPort;
import cl.casol.backend.tickets.application.port.out.AlmacenAdjuntosPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.AdjuntoTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.AdjuntoNoEncontradoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Transactional
public class AdjuntarArchivoTicketService implements AdjuntarArchivoTicketUseCase {
    private final ContextoTicketService contexto;
    private final AdjuntoRepositoryPort adjuntos;
    private final AlmacenAdjuntosPort almacen;

    public AdjuntarArchivoTicketService(ContextoTicketService contexto, AdjuntoRepositoryPort adjuntos,
            AlmacenAdjuntosPort almacen) {
        this.contexto = contexto;
        this.adjuntos = adjuntos;
        this.almacen = almacen;
    }

    @Override
    public TicketVista adjuntar(Long ticketId, Long comentarioId, String nombre, byte[] contenido,
            String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        if (actor.esAdministrador()) {
            throw new TicketInvalidoException("El administrador supervisa los tickets, pero no adjunta archivos");
        }
        Ticket ticket = contexto.ticketVisible(ticketId, actor);
        ticket.validarAdmiteCambios();
        if (comentarioId != null) {
            boolean esSuyo = ticket.getComentarios().stream()
                    .anyMatch(c -> comentarioId.equals(c.id()) && Objects.equals(c.autorId(), actor.id()));
            if (!esSuyo) throw new TicketInvalidoException("Solo puedes adjuntar archivos a tus propios mensajes");
        }
        AdjuntoTicket.validarContenido(contenido);

        String referencia = almacen.guardar(contenido);
        try {
            adjuntos.guardar(AdjuntoTicket.nuevo(ticketId, comentarioId, nombre, contenido, referencia, actor.id(),
                    LocalDateTime.now()));
        } catch (RuntimeException ex) {
            almacen.eliminar(referencia);
            throw ex;
        }
        return contexto.detalle(ticket, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public ArchivoAdjunto descargar(Long ticketId, Long adjuntoId, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        contexto.exigirVisible(ticketId, actor);
        AdjuntoTicket adjunto = adjuntos.buscar(ticketId, adjuntoId)
                .filter(a -> !contexto.comentarioOculto(ticketId, a.comentarioId(), actor))
                .orElseThrow(() -> new AdjuntoNoEncontradoException(adjuntoId));
        return new ArchivoAdjunto(adjunto.nombre(), adjunto.mime(), almacen.leer(adjunto.referencia()));
    }
}
