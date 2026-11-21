package cl.casol.backend.identidad.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Bandeja durable exclusiva de identidad. Nunca almacena el código en claro. */
public interface CorreoCodigoStore {
    enum Estado { PENDIENTE, ENVIANDO, ENVIADO, ERROR_TEMPORAL, ERROR_PERMANENTE,
        NO_CONFIGURADO, REAUTORIZAR, INCIERTO, EXPIRADO }
    // Devuelve trabajo.
    record Trabajo(String id, Long codigoId, String cifrado, LocalDateTime expira, int intentos) {
        @Override public String toString() { return "CorreoCodigo[" + id + "]"; }
    }
    // Guarda por id.
    void guardar(String id, Long codigoId, String cifrado, LocalDateTime expira, Estado estado);
    // Devuelve pendientes.
    List<String> pendientes(LocalDateTime ahora);
    // Reserva por id.
    Optional<Trabajo> reservar(String id, LocalDateTime ahora);
    // Termina por id.
    void terminar(String id, Estado estado, String referencia, LocalDateTime proximoIntento);
}
