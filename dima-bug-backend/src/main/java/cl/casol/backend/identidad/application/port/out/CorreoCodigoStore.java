package cl.casol.backend.identidad.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Bandeja durable exclusiva de identidad. Nunca almacena el código en claro. */
public interface CorreoCodigoStore {
    enum Estado { PENDIENTE, ENVIANDO, ENVIADO, ERROR_TEMPORAL, ERROR_PERMANENTE,
        NO_CONFIGURADO, REAUTORIZAR, INCIERTO, EXPIRADO }
    record Trabajo(String id, Long codigoId, String cifrado, LocalDateTime expira, int intentos) {
        @Override public String toString() { return "CorreoCodigo[" + id + "]"; }
    }
    void guardar(String id, Long codigoId, String cifrado, LocalDateTime expira, Estado estado);
    List<String> pendientes(LocalDateTime ahora);
    Optional<Trabajo> reservar(String id, LocalDateTime ahora);
    void terminar(String id, Estado estado, String referencia, LocalDateTime proximoIntento);
}
