package cl.casol.backend.procedimiento.infrastructure.persistence.mapper;

import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.UsuarioMapper;
import cl.casol.backend.procedimiento.domain.Procedimiento;
import cl.casol.backend.procedimiento.infrastructure.persistence.entity.ProcedimientoEntity;

/**
 * Convierte los datos de procedimiento entre la base de datos o la API y el dominio.
 */
public final class ProcedimientoMapper {
    private ProcedimientoMapper() { }
    // Convierte la entidad al modelo del dominio.
    public static Procedimiento toDomain(ProcedimientoEntity e) {
        return new Procedimiento(e.getId(), e.getNombre(), e.getDescripcion(), e.getEstado(),
                UsuarioMapper.toDomain(e.getCreadoPor()), e.getFechaCreacion(),
                UsuarioMapper.toDomain(e.getModificadoPor()), e.getFechaModificacion(),e.getDatosEditoriales()==null?null:tools.jackson.databind.json.JsonMapper.builder().build().readValue(e.getDatosEditoriales(),cl.casol.backend.procedimiento.domain.DatosEditorialesProcedimiento.class),e.getHardwareId());
    }
    // Convierte el modelo del dominio a la entidad de base de datos.
    public static ProcedimientoEntity toEntity(Procedimiento p, UsuarioEntity creador, UsuarioEntity modificador) {
        var entity=new ProcedimientoEntity(p.id(), p.nombre(), p.descripcion(), p.estado(), creador,
                p.fechaCreacion(), modificador, p.fechaModificacion());
        entity.setHardwareId(p.hardwareId());
        entity.setDatosEditoriales(ConfiguracionPasoJson.escribir(p.datos()));return entity;
    }
}
