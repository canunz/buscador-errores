package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.Solucion;
import cl.casol.backend.conocimiento.domain.TipoSolucion;

/**
 * Datos que se devuelven al frontend con solución.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record SolucionResponse(Integer id, String descripcion, TipoSolucion tipo, Integer orden, String titulo, boolean activo, boolean autoservicio) {
    // Crea el objeto a partir de los datos recibidos.
    public static SolucionResponse from(Solucion s) {
        return new SolucionResponse(s.id(), s.descripcion(), s.tipo(), s.orden(), s.titulo(), s.activo(), s.autoservicio());
    }
}
