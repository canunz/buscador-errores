package cl.casol.backend.procedimiento.domain;

import cl.casol.backend.identidad.domain.Usuario;
import java.time.LocalDateTime;

public record Procedimiento(Integer id, String nombre, String descripcion, EstadoProcedimiento estado,
        Usuario creadoPor, LocalDateTime fechaCreacion, Usuario modificadoPor,
        LocalDateTime fechaModificacion) { }
