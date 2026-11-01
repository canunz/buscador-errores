package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Usuario;

import java.time.LocalDateTime;

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
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.isActivo(),
                usuario.getRol().getId(), usuario.getRol().getNombre(),
                usuario.getFechaCreacion(), usuario.getUltimoIngreso()
        );
    }
}
