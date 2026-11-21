package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.application.port.out.ConocimientoSugerido;

/**
 * Datos que se devuelven al frontend con conocimiento sugerido.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ConocimientoSugeridoResponse(Integer id, String titulo, String hardware, String sistema, String modulo) {
    // Crea el objeto a partir de los datos recibidos.
    public static ConocimientoSugeridoResponse from(ConocimientoSugerido s) {
        return new ConocimientoSugeridoResponse(s.id(), s.titulo(), s.hardware(), s.sistema(), s.modulo());
    }
}