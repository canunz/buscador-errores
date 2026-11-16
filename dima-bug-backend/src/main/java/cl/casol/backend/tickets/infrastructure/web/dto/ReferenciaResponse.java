package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.application.port.out.UsuarioTicket;

/** Par id + nombre para usuarios y catálogos. */
public record ReferenciaResponse(Number id, String nombre) {
    public static ReferenciaResponse usuario(Integer id, UsuarioTicket usuario) {
        if (id == null) return null;
        return new ReferenciaResponse(id, usuario == null ? "Usuario " + id : usuario.nombre());
    }

    public static ReferenciaResponse catalogo(Integer id, String nombre) {
        return id == null ? null : new ReferenciaResponse(id, nombre);
    }
}