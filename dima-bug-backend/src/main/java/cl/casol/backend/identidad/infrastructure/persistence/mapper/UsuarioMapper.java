package cl.casol.backend.identidad.infrastructure.persistence.mapper;

import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.persistence.entity.RolEntity;
import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;

//Antes Jpa Repository. Paso 5: Evita que JPA se meta en nuestro dominio.
//Infrastructure puede hablar JPA;
public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static Usuario toDomain(UsuarioEntity entity) {

        if (entity == null) {
            return null;
        }

        Rol rol = toDomain(entity.getRol());

        return new Usuario(
                entity.getId(),
                rol,
                entity.getNombre(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.isActivo(),
                entity.getFechaCreacion(),
                entity.getFechaActualizacion()
        );
    }

    private static Rol toDomain(RolEntity entity) {

        if (entity == null) {
            return null;
        }

        return new Rol(
                entity.getId(),
                entity.getNombre(),
                entity.getDescripcion(),
                entity.isActivo(),
                entity.getFechaCreacion(),
                entity.getFechaActualizacion()
        );
    }
}