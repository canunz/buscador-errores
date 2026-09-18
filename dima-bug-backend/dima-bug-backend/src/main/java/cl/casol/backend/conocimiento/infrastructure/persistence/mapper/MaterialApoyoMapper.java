package cl.casol.backend.conocimiento.infrastructure.persistence.mapper;

import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.MaterialApoyoEntity;

public final class MaterialApoyoMapper {
    private MaterialApoyoMapper() { }

    public static MaterialApoyo toDomain(MaterialApoyoEntity entity) {
        return new MaterialApoyo(entity.getId(), entity.getConocimientoId(), entity.getPasoId(),
                entity.getNombre(), entity.getTipo(), entity.getUrl());
    }

    public static MaterialApoyoEntity toEntity(MaterialApoyo material) {
        return new MaterialApoyoEntity(material.id(), material.conocimientoId(), null,
                material.nombre(), material.tipo(), material.url());
    }
}
