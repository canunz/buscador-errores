package cl.casol.backend.conocimiento.infrastructure.persistence.mapper;

import cl.casol.backend.conocimiento.domain.Sintoma;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SintomaEntity;

/**
 * Convierte los datos de síntoma entre la base de datos o la API y el dominio.
 */
public final class SintomaMapper {
    private SintomaMapper() { }

    // Convierte la entidad al modelo del dominio.
    public static Sintoma toDomain(SintomaEntity entity) {
        return new Sintoma(entity.getId(), entity.getConocimientoId(), entity.getDescripcion(), entity.getOrden());
    }

    // Convierte el modelo del dominio a la entidad de base de datos.
    public static SintomaEntity toEntity(Sintoma sintoma) {
        return new SintomaEntity(sintoma.id(), sintoma.conocimientoId(), sintoma.descripcion(), sintoma.orden());
    }
}
