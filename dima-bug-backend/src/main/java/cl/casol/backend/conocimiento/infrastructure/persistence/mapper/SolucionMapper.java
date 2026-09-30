package cl.casol.backend.conocimiento.infrastructure.persistence.mapper;

import cl.casol.backend.conocimiento.domain.Solucion;
import cl.casol.backend.conocimiento.domain.SolucionAsignacion;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SolucionAsignacionEntity;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SolucionEntity;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.OrganizacionMapper;

public final class SolucionMapper {
    private SolucionMapper() { }
    public static Solucion toDomain(SolucionEntity e) {
        return new Solucion(e.getId(), e.getConocimientoId(), e.getDescripcion(), e.getTipo(), e.getOrden());
    }
    public static SolucionEntity toEntity(Solucion s) {
        return new SolucionEntity(s.id(), s.conocimientoId(), s.descripcion(), s.tipo(), s.orden());
    }
    public static SolucionAsignacion toDomain(SolucionAsignacionEntity e) {
        return new SolucionAsignacion(e.getId(), e.getSolucion().getId(),
                e.getResponsable() == null ? null : OrganizacionMapper.toDomain(e.getResponsable()),
                e.getDepartamento() == null ? null : OrganizacionMapper.toDomain(e.getDepartamento()),
                e.isPrincipal());
    }
}
