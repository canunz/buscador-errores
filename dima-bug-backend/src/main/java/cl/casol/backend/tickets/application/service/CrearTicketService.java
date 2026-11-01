package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.CrearTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.AvisoTicketCriticoPort;
import cl.casol.backend.tickets.application.port.out.CatalogoConsultaPort;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class CrearTicketService implements CrearTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final CatalogoConsultaPort catalogo;
    private final ContextoTicketService contexto;
    private final AvisoTicketCriticoPort avisoCritico;

    public CrearTicketService(TicketRepositoryPort tickets, CatalogoConsultaPort catalogo,
            ContextoTicketService contexto) {
        this(tickets, catalogo, contexto, AvisoTicketCriticoPort.NINGUNO);
    }

    @Autowired
    public CrearTicketService(TicketRepositoryPort tickets, CatalogoConsultaPort catalogo,
            ContextoTicketService contexto, AvisoTicketCriticoPort avisoCritico) {
        this.tickets = tickets;
        this.catalogo = catalogo;
        this.contexto = contexto;
        this.avisoCritico = avisoCritico;
    }

    @Override
    public TicketVista crear(Comando comando, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        if (actor.esAdministrador()) {
            throw new AccesoTicketDenegadoException("El administrador supervisa los tickets, pero no los crea");
        }
        if (comando.hardwareId() != null || comando.sistemaId() != null || comando.moduloId() != null) {
            catalogo.validarClasificacion(comando.hardwareId(), comando.sistemaId(), comando.moduloId())
                    .ifPresent(motivo -> { throw new TicketInvalidoException(motivo); });
        }
        LocalDateTime ahora = LocalDateTime.now();
        int anio = ahora.getYear();
        String codigo = Ticket.generarCodigo(anio, tickets.siguienteCorrelativo(anio));
        Ticket nuevo = Ticket.crear(codigo, comando.titulo(), comando.descripcion(), comando.prioridad(),
                actor.id(), comando.hardwareId(), comando.sistemaId(), comando.moduloId(), ahora);
        Ticket guardado = tickets.guardar(nuevo);
        if (guardado.getPrioridad() == PrioridadTicket.CRITICA) {
            avisoCritico.avisar(guardado, actor.id());
        }
        return contexto.detalle(guardado, actor);
    }
}
