package cl.casol.backend.seguimiento.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Usuario;

public record ResultadoUsuarioResponse(Integer id,String nombre,String email) {
    public static ResultadoUsuarioResponse from(Usuario u){return new ResultadoUsuarioResponse(
            u.getId(),u.getNombre(),u.getEmail());}
}
