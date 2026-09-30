package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Rol;

public record RolResponse(Integer id, String nombre, String descripcion) {
    public static RolResponse from(Rol rol) {
        return new RolResponse(rol.getId(), rol.getNombre(), rol.getDescripcion());
    }
}
