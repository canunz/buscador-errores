package cl.casol.backend.tickets.domain;

public enum EstadoTicket {
    ABIERTO,
    EN_PROGRESO,
    EN_ESPERA,
    RESUELTO,
    CERRADO,
    CANCELADO;

    public boolean esFinal() {
        return this == CERRADO || this == CANCELADO;
    }
}