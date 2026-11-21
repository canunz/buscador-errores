package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Rol;

/**
 * Datos que se devuelven al frontend con rol.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record RolResponse(Integer id, String nombre, String descripcion) {
    // Crea el objeto a partir de los datos recibidos.
    public static RolResponse from(Rol rol) {
        return new RolResponse(rol.getId(), rol.getNombre(), rol.getDescripcion());
    }
}
