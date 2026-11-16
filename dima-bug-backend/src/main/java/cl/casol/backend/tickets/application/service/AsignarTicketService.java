package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.AsignarTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.application.port.out.UsuarioConsultaPort;
import cl.casol.backend.tickets.application.port.out.UsuarioTicket;
import cl.casol.backend.tickets.domain.RolTicket;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class AsignarTicketService implements AsignarTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final ContextoTicketService contexto;

    private final UsuarioConsultaPort usuarios;

    public AsignarTicketService(TicketRepositoryPort tickets, UsuarioConsultaPort usuarios,
            ContextoTicketService contexto) {
        this.usuarios = usuarios;
        this.tickets = tickets;
        this.contexto = contexto;
    }

    @Override
    public TicketVista asignar(Long ticketId, Integer asignadoId, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        contexto.exigirStaff(actor, "asignar tickets");
        if (asignadoId == null) throw new TicketInvalidoException("Debe indicar el técnico asignado");
        Ticket ticket = contexto.ticketVisible(ticketId, actor);

        if (actor.esAdministrador()) {
            // El administrador reparte el trabajo: asigna o reasigna a un técnico de soporte activo.
            UsuarioTicket tecnico = usuarios.buscarPorId(asignadoId)
                    .filter(u -> u.activo() && u.rol() != null && u.rol().esStaff() && u.rol() != RolTicket.ADMINISTRADOR)
                    .orElseThrow(() -> new TicketInvalidoException("El usuario indicado no es un técnico de soporte activo"));
            asignadoId = tecnico.id();
        } else if (!asignadoId.equals(actor.id())) {
            // Soporte toma sus propios tickets ("Tomar" o "Asignármelo").
            throw new AccesoTicketDenegadoException("Solo puedes asignarte tickets a ti mismo");
        }

        ticket.asignar(asignadoId, actor.id(), LocalDateTime.now());
        return contexto.detalle(tickets.guardar(ticket), actor);
    }
}
