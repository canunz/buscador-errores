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

/** Caso de uso de «Tomar» / asignar: quién puede hacerlo y el saludo al usuario. */
@Service
@Transactional
public class AsignarTicketService implements AsignarTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final ContextoTicketService contexto;
    private final PresentacionTecnicoTicket presentacion;

    private final UsuarioConsultaPort usuarios;

    public AsignarTicketService(TicketRepositoryPort tickets, UsuarioConsultaPort usuarios,
            ContextoTicketService contexto, PresentacionTecnicoTicket presentacion) {
        this.usuarios = usuarios;
        this.tickets = tickets;
        this.contexto = contexto;
        this.presentacion = presentacion;
    }

    // Asigna el ticket indicado.
    @Override
    public TicketVista asignar(Long ticketId, Integer asignadoId, String emailAutenticado) {
        // Quién está logueado (soporte o administrador).
        ActorTicket actor = contexto.actor(emailAutenticado);
        contexto.exigirStaff(actor, "asignar tickets");
        if (asignadoId == null) throw new TicketInvalidoException("Debe indicar el técnico asignado");
        Ticket ticket = contexto.ticketVisible(ticketId, actor);

        if (actor.esAdministrador()) {
            // El admin reparte trabajo: solo a un técnico de soporte activo, no a otro admin ni a un usuario.
            UsuarioTicket tecnico = usuarios.buscarPorId(asignadoId)
                    .filter(u -> u.activo() && u.rol() != null && u.rol().esStaff() && u.rol() != RolTicket.ADMINISTRADOR)
                    .orElseThrow(() -> new TicketInvalidoException("El usuario indicado no es un técnico de soporte activo"));
            asignadoId = tecnico.id();
        } else if (!asignadoId.equals(actor.id())) {
            // Un técnico solo puede tomarlo él: no se lo pasa a un compañero desde aquí.
            throw new AccesoTicketDenegadoException("Solo puedes asignarte tickets a ti mismo");
        }

        Integer anterior = ticket.getAsignadoId();
        LocalDateTime ahora = LocalDateTime.now();
        // En el dominio: queda asignado y, si estaba ABIERTO, pasa a EN_PROGRESO.
        ticket.asignar(asignadoId, actor.id(), ahora);
        // Si cambió el técnico, se publica el «Hola, soy…» en la conversación.
        presentacion.siCambioAsignado(ticket, anterior, asignadoId, ahora);
        return contexto.detalle(tickets.guardar(ticket), actor);
    }
}
