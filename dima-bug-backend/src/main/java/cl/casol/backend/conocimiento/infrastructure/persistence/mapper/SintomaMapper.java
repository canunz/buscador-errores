package cl.casol.backend.conocimiento.infrastructure.persistence.mapper;

import cl.casol.backend.conocimiento.domain.Sintoma;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SintomaEntity;

public final class SintomaMapper {
    private SintomaMapper() { }

    public static Sintoma toDomain(SintomaEntity entity) {
        return new Sintoma(entity.getId(), entity.getConocimientoId(), entity.getDescripcion(), entity.getOrden());
    }

    public static SintomaEntity toEntity(Sintoma sintoma) {
        return new SintomaEntity(sintoma.id(), sintoma.conocimientoId(), sintoma.descripcion(), sintoma.orden());
    }
}
