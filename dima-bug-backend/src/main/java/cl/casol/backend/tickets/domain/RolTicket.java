package cl.casol.backend.tickets.domain;

/** Rol con el que una persona actúa sobre los tickets. */
public enum RolTicket {
    USUARIO,
    SOPORTE,
    ADMINISTRADOR;

    public boolean esStaff() {
        return this == SOPORTE || this == ADMINISTRADOR;
    }
}