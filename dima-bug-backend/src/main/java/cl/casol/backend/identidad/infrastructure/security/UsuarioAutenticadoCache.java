package cl.casol.backend.identidad.infrastructure.security;

import cl.casol.backend.identidad.domain.Usuario;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UsuarioAutenticadoCache {

    private static final long VIGENCIA_MS = 45_000;

    private final ConcurrentHashMap<String, Entrada> entradas = new ConcurrentHashMap<>();

    public Optional<Usuario> obtener(String email) {
        String clave = clave(email);
        if (clave.isEmpty()) {
            return Optional.empty();
        }
        Entrada entrada = entradas.get(clave);
        if (entrada == null) {
            return Optional.empty();
        }
        if (entrada.expiraEn < System.currentTimeMillis()) {
            entradas.remove(clave, entrada);
            return Optional.empty();
        }
        return Optional.of(entrada.usuario);
    }

    public void guardar(String email, Usuario usuario) {
        String clave = clave(email);
        if (clave.isEmpty() || usuario == null) {
            return;
        }
        entradas.put(clave, new Entrada(usuario, System.currentTimeMillis() + VIGENCIA_MS));
    }

    public void invalidar(String email) {
        String clave = clave(email);
        if (!clave.isEmpty()) {
            entradas.remove(clave);
        }
    }

    private static String clave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Entrada(Usuario usuario, long expiraEn) {
    }
}
