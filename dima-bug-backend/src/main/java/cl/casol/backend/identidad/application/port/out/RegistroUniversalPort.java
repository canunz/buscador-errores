package cl.casol.backend.identidad.application.port.out;
import cl.casol.backend.identidad.domain.Usuario;
import java.util.Optional;
/** Runs in the caller transaction; locks precede eligibility/rate-limit checks. */
public interface RegistroUniversalPort {
    Optional<Usuario> resolver(String email, boolean crear, String hashAleatorio);
}
