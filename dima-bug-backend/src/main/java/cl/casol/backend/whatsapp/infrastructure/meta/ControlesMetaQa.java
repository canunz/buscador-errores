package cl.casol.backend.whatsapp.infrastructure.meta;

import cl.casol.backend.whatsapp.application.port.out.ControlProcesamientoWhatsAppPort;
import java.util.concurrent.atomic.AtomicReference;

/** Administración local Java, sin endpoint público. La única salida sigue siendo simulada. */
public final class ControlesMetaQa implements ControlProcesamientoWhatsAppPort {
    public record Estado(boolean recepcion,boolean procesamiento,boolean medios,boolean envio) { }
    private final AtomicReference<Estado> estado;
    private final ObservabilidadMetaQa observabilidad;
    public ControlesMetaQa(Estado inicial,ObservabilidadMetaQa o){estado=new AtomicReference<>(inicial);observabilidad=o;}
    public boolean recepcion(){return estado.get().recepcion();}
    public boolean procesamiento(){return estado.get().procesamiento();}
    public boolean medios(){return estado.get().medios();}
    public boolean envio(){return estado.get().envio();}
    public Estado instantanea(){return estado.get();}
    public void recepcion(boolean v){cambiar(s->new Estado(v,s.procesamiento(),s.medios(),s.envio()));}
    public void procesamiento(boolean v){cambiar(s->new Estado(s.recepcion(),v,s.medios(),s.envio()));}
    public void medios(boolean v){cambiar(s->new Estado(s.recepcion(),s.procesamiento(),v,s.envio()));}
    public void envio(boolean v){cambiar(s->new Estado(s.recepcion(),s.procesamiento(),s.medios(),v));}
    private void cambiar(java.util.function.UnaryOperator<Estado> cambio){estado.updateAndGet(cambio);observabilidad.registrar(ObservabilidadMetaQa.Codigo.CONTROL);}
}
