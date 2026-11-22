package cl.casol.backend.seguimiento.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Usuario;

/**
 * Datos que se devuelven al frontend con resultado usuario.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ResultadoUsuarioResponse(Integer id,String nombre,String email) {
    // Crea el objeto a partir de los datos recibidos.
    public static ResultadoUsuarioResponse from(Usuario u){return new ResultadoUsuarioResponse(
            u.getId(),u.getNombre(),u.getEmail());}
}
