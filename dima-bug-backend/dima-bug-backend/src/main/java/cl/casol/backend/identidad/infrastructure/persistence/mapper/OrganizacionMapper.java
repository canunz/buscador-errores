package cl.casol.backend.identidad.infrastructure.persistence.mapper;

import cl.casol.backend.identidad.domain.Departamento;
import cl.casol.backend.identidad.domain.DepartamentoContacto;
import cl.casol.backend.identidad.domain.Responsable;
import cl.casol.backend.identidad.infrastructure.persistence.entity.DepartamentoContactoEntity;
import cl.casol.backend.identidad.infrastructure.persistence.entity.DepartamentoEntity;
import cl.casol.backend.identidad.infrastructure.persistence.entity.ResponsableEntity;

public final class OrganizacionMapper {
    private OrganizacionMapper() { }

    public static Departamento toDomain(DepartamentoEntity entity) {
        return new Departamento(entity.getId(), entity.getNombre(), entity.isActivo());
    }

    public static Responsable toDomain(ResponsableEntity entity) {
        return new Responsable(entity.getId(), entity.getDepartamentoId(), entity.getNombre(),
                entity.getCargo(), entity.getContacto(), entity.isActivo());
    }

    public static DepartamentoContacto toDomain(DepartamentoContactoEntity entity) {
        return new DepartamentoContacto(entity.getId(), entity.getDepartamentoId(), entity.getTipo(),
                entity.getValor(), entity.isActivo());
    }
}
