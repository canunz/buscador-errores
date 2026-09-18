package cl.casol.backend.conocimiento.infrastructure.persistence.mapper;

import cl.casol.backend.conocimiento.domain.Causa;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.CausaEntity;

public final class CausaMapper {
    private CausaMapper() { }

    public static Causa toDomain(CausaEntity entity) {
        return new Causa(entity.getId(), entity.getConocimientoId(), entity.getDescripcion(), entity.getOrden());
    }

    public static CausaEntity toEntity(Causa causa) {
        return new CausaEntity(causa.id(), causa.conocimientoId(), causa.descripcion(), causa.orden());
    }
}
