package cl.casol.backend.catalogo.infrastructure.web.dto;

import cl.casol.backend.catalogo.domain.Sistema;

/**
 * Datos que se devuelven al frontend con sistema.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record SistemaResponse(Integer id, String nombre, String descripcion) {
    // Crea el objeto a partir de los datos recibidos.
    public static SistemaResponse from(Sistema sistema) {
        return new SistemaResponse(sistema.id(), sistema.nombre(), sistema.descripcion());
    }
}
