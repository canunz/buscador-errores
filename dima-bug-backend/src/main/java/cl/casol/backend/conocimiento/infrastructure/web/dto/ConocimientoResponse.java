package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.Conocimiento;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;

import java.time.LocalDateTime;

public record ConocimientoResponse(Integer id, String titulo, String descripcion, EstadoConocimiento estado,
        ResumenResponse hardware, ResumenResponse sistema, ResumenResponse modulo,
        ResumenResponse frecuencia, String comentario, ResumenResponse creadoPor,
        LocalDateTime fechaCreacion, ResumenResponse modificadoPor, LocalDateTime fechaModificacion) {

    public static ConocimientoResponse from(Conocimiento c) {
        return new ConocimientoResponse(c.getId(), c.getTitulo(), c.getDescripcion(), c.getEstado(),
                c.getHardware() == null ? null : new ResumenResponse(c.getHardware().id(), c.getHardware().nombre()),
                c.getSistema() == null ? null : new ResumenResponse(c.getSistema().id(), c.getSistema().nombre()),
                c.getModulo() == null ? null : new ResumenResponse(c.getModulo().id(), c.getModulo().nombre()),
                c.getFrecuencia() == null ? null : new ResumenResponse(c.getFrecuencia().id(), c.getFrecuencia().nombre()),
                c.getComentario(), new ResumenResponse(c.getCreadoPor().getId(), c.getCreadoPor().getNombre()),
                c.getFechaCreacion(), c.getModificadoPor() == null ? null
                : new ResumenResponse(c.getModificadoPor().getId(), c.getModificadoPor().getNombre()),
                c.getFechaModificacion());
    }

    public record ResumenResponse(Integer id, String nombre) { }
}
