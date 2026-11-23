package cl.casol.backend.whatsapp.infrastructure.persistence;

import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.application.service.ProcesarReservaWhatsAppService;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import cl.casol.backend.whatsapp.infrastructure.meta.*;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;

/** Dos consultas, un OCR y una preparación; salida simulada o, si está autorizado, envío real a Meta. */
@Component
@Profile(PerfilesWhatsApp.DURABLE)
@Conditional(TrabajadoresWhatsApp.Habilitados.class)
public class TrabajadoresWhatsApp {
    public static final class Habilitados implements Condition {
        public boolean matches(ConditionContext c,AnnotatedTypeMetadata m){
            return c.getEnvironment().acceptsProfiles(org.springframework.core.env.Profiles.of("qa-whatsapp-meta"))||
                c.getEnvironment().getProperty("dimabug.whatsapp.durable.trabajadores",Boolean.class,true);
        }
    }
    private final AlmacenWhatsAppPort almacen;
    private final ProcesarReservaWhatsAppService procesador;
    private final PrepararRespuestaPublicaPort preparador;
    private final ControlProcesamientoWhatsAppPort controles;
    private final ObservabilidadMetaQa observabilidad;
    private final EnviarMensajeCanalPort envio;
    private final ThreadPoolExecutor textos=pool(2,"wa-texto"),imagenes=pool(1,"wa-ocr"),salidas=pool(1,"wa-salida");
    private record Activa(long inicio,AlmacenWhatsAppPort.Reserva reserva,Thread hilo) { }
    private record ClaveReserva(long evento,String token,long cercado) { }
    private final ConcurrentMap<ClaveReserva,Activa> activas=new ConcurrentHashMap<>();
    public TrabajadoresWhatsApp(AlmacenWhatsAppPort a,ProcesarReservaWhatsAppService p,PrepararRespuestaPublicaPort s){this(a,p,s,(ControlProcesamientoWhatsAppPort)null,null);}
    @Autowired public TrabajadoresWhatsApp(AlmacenWhatsAppPort a,ProcesarReservaWhatsAppService p,PrepararRespuestaPublicaPort s,
            ObjectProvider<ControlesMetaQa> c,ObjectProvider<ObservabilidadMetaQa> o,ObjectProvider<EnviarMensajeCanalPort> e){
        this(a,p,s,c.getIfAvailable(),o.getIfAvailable(),e.getIfAvailable());}
    public TrabajadoresWhatsApp(AlmacenWhatsAppPort a,ProcesarReservaWhatsAppService p,PrepararRespuestaPublicaPort s,
            ControlProcesamientoWhatsAppPort c,ObservabilidadMetaQa o){this(a,p,s,c,o,null);}
    /** envio==null: salida SIMULADA (comportamiento QA original). */
    public TrabajadoresWhatsApp(AlmacenWhatsAppPort a,ProcesarReservaWhatsAppService p,PrepararRespuestaPublicaPort s,
            ControlProcesamientoWhatsAppPort c,ObservabilidadMetaQa o,EnviarMensajeCanalPort e){
        almacen=a;procesador=p;preparador=s;controles=c;observabilidad=o;envio=e;}
    private static ThreadPoolExecutor pool(int n,String nombre){
        return new ThreadPoolExecutor(n,n,0,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{var t=new Thread(r,nombre);t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    @Scheduled(fixedDelayString="${dimabug.whatsapp.durable.sondeo-ms:1000}",scheduler="whatsAppScheduler")
    public void sondear(){
        if(controles==null||controles.procesamiento()){
            for(int i=0;i<2;i++)lanzar(textos,()->procesar(false));
            if(controles==null||controles.medios())lanzar(imagenes,()->procesar(true));
        }
        if(controles==null||controles.envio())lanzar(salidas,this::salida);
    }
    private void lanzar(ThreadPoolExecutor pool,Runnable tarea){
        try{pool.execute(()->{try{tarea.run();}catch(RuntimeException e){registrar(ObservabilidadMetaQa.Codigo.PROCESAMIENTO);}});}
        catch(RejectedExecutionException e){sumar(ObservabilidadMetaQa.Codigo.SATURACION);}
    }
    private void procesar(boolean imagen){
        if(controles!=null&&(!controles.procesamiento()||imagen&&!controles.medios()))return;
        var r=almacen.reservar(imagen);if(r==null)return;
        var clave=new ClaveReserva(r.evento(),r.token(),r.cercado());
        activas.put(clave,new Activa(System.nanoTime(),r,Thread.currentThread()));
        try{procesador.ejecutar(r);sumar(ObservabilidadMetaQa.Codigo.EJECUCIONES);}finally{activas.remove(clave);Thread.interrupted();}
    }
    private void salida(){
        if(controles!=null&&!controles.envio())return;
        var s=almacen.reservarSalida();if(s==null)return;
        RespuestaCanal publica;
        try{
            publica=preparador.preparar(s.respuesta());
            if(controles!=null&&!controles.envio())return; // Se recuperará la reserva sin despacharla.
            if(envio==null){if(almacen.simular(s,publica))sumar(ObservabilidadMetaQa.Codigo.SIMULADAS);return;}
        }catch(FalloCatalogoPublico e){registrar(ObservabilidadMetaQa.Codigo.SALIDA);almacen.reintentarSalida(s,e.reintentable()?5:1,e.esperaMinima());return;}
         catch(RuntimeException e){registrar(ObservabilidadMetaQa.Codigo.SALIDA);almacen.reintentarSalida(s);return;}
        String idProveedor;
        try{idProveedor=envio.enviar(s.respuesta().destinatario(),FormatoWhatsApp.texto(publica));}
        catch(EnviarMensajeCanalPort.FalloEnvio e){registrar(ObservabilidadMetaQa.Codigo.SALIDA);almacen.reintentarEnvio(s,e.reintentable());return;}
        catch(RuntimeException e){registrar(ObservabilidadMetaQa.Codigo.SALIDA);almacen.reintentarEnvio(s,true);return;}
        try{if(almacen.aceptar(s,publica,idProveedor))sumar(ObservabilidadMetaQa.Codigo.ENVIADAS);}
        catch(RuntimeException e){registrar(ObservabilidadMetaQa.Codigo.SALIDA);} // Ya enviado: no se reintenta.
    }
    @Scheduled(fixedDelay=10000,scheduler="whatsAppScheduler")
    public void renovar(){
        for(var a:activas.values())try{
            if(System.nanoTime()-a.inicio()>TimeUnit.SECONDS.toNanos(45)){
                almacen.reintentar(a.reserva(),"TIEMPO_AGOTADO",2);a.hilo().interrupt();
            }else almacen.renovar(a.reserva());
        }catch(RuntimeException e){registrar(ObservabilidadMetaQa.Codigo.RECUPERACION);}
    }
    @Scheduled(fixedDelay=15000,scheduler="whatsAppScheduler")
    public void recuperar(){try{almacen.recuperar();}catch(RuntimeException e){registrar(ObservabilidadMetaQa.Codigo.RECUPERACION);}}
    private void registrar(ObservabilidadMetaQa.Codigo c){if(observabilidad!=null)observabilidad.registrar(c);}
    private void sumar(ObservabilidadMetaQa.Codigo c){if(observabilidad!=null)observabilidad.sumar(c,1);}
    @PreDestroy public void cerrar(){textos.shutdownNow();imagenes.shutdownNow();salidas.shutdownNow();}
}
