package cl.casol.backend.whatsapp.infrastructure.http;

import cl.casol.backend.whatsapp.application.service.ProcesarMensajeService;
import cl.casol.backend.whatsapp.application.port.out.AlmacenWhatsAppPort;
import cl.casol.backend.whatsapp.domain.*;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal.*;

/** Traduce la envoltura Meta a mensajes neutrales. Valida todo el lote antes de procesar. */
public final class EntradaWebhookHttpAdapter {
    public record Recepcion(List<RespuestaCanal> respuestas,int estados,int ignorados,int aceptados,int duplicados) {
        public Recepcion(List<RespuestaCanal> respuestas,int estados,int ignorados){this(respuestas,estados,ignorados,0,0);}
        public Recepcion { respuestas=List.copyOf(respuestas); }
        @Override public String toString(){return "Recepcion[contenido omitido]";}
    }
    private final ProcesarMensajeService servicio;
    private final AlmacenWhatsAppPort almacen;
    private final ValidacionWebhookLocal validacion;
    private final JsonMapper json=JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(MAX_BYTES).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public EntradaWebhookHttpAdapter(ProcesarMensajeService servicio,ValidacionWebhookLocal validacion) {
        this.servicio=servicio;this.validacion=validacion;this.almacen=null;
    }
    public EntradaWebhookHttpAdapter(AlmacenWhatsAppPort almacen,ValidacionWebhookLocal validacion) {
        this.servicio=null;this.validacion=validacion;this.almacen=almacen;
    }
    public Recepcion recibir(byte[] cuerpo,String firma) {
        validacion.autenticar(cuerpo,firma); // Antes del parser y de cualquier caso de uso.
        var mensajes=new ArrayList<MensajeCanal>();
        var entradasDurables=new ArrayList<AlmacenWhatsAppPort.Entrada>();
        int estados=0,ignorados=0,eventos=0;
        try {
            JsonNode raiz=json.readTree(cuerpo);
            if (raiz==null || !raiz.isObject() || !"whatsapp_business_account".equals(texto(raiz,"object",40))) invalido();
            var entradas=lista(raiz,"entry",5);
            for(var entry:entradas) {
                String cuenta=identificador(entry,"id");
                for(var cambio:lista(entry,"changes",5)) {
                    String field=texto(cambio,"field",64);
                    JsonNode valor=cambio.path("value");
                    if (!valor.isObject()) invalido();
                    if (!field.equals("messages")) { if(++eventos>10)invalido(); ignorados++;continue; }
                    if(!"whatsapp".equals(texto(valor,"messaging_product",20)))invalido();
                    String canal=identificador(valor.path("metadata"),"phone_number_id");
                    boolean tieneMensajes=valor.has("messages"),tieneEstados=valor.has("statuses");
                    if(!tieneMensajes && !tieneEstados) {
                        if(!valor.path("errors").isArray() || valor.path("errors").isEmpty())invalido();
                        if(++eventos>10)invalido();ignorados++;continue;
                    }
                    if(tieneMensajes) for(var m:lista(valor,"messages",10)) {
                        if(++eventos>10)invalido();
                        String id=identificador(m,"id"),usuario=identificador(m,"from"),tipo=texto(m,"type",30);
                        timestamp(m);
                        var emitido=java.time.Instant.ofEpochSecond(Long.parseLong(m.path("timestamp").asText()));
                        MensajeCanal.Tipo t=switch(tipo) {
                            case "text" -> MensajeCanal.Tipo.TEXTO;
                            case "image" -> MensajeCanal.Tipo.IMAGEN;
                            default -> MensajeCanal.Tipo.NO_COMPATIBLE;
                        };
                        String contenido="";
                        if(t==MensajeCanal.Tipo.TEXTO) {
                            contenido=texto(m.path("text"),"body",450);
                            if(contenido.isBlank() || contenido.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))invalido();
                        }
                        if(t==MensajeCanal.Tipo.IMAGEN) {
                            contenido=identificador(m.path("image"),"id");
                            if(!Set.of("image/png","image/jpeg","image/webp").contains(texto(m.path("image"),"mime_type",30)))invalido();
                        }
                        var mensaje=new MensajeCanal(cuenta,canal,usuario,id,t,contenido);
                        mensajes.add(mensaje);entradasDurables.add(new AlmacenWhatsAppPort.Entrada(mensaje,emitido));
                    }
                    if(tieneEstados) for(var estado:lista(valor,"statuses",10)) {
                        if(++eventos>10)invalido();
                        identificador(estado,"id");identificador(estado,"recipient_id");timestamp(estado);
                        String status=texto(estado,"status",40);
                        if(Set.of("sent","delivered","read","failed","deleted").contains(status))estados++;else ignorados++;
                    }
                }
            }
        } catch(Rechazo e) { throw e; }
          catch(RuntimeException e) { throw new Rechazo(Motivo.EVENTO); }
        var respuestas=new ArrayList<RespuestaCanal>();
        try {
            if(almacen!=null){
                var recibo=almacen.recibir(entradasDurables); // Commit completado antes de devolver HTTP 200.
                return new Recepcion(List.of(),estados,ignorados,recibo.aceptados(),recibo.duplicados());
            }
            for(var m:mensajes)respuestas.add(servicio.procesar(m));
        } catch(IllegalArgumentException|AlmacenWhatsAppPort.EventoConflictivo e) { throw new Rechazo(Motivo.EVENTO); }
          catch(RuntimeException e) { throw new Rechazo(Motivo.INTERNO); }
        return new Recepcion(respuestas,estados,ignorados);
    }
    private JsonNode lista(JsonNode n,String campo,int max) {
        var lista=n.path(campo);
        if(!lista.isArray() || lista.isEmpty() || lista.size()>max)invalido();return lista;
    }
    private String texto(JsonNode n,String campo,int max) {
        var v=n.path(campo);
        if(!v.isString() || v.asText().isEmpty() || v.asText().length()>max)invalido();
        return v.asText();
    }
    private String identificador(JsonNode n,String campo) {
        String v=texto(n,campo,128);
        if(!v.matches("[A-Za-z0-9_.:+=/-]{1,128}"))invalido();return v;
    }
    private void timestamp(JsonNode n) { if(!texto(n,"timestamp",12).matches("[0-9]{1,12}"))invalido(); }
    private void invalido(){throw new Rechazo(Motivo.EVENTO);}
}
