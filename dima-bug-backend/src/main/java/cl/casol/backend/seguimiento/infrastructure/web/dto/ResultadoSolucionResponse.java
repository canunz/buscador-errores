package cl.casol.backend.seguimiento.infrastructure.web.dto;

import cl.casol.backend.seguimiento.domain.*;
import java.time.LocalDateTime;

/**
 * Datos que se devuelven al frontend con resultado solución.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ResultadoSolucionResponse(Integer id,boolean funciono,String comentario,
        LocalDateTime fecha,ResultadoUsuarioResponse usuario) {
    // Crea el objeto a partir de los datos recibidos.
    public static ResultadoSolucionResponse from(DetalleResultadoSolucion d){ResultadoSolucion r=d.resultado();
        return new ResultadoSolucionResponse(r.id(),r.funciono(),r.comentario(),r.fecha(),
                ResultadoUsuarioResponse.from(d.usuario()));}
}
