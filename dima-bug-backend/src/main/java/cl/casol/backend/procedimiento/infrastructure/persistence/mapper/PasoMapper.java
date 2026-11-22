package cl.casol.backend.procedimiento.infrastructure.persistence.mapper;

import cl.casol.backend.procedimiento.domain.Paso;
import cl.casol.backend.procedimiento.infrastructure.persistence.entity.PasoEntity;

/**
 * Convierte los datos de paso entre la base de datos o la API y el dominio.
 */
public final class PasoMapper {
    private PasoMapper() { }
    // Convierte la entidad al modelo del dominio.
    public static Paso toDomain(PasoEntity e) {
        return new Paso(e.getId(), e.getProcedimientoId(), e.getOrden(), e.getInstruccion(), e.isEsCritico(),e.getModoEjecucion(),e.getMensajeEvidencia(),e.getConfiguracion());
    }
    // Convierte el modelo del dominio a la entidad de base de datos.
    public static PasoEntity toEntity(Paso p) {
        var e=new PasoEntity(p.id(), p.procedimientoId(), p.orden(), p.instruccion(), p.esCritico(),p.modoEjecucion(),p.mensajeEvidencia());e.configurar(p.configuracion());return e;
    }
}
