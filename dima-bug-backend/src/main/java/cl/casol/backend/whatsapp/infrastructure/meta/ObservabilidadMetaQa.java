package cl.casol.backend.whatsapp.infrastructure.meta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

/** Etiquetas cerradas y agregadas: nunca IDs, excepciones, cuerpos o secretos. */
public final class ObservabilidadMetaQa {
    public enum Codigo { ACEPTADOS,DUPLICADOS,ESTADOS,IGNORADOS,FIRMA,TAMANO,EVENTO,
        NO_AUTORIZADO,PAUSA,ALMACEN,PROCESAMIENTO,RECUPERACION,SALIDA,SATURACION,CONTROL,EJECUCIONES,SIMULADAS,ENVIADAS }
    private static final Logger LOG=LoggerFactory.getLogger(ObservabilidadMetaQa.class);
    private final EnumMap<Codigo,LongAdder> contadores=new EnumMap<>(Codigo.class);
    private final LongAdder solicitudes=new LongAdder(),duracionNanos=new LongAdder();
    public ObservabilidadMetaQa(){for(var c:Codigo.values())contadores.put(c,new LongAdder());}
    public void sumar(Codigo c,long cantidad){contadores.get(c).add(cantidad);}
    public void registrar(Codigo c){
        sumar(c,1);long n=contadores.get(c).sum();
        if(c==Codigo.CONTROL||(n&(n-1))==0)LOG.info("wa_meta_qa codigo={}",c.name());
    }
    public void terminar(long inicio){solicitudes.increment();duracionNanos.add(Math.max(0,System.nanoTime()-inicio));}
    public Map<String,Long> instantanea(){
        var resultado=new LinkedHashMap<String,Long>();contadores.forEach((c,v)->resultado.put(c.name(),v.sum()));
        resultado.put("SOLICITUDES",solicitudes.sum());resultado.put("DURACION_NANOS",duracionNanos.sum());
        return Collections.unmodifiableMap(resultado);
    }
}
