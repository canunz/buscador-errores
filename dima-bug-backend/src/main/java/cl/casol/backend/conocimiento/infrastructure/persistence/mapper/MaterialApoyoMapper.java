package cl.casol.backend.conocimiento.infrastructure.persistence.mapper;

import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.MaterialApoyoEntity;

/**
 * Convierte los datos de material apoyo entre la base de datos o la API y el dominio.
 */
public final class MaterialApoyoMapper {
    private MaterialApoyoMapper() { }

    // Convierte la entidad al modelo del dominio.
    public static MaterialApoyo toDomain(MaterialApoyoEntity entity) {
        return new MaterialApoyo(entity.getId(), entity.getConocimientoId(), entity.getPasoId(),
                entity.getNombre(), entity.getTipo(), entity.getUrl());
    }

    // Convierte el modelo del dominio a la entidad de base de datos.
    public static MaterialApoyoEntity toEntity(MaterialApoyo material) {
        return new MaterialApoyoEntity(material.id(), material.conocimientoId(), material.pasoId(),
                material.nombre(), material.tipo(), material.url());
    }
}
