package cl.casol.backend.tickets.domain;

/** Estados posibles de un ticket, en el orden normal del flujo. */
public enum EstadoTicket {
    ABIERTO,
    EN_PROGRESO,
    EN_ESPERA,
    RESUELTO,
    CERRADO,
    CANCELADO;

    /** Cerrado y cancelado son finales: ya no admiten cambios. */
    public boolean esFinal() {
        return this == CERRADO || this == CANCELADO;
    }
}