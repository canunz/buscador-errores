package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.TicketsPorUsuario;

/**
 * Fila del gráfico "Tickets por usuario" del panel del administrador.
 */
public record TicketsPorUsuarioResponse(Integer usuarioId, String nombre, String email,
        long total, long activos, long resueltos) {

    // Pasa los datos del dominio a la respuesta que recibe el frontend.
    public static TicketsPorUsuarioResponse from(TicketsPorUsuario fila) {
        return new TicketsPorUsuarioResponse(fila.usuarioId(), fila.nombre(), fila.email(),
                fila.total(), fila.activos(), fila.resueltos());
    }
}
