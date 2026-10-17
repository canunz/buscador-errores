package cl.casol.backend.tickets.domain;

/** Persona autenticada que ejecuta una acción sobre un ticket. */
public record ActorTicket(Integer id, String nombre, RolTicket rol) {

    public boolean esAdministrador() { return rol == RolTicket.ADMINISTRADOR; }

    public boolean esStaff() { return rol != null && rol.esStaff(); }

    /** Soporte que atiende tickets. El administrador ve todo, pero solo supervisa. */
    public boolean esSoporte() { return esStaff() && !esAdministrador(); }

    public boolean esAsignadoDe(Ticket ticket) {
        return id != null && id.equals(ticket.getAsignadoId());
    }

    public boolean esSolicitanteDe(Ticket ticket) {
        return id != null && id.equals(ticket.getSolicitanteId());
    }
}
