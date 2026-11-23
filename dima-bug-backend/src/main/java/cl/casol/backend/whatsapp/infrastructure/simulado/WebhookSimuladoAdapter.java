package cl.casol.backend.whatsapp.infrastructure.simulado;

import cl.casol.backend.whatsapp.application.service.ProcesarMensajeService;
import cl.casol.backend.whatsapp.domain.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** Entrada programática QA: no es controlador y no publica rutas HTTP. */
public final class WebhookSimuladoAdapter {
    private final ProcesarMensajeService servicio;
    private final JsonMapper json=JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder()
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public WebhookSimuladoAdapter(ProcesarMensajeService servicio) { this.servicio=servicio; }
    public List<RespuestaCanal> recibir(byte[] bytes) {
        if (bytes==null || bytes.length==0 || bytes.length>16384) return invalido();
        try {
            JsonNode raiz=json.readTree(bytes);
            if (!raiz.path("object").asText().equals("whatsapp_business_account")) return invalido();
            var mensajes=new ArrayList<MensajeCanal>();
            JsonNode entradas=raiz.path("entry");
            if (!entradas.isArray() || entradas.isEmpty() || entradas.size()>5) return invalido();
            for (JsonNode entry:entradas) {
                String cuenta=id(entry,"id","qa-cuenta-");
                JsonNode cambios=entry.path("changes");
                if (!cambios.isArray() || cambios.isEmpty() || cambios.size()>5) return invalido();
                for (JsonNode cambio:cambios) {
                    if (!cambio.path("field").asText().equals("messages")) return invalido();
                    JsonNode valor=cambio.path("value");
                    if (!valor.path("messaging_product").asText().equals("whatsapp")) return invalido();
                    String canal=id(valor.path("metadata"),"phone_number_id","qa-canal-");
                    JsonNode lista=valor.path("messages");
                    if (!lista.isArray() || lista.isEmpty() || lista.size()>10) return invalido();
                    for (JsonNode m:lista) {
                        String usuario=id(m,"from","qa-usuario-"), mid=id(m,"id","wamid.qa-");
                        if (!m.path("timestamp").isString() || !m.path("timestamp").asText().matches("[0-9]{1,12}")) return invalido();
                        String tipo=m.path("type").asText();
                        MensajeCanal.Tipo t=switch(tipo) { case "text" -> MensajeCanal.Tipo.TEXTO; case "image" -> MensajeCanal.Tipo.IMAGEN; default -> MensajeCanal.Tipo.NO_COMPATIBLE; };
                        if (tipo.isBlank() || tipo.length()>30) return invalido();
                        String contenido="";
                        if (t==MensajeCanal.Tipo.TEXTO) {
                            if (!m.path("text").path("body").isString()) return invalido();
                            contenido=m.path("text").path("body").asText();
                            if (contenido.length()>450) return invalido();
                        }
                        if (t==MensajeCanal.Tipo.IMAGEN) {
                            contenido=id(m.path("image"),"id","qa-media-");
                            if (!m.path("image").path("mime_type").asText().equals("image/png")) return invalido();
                        }
                        mensajes.add(new MensajeCanal(cuenta,canal,usuario,mid,t,contenido));
                        if (mensajes.size()>10) return invalido();
                    }
                }
            }
            // Validar el lote completo antes de producir efectos.
            return mensajes.stream().map(servicio::procesar).toList();
        } catch (RuntimeException e) { return invalido(); }
    }
    private String id(JsonNode n,String campo,String prefijo) {
        JsonNode v=n.path(campo);
        if (!v.isString() || !v.asText().matches(java.util.regex.Pattern.quote(prefijo)+"[a-zA-Z0-9_-]{1,48}"))
            throw new IllegalArgumentException("Identificador QA no válido");
        return v.asText();
    }
    private List<RespuestaCanal> invalido() { return List.of(RespuestaCanal.simple(RespuestaCanal.Estado.INVALIDO,"Evento de simulación no válido.")); }
}
