package cl.casol.backend.catalogo.infrastructure.web.dto;

import cl.casol.backend.catalogo.domain.Modulo;

/**
 * Datos que se devuelven al frontend con módulo.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ModuloResponse(Integer id, Integer sistemaId, String nombre) {
    // Crea el objeto a partir de los datos recibidos.
    public static ModuloResponse from(Modulo modulo) {
        return new ModuloResponse(modulo.id(), modulo.sistemaId(), modulo.nombre());
    }
}
