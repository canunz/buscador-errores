package cl.casol.backend.conocimiento.infrastructure.persistence.mapper;

import cl.casol.backend.conocimiento.domain.ConocimientoPrueba;
import cl.casol.backend.conocimiento.domain.Prueba;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoPruebaEntity;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoPruebaId;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.PruebaEntity;

/**
 * Convierte los datos de prueba entre la base de datos o la API y el dominio.
 */
public final class PruebaMapper {
    private PruebaMapper() { }

    // Convierte la entidad al modelo del dominio.
    public static Prueba toDomain(PruebaEntity entity) {
        return new Prueba(entity.getId(), entity.getDescripcion(), entity.getResultadoEsperado(), entity.isActiva());
    }

    // Convierte la entidad al modelo del dominio.
    public static ConocimientoPrueba toDomain(ConocimientoPruebaEntity entity) {
        return new ConocimientoPrueba(entity.getId().getConocimientoId(), toDomain(entity.getPrueba()),
                entity.getOrden());
    }

    // Convierte el modelo del dominio a la entidad de base de datos.
    public static ConocimientoPruebaEntity toEntity(ConocimientoPrueba asociacion, PruebaEntity prueba) {
        ConocimientoPruebaId id = new ConocimientoPruebaId(
                asociacion.conocimientoId(), asociacion.prueba().id());
        return new ConocimientoPruebaEntity(id, prueba, asociacion.orden());
    }
}
