package cl.casol.backend.tickets.application.port.out;

import cl.casol.backend.tickets.domain.RolTicket;

/** Lo que el módulo de tickets necesita saber de un usuario. */
public record UsuarioTicket(Integer id, String nombre, String email, RolTicket rol, boolean activo) {
}