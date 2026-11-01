package cl.casol.backend.tickets.application.port.out;

import cl.casol.backend.tickets.domain.Ticket;

/** Avisa a soporte por correo cuando un ticket queda con prioridad crítica. */
public interface AvisoTicketCriticoPort {

    /** Sin aviso (pruebas o cuando no hay correo configurado). */
    AvisoTicketCriticoPort NINGUNO = (ticket, actorId) -> { };

    /**
     * @param ticket  ticket ya guardado con prioridad crítica
     * @param actorId quien lo creó o le subió la prioridad (no se le avisa a sí mismo)
     */
    void avisar(Ticket ticket, Integer actorId);
}
