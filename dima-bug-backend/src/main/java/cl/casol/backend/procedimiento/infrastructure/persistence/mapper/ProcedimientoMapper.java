package cl.casol.backend.procedimiento.infrastructure.persistence.mapper;

import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.UsuarioMapper;
import cl.casol.backend.procedimiento.domain.Procedimiento;
import cl.casol.backend.procedimiento.infrastructure.persistence.entity.ProcedimientoEntity;

public final class ProcedimientoMapper {
    private ProcedimientoMapper() { }
    public static Procedimiento toDomain(ProcedimientoEntity e) {
        return new Procedimiento(e.getId(), e.getNombre(), e.getDescripcion(), e.getEstado(),
                UsuarioMapper.toDomain(e.getCreadoPor()), e.getFechaCreacion(),
                UsuarioMapper.toDomain(e.getModificadoPor()), e.getFechaModificacion());
    }
    public static ProcedimientoEntity toEntity(Procedimiento p, UsuarioEntity creador, UsuarioEntity modificador) {
        return new ProcedimientoEntity(p.id(), p.nombre(), p.descripcion(), p.estado(), creador,
                p.fechaCreacion(), modificador, p.fechaModificacion());
    }
}
