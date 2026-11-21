package cl.casol.backend.ejecucion.application.port.out;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.procedimiento.domain.Paso;
import java.util.*;
/**
 * Puerto de salida de automatización ejecución.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface AutomatizacionEjecucionPort {
    // Valida inicio.
    void validarInicio(Integer sucursalId);
    // Busca inicio.
    Optional<Integer> buscarInicio(Integer actorId,String clave,Integer procedimientoId,Integer sucursalId);
    // Busca inicio.
    default Optional<Integer> buscarInicio(Integer actor,String clave,Integer procedimiento,Integer sucursal,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> adicionales){return buscarInicio(actor,clave,procedimiento,sucursal);}
    // Inicia la ejecución.
    void iniciar(Ejecucion ejecucion,List<EjecucionPaso> pasos,List<Paso> definiciones,Integer sucursalId,String clave);
    // Inicia con los datos recibidos.
    default void iniciar(Ejecucion e,List<EjecucionPaso> pasos,List<Paso> definiciones,Integer sucursal,String clave,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> adicionales){iniciar(e,pasos,definiciones,sucursal,clave);}
    // Registra manual.
    default void registrarManual(Ejecucion e,EjecucionPaso paso,Integer actorId){}
    // Avanza la ejecución.
    void avanzar(Ejecucion ejecucion);
    // Cancela la ejecución.
    void cancelar(Ejecucion ejecucion,Integer actorId);
}
