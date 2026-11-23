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

/**
 * Caso de uso "crear ticket": aplica las reglas antes de guardar.
 * Usa puertos (interfaces) para guardar y consultar, sin saber que hay una base de datos detrás.
 */
@Service
@Transactional
public class CrearTicketService implements CrearTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final CatalogoConsultaPort catalogo;
    private final ContextoTicketService contexto;
    private final AvisoTicketCriticoPort avisoCritico;

    /** Sin aviso por correo (se usa en pruebas que no revisan el correo). */
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

    // Crea con los datos recibidos.
    @Override
    public TicketVista crear(Comando comando, String emailAutenticado) {
        // Quién está creando el ticket (sale del token)
        ActorTicket actor = contexto.actor(emailAutenticado);
        // Regla: el administrador supervisa, no crea tickets
        if (actor.esAdministrador()) {
            throw new AccesoTicketDenegadoException("El administrador supervisa los tickets, pero no los crea");
        }
        // Regla: en el portal básico se exigen los datos del solicitante
        if (actor.accesoBasico() && comando.datosSolicitante()==null)
            throw new TicketInvalidoException("Completa los datos del solicitante en el portal de solicitudes");
        if (actor.accesoBasico() && (comando.hardwareId()!=null || comando.sistemaId()!=null || comando.moduloId()!=null))
            throw new AccesoTicketDenegadoException("Clasificación interna no disponible");
        // Revisa que hardware, sistema y módulo calcen entre sí
        if (comando.hardwareId() != null || comando.sistemaId() != null || comando.moduloId() != null) {
            catalogo.validarClasificacion(comando.hardwareId(), comando.sistemaId(), comando.moduloId())
                    .ifPresent(motivo -> { throw new TicketInvalidoException(motivo); });
        }
        LocalDateTime ahora = LocalDateTime.now();
        int anio = ahora.getYear();
        // Genera el código correlativo, por ejemplo TK-2026-0004
        String codigo = Ticket.generarCodigo(anio, tickets.siguienteCorrelativo(anio));
        // Crea el ticket en estado ABIERTO
        Ticket nuevo = Ticket.crear(codigo, comando.titulo(), comando.descripcion(), actor.accesoBasico() ? cl.casol.backend.tickets.domain.PrioridadTicket.MEDIA : comando.prioridad(),
                actor.id(), comando.hardwareId(), comando.sistemaId(), comando.moduloId(), ahora);
        nuevo.registrarDatosSolicitante(comando.datosSolicitante());
        // Lo guarda (por el puerto del repositorio)
        Ticket guardado = tickets.guardar(nuevo);

        // Si nace con prioridad crítica, se avisa por correo a Soporte y al administrador
        if (guardado.getPrioridad() == PrioridadTicket.CRITICA) {
            avisoCritico.avisar(guardado, actor.id());
        }
        return contexto.detalle(guardado, actor);
    }
}
