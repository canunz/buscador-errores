package cl.casol.backend.conocimiento.infrastructure.persistence.mapper;

import cl.casol.backend.conocimiento.domain.Causa;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.CausaEntity;

/**
 * Convierte los datos de causa entre la base de datos o la API y el dominio.
 */
public final class CausaMapper {
    private CausaMapper() { }

    // Convierte la entidad al modelo del dominio.
    public static Causa toDomain(CausaEntity entity) {
        return new Causa(entity.getId(), entity.getConocimientoId(), entity.getDescripcion(), entity.getOrden());
    }

    // Convierte el modelo del dominio a la entidad de base de datos.
    public static CausaEntity toEntity(Causa causa) {
        return new CausaEntity(causa.id(), causa.conocimientoId(), causa.descripcion(), causa.orden());
    }
}
