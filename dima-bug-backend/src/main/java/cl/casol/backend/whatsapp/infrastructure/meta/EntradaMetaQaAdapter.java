package cl.casol.backend.whatsapp.infrastructure.meta;

import cl.casol.backend.whatsapp.application.port.out.AlmacenWhatsAppPort;
import cl.casol.backend.whatsapp.domain.*;
import cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal.*;
import static cl.casol.backend.whatsapp.infrastructure.meta.ObservabilidadMetaQa.Codigo.*;

/** Normalizador del perfil Meta QA, sin HTTP saliente ni interpretación de soluciones. */
public final class EntradaMetaQaAdapter {
    public static final int MAX_BYTES=3*1024*1024,MAX_EVENTOS=1000;
    public record Acuse(int aceptados,int duplicados,int estados,int ignorados) { }
    public static final class Pausado extends RuntimeException { public Pausado(){super("Recepción QA pausada");} }
    private final AlmacenWhatsAppPort almacen;
    private final ValidacionWebhookLocal firma;
    private final PoliticaMetaQa politica;
    private final ControlesMetaQa controles;
    private final ObservabilidadMetaQa observabilidad;
    private final JsonMapper json=JsonMapper.builder(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(MAX_BYTES).build())
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public EntradaMetaQaAdapter(AlmacenWhatsAppPort almacen,ValidacionWebhookLocal f,PoliticaMetaQa p,ControlesMetaQa c,ObservabilidadMetaQa o){
        this.almacen=Objects.requireNonNull(almacen,"Persistencia obligatoria");firma=f;politica=p;controles=c;observabilidad=o;
    }
    public Acuse recibir(byte[] cuerpo,String sign){
        long inicio=System.nanoTime();
        try{
            firma.autenticar(cuerpo,sign);
            if(!controles.recepcion())throw new Pausado();
            var mensajes=new ArrayList<AlmacenWhatsAppPort.Entrada>();var estados=new ArrayList<EstadoEntregaCanal>();
            int ignorados;
            try{ignorados=normalizar(json.readTree(cuerpo),mensajes,estados);}
            catch(PoliticaMetaQa.NoAutorizado|Rechazo e){throw e;}
            catch(RuntimeException e){throw new Rechazo(Motivo.EVENTO);}
            if(!controles.recepcion())throw new Pausado(); // Revisión final antes del compromiso durable.
            AlmacenWhatsAppPort.Recibo recibo;
            try{recibo=almacen.recibirLote(mensajes,estados);}
            catch(AlmacenWhatsAppPort.EventoConflictivo e){throw new Rechazo(Motivo.EVENTO);}
            catch(RuntimeException e){observabilidad.registrar(ALMACEN);throw new Rechazo(Motivo.INTERNO);}
            observabilidad.sumar(ACEPTADOS,recibo.aceptados());observabilidad.sumar(DUPLICADOS,recibo.duplicados());
            observabilidad.sumar(ESTADOS,estados.size());observabilidad.sumar(IGNORADOS,ignorados);
            return new Acuse(recibo.aceptados(),recibo.duplicados(),estados.size(),ignorados);
        }catch(Rechazo e){if(e.motivo()!=Motivo.INTERNO)observabilidad.registrar(switch(e.motivo()){
            case FIRMA,PARAMETROS->FIRMA;case TAMANO->TAMANO;default->EVENTO;});throw e;}
        catch(PoliticaMetaQa.NoAutorizado e){observabilidad.registrar(NO_AUTORIZADO);throw e;}
        catch(Pausado e){observabilidad.registrar(PAUSA);throw e;}
        finally{observabilidad.terminar(inicio);}
    }
    private int normalizar(JsonNode raiz,List<AlmacenWhatsAppPort.Entrada> mensajes,List<EstadoEntregaCanal> estados){
        if(raiz==null||!raiz.isObject()||!"whatsapp_business_account".equals(texto(raiz,"object",40)))invalido();
        int ignorados=0,total=0;
        for(var entrada:lista(raiz,"entry")){
            String cuenta=id(entrada,"id");politica.cuenta(cuenta);
            for(var cambio:lista(entrada,"changes")){
                var valor=cambio.path("value");if(!valor.isObject())invalido();
                if(!"messages".equals(texto(cambio,"field",64))){if(++total>MAX_EVENTOS)invalido();ignorados++;continue;}
                if(!"whatsapp".equals(texto(valor,"messaging_product",20)))invalido();
                String canal=id(valor.path("metadata"),"phone_number_id");politica.numero(canal);
                if(!valor.has("messages")&&!valor.has("statuses")){
                    if(!valor.path("errors").isArray()||valor.path("errors").isEmpty())invalido();
                    if(++total>MAX_EVENTOS)invalido();ignorados++;continue;
                }
                if(valor.has("messages"))for(var m:lista(valor,"messages")){
                    if(++total>MAX_EVENTOS)invalido();
                    String mensaje=id(m,"id"),participante=politica.participante(opcional(m,"from"),opcional(m,"from_user_id"));
                    Instant fecha=fecha(m);String tipo=texto(m,"type",40),contenido="";
                    MensajeCanal.Tipo t=MensajeCanal.Tipo.NO_COMPATIBLE;
                    if(tipo.equals("text")){
                        t=MensajeCanal.Tipo.TEXTO;contenido=texto(m.path("text"),"body",4096);
                        if(contenido.isBlank()||contenido.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))invalido();
                    }else if(tipo.equals("image")){
                        t=MensajeCanal.Tipo.IMAGEN;contenido=id(m.path("image"),"id");
                        if(!Set.of("image/png","image/jpeg","image/webp").contains(texto(m.path("image"),"mime_type",30)))invalido();
                    }
                    mensajes.add(new AlmacenWhatsAppPort.Entrada(new MensajeCanal(cuenta,canal,participante,mensaje,t,contenido),fecha));
                }
                if(valor.has("statuses"))for(var s:lista(valor,"statuses")){
                    if(++total>MAX_EVENTOS)invalido();
                    String participante;
                    try{participante=politica.participante(opcional(s,"recipient_id"),opcional(s,"recipient_user_id"));}
                    catch(PoliticaMetaQa.NoAutorizado e){throw e.enStatuses();}
                    String estado=texto(s,"status",40);if(!estado.matches("[a-z_]{1,40}"))invalido();
                    estados.add(new EstadoEntregaCanal(cuenta,canal,id(s,"id"),participante,estado,fecha(s)));
                }
            }
        }
        return ignorados;
    }
    private JsonNode lista(JsonNode n,String campo){var a=n.path(campo);if(!a.isArray()||a.isEmpty()||a.size()>MAX_EVENTOS)invalido();return a;}
    private String texto(JsonNode n,String campo,int max){var v=n.path(campo);if(!v.isString()||v.asText().isEmpty()||v.asText().length()>max)invalido();return v.asText();}
    private String id(JsonNode n,String campo){String v=texto(n,campo,512);if(!v.matches("[A-Za-z0-9_.:+=/-]{1,512}"))invalido();return v;}
    private String opcional(JsonNode n,String campo){return n.has(campo)?id(n,campo):null;}
    private Instant fecha(JsonNode n){
        var v=n.path("timestamp");String t=v.asText();
        if((!v.isString()&&!v.isNumber())||!t.matches("[0-9]{1,12}"))invalido();
        Instant fecha=Instant.ofEpochSecond(Long.parseLong(t));if(fecha.isAfter(Instant.now().plusSeconds(300)))invalido();return fecha;
    }
    private void invalido(){throw new Rechazo(Motivo.EVENTO);}
}
