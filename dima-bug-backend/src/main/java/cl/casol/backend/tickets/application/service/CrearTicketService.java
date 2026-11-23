package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.CrearTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.CatalogoConsultaPort;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class CrearTicketService implements CrearTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final CatalogoConsultaPort catalogo;
    private final ContextoTicketService contexto;

    public CrearTicketService(TicketRepositoryPort tickets, CatalogoConsultaPort catalogo,
            ContextoTicketService contexto) {
        this.tickets = tickets;
        this.catalogo = catalogo;
        this.contexto = contexto;
    }

    @Override
    public TicketVista crear(Comando comando, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        if (actor.esAdministrador()) {
            throw new AccesoTicketDenegadoException("El administrador supervisa los tickets, pero no los crea");
        }
        if (actor.accesoBasico() && comando.datosSolicitante()==null)
            throw new TicketInvalidoException("Completa los datos del solicitante en el portal de solicitudes");
        if (actor.accesoBasico() && (comando.hardwareId()!=null || comando.sistemaId()!=null || comando.moduloId()!=null))
            throw new AccesoTicketDenegadoException("Clasificación interna no disponible");
        if (comando.hardwareId() != null || comando.sistemaId() != null || comando.moduloId() != null) {
            catalogo.validarClasificacion(comando.hardwareId(), comando.sistemaId(), comando.moduloId())
                    .ifPresent(motivo -> { throw new TicketInvalidoException(motivo); });
        }
        LocalDateTime ahora = LocalDateTime.now();
        int anio = ahora.getYear();
        String codigo = Ticket.generarCodigo(anio, tickets.siguienteCorrelativo(anio));
        Ticket nuevo = Ticket.crear(codigo, comando.titulo(), comando.descripcion(), actor.accesoBasico() ? cl.casol.backend.tickets.domain.PrioridadTicket.MEDIA : comando.prioridad(),
                actor.id(), comando.hardwareId(), comando.sistemaId(), comando.moduloId(), ahora);
        nuevo.registrarDatosSolicitante(comando.datosSolicitante());
        return contexto.detalle(tickets.guardar(nuevo), actor);
    }
}
