package cl.casol.backend.ejecucion.application.port.out;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.procedimiento.domain.Paso;
import java.util.*;
public interface AutomatizacionEjecucionPort {
    void validarInicio(Integer sucursalId);
    Optional<Integer> buscarInicio(Integer actorId,String clave,Integer procedimientoId,Integer sucursalId);
    default Optional<Integer> buscarInicio(Integer actor,String clave,Integer procedimiento,Integer sucursal,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> adicionales){return buscarInicio(actor,clave,procedimiento,sucursal);}
    void iniciar(Ejecucion ejecucion,List<EjecucionPaso> pasos,List<Paso> definiciones,Integer sucursalId,String clave);
    default void iniciar(Ejecucion e,List<EjecucionPaso> pasos,List<Paso> definiciones,Integer sucursal,String clave,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> adicionales){iniciar(e,pasos,definiciones,sucursal,clave);}
    default void registrarManual(Ejecucion e,EjecucionPaso paso,Integer actorId){}
    void avanzar(Ejecucion ejecucion);
    void cancelar(Ejecucion ejecucion,Integer actorId);
}
