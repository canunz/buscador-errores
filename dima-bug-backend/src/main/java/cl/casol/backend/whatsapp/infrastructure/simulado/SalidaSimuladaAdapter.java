package cl.casol.backend.whatsapp.infrastructure.simulado;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import tools.jackson.databind.json.JsonMapper;
/** Serialización para inspección QA en memoria: no envía ni registra contenido. */
public final class SalidaSimuladaAdapter {
    public byte[] inspeccionar(RespuestaCanal respuesta) {
        return JsonMapper.builder().build().writeValueAsBytes(respuesta);
    }
}
