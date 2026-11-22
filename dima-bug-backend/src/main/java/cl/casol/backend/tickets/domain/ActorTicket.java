package cl.casol.backend.tickets.domain;

/** Persona autenticada que ejecuta una acción sobre un ticket. */
public record ActorTicket(Integer id, String nombre, RolTicket rol, boolean accesoBasico) {
    public ActorTicket(Integer id, String nombre, RolTicket rol) { this(id,nombre,rol,false); }

    // Indica si es administrador.
    public boolean esAdministrador() { return rol == RolTicket.ADMINISTRADOR; }

    // Indica si es staff.
    public boolean esStaff() { return rol != null && rol.esStaff(); }

    /** Soporte que atiende tickets. El administrador ve todo, pero solo supervisa. */
    public boolean esSoporte() { return esStaff() && !esAdministrador(); }

    // Indica si es asignado de.
    public boolean esAsignadoDe(Ticket ticket) {
        return id != null && id.equals(ticket.getAsignadoId());
    }

    // Indica si es solicitante de.
    public boolean esSolicitanteDe(Ticket ticket) {
        return id != null && id.equals(ticket.getSolicitanteId());
    }
}
