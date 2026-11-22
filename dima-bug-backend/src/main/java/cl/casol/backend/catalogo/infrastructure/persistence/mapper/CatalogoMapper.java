package cl.casol.backend.catalogo.infrastructure.persistence.mapper;

import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Modulo;
import cl.casol.backend.catalogo.domain.Sistema;
import cl.casol.backend.catalogo.infrastructure.persistence.entity.HardwareEntity;
import cl.casol.backend.catalogo.infrastructure.persistence.entity.ModuloEntity;
import cl.casol.backend.catalogo.infrastructure.persistence.entity.SistemaEntity;

/**
 * Convierte los datos de catálogo entre la base de datos o la API y el dominio.
 */
public final class CatalogoMapper {
    private CatalogoMapper() { }

    // Convierte la entidad al modelo del dominio.
    public static Sistema toDomain(SistemaEntity entity) {
        return new Sistema(entity.getId(), entity.getNombre(), entity.getDescripcion(), entity.isActivo());
    }

    // Convierte la entidad al modelo del dominio.
    public static Hardware toDomain(HardwareEntity entity) {
        return new Hardware(entity.getId(), entity.getNombre(), entity.getSistemaOperativo(), entity.isActivo());
    }

    // Convierte la entidad al modelo del dominio.
    public static Modulo toDomain(ModuloEntity entity) {
        Integer sistemaId = entity.getSistema() == null ? null : entity.getSistema().getId();
        return new Modulo(entity.getId(), sistemaId, entity.getNombre(), entity.isActivo());
    }
}
