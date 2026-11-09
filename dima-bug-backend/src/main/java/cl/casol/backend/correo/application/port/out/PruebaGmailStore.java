package cl.casol.backend.correo.application.port.out;
import java.time.Instant;
import java.util.Optional;
public interface PruebaGmailStore {
    enum Estado { PENDIENTE,ENVIANDO,ENVIADO,FALLIDO,INCIERTO }
    record Prueba(String id,Integer actor,String remitente,String destinatario,Estado estado,Instant creada,Instant intento,Instant finalizada,String mensajeId,String resultado){}
    Optional<Prueba> ultima(String destinatario);
    Optional<Prueba> buscar(String id);
    Prueba preparar(String clave,String anterior,Integer actor,String remitente,String destinatario);
    boolean reservar(String id);
    Prueba finalizar(String id,Estado estado,String mensajeId,String resultado);
}
