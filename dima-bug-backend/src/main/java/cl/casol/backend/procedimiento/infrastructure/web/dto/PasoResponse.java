package cl.casol.backend.procedimiento.infrastructure.web.dto;

import cl.casol.backend.procedimiento.domain.Paso;

/**
 * Datos que se devuelven al frontend con paso.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record PasoResponse(Integer id,Integer procedimientoId,Integer orden,String instruccion,boolean esCritico,
        cl.casol.backend.procedimiento.domain.ModoEjecucionPaso modoEjecucion,String mensajeEvidencia,cl.casol.backend.procedimiento.domain.ConfiguracionPaso configuracion,String revisionConfiguracion){
    // Crea el objeto a partir de los datos recibidos.
    public static PasoResponse from(Paso p){return new PasoResponse(p.id(),p.procedimientoId(),p.orden(),p.instruccion(),p.esCritico(),p.modoEjecucion(),p.mensajeEvidencia(),p.configuracion(),p.revisionConfiguracion());}
}
