package cl.casol.backend.whatsapp.application.port.out;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
public interface PrepararRespuestaPublicaPort {
    RespuestaCanal preparar(RespuestaCanal respuesta);
}
