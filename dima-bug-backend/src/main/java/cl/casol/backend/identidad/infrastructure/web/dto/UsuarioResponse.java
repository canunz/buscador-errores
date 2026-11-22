package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Usuario;

import java.time.LocalDateTime;

/**
 * Datos que se devuelven al frontend con usuario.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record UsuarioResponse(
        Integer id,
        String nombre,
        String email,
        boolean activo,
        Integer rolId,
        String rol,
        LocalDateTime fechaCreacion,
        LocalDateTime ultimoIngreso
) {
    // Crea el objeto a partir de los datos recibidos.
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.isActivo(),
                usuario.getRol().getId(), usuario.getRol().getNombre(),
                usuario.getFechaCreacion(), usuario.getUltimoIngreso()
        );
    }
}
