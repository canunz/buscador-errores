package cl.casol.backend.conocimiento.infrastructure.persistence.mapper;

import cl.casol.backend.catalogo.domain.Frecuencia;
import cl.casol.backend.catalogo.infrastructure.persistence.entity.*;
import cl.casol.backend.catalogo.infrastructure.persistence.mapper.CatalogoMapper;
import cl.casol.backend.conocimiento.domain.Conocimiento;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoEntity;
import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.UsuarioMapper;

public final class ConocimientoMapper {
    private ConocimientoMapper() { }

    public static Conocimiento toDomain(ConocimientoEntity entity) {
        Frecuencia frecuencia = entity.getFrecuencia() == null ? null
                : new Frecuencia(entity.getFrecuencia().getId(), entity.getFrecuencia().getNombre(),
                entity.getFrecuencia().isActiva());
        return new Conocimiento(entity.getId(), entity.getTitulo(), entity.getDescripcion(), entity.getEstado(),
                entity.getHardware() == null ? null : CatalogoMapper.toDomain(entity.getHardware()),
                entity.getSistema() == null ? null : CatalogoMapper.toDomain(entity.getSistema()),
                entity.getModulo() == null ? null : CatalogoMapper.toDomain(entity.getModulo()), frecuencia,
                entity.getComentario(), UsuarioMapper.toDomain(entity.getCreadoPor()), entity.getFechaCreacion(),
                UsuarioMapper.toDomain(entity.getModificadoPor()), entity.getFechaModificacion());
    }

    public static ConocimientoEntity toEntity(Conocimiento c, HardwareEntity hardware, SistemaEntity sistema,
            ModuloEntity modulo, FrecuenciaEntity frecuencia, UsuarioEntity creadoPor,
            UsuarioEntity modificadoPor) {
        return new ConocimientoEntity(c.getId(), c.getTitulo(), c.getDescripcion(), c.getEstado(), hardware,
                sistema, modulo, frecuencia, c.getComentario(), creadoPor, c.getFechaCreacion(),
                modificadoPor, c.getFechaModificacion());
    }
}
