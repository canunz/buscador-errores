package cl.casol.backend.correo.application.port.out;
import java.time.Instant;
import java.util.Optional;
/**
 * Contrato de prueba gmail store (módulo de correo).
 */
public interface PruebaGmailStore {
    enum Estado { PENDIENTE,ENVIANDO,ENVIADO,FALLIDO,INCIERTO }
    // Devuelve prueba.
    record Prueba(String id,Integer actor,String remitente,String destinatario,Estado estado,Instant creada,Instant intento,Instant finalizada,String mensajeId,String resultado){}
    // Devuelve última.
    Optional<Prueba> ultima(String destinatario);
    // Busca por id.
    Optional<Prueba> buscar(String id);
    // Prepara el clave.
    Prueba preparar(String clave,String anterior,Integer actor,String remitente,String destinatario);
    // Reserva por id.
    boolean reservar(String id);
    // Finaliza por id.
    Prueba finalizar(String id,Estado estado,String mensajeId,String resultado);
}
