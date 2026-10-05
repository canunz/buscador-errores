package cl.casol.backend.identidad.infrastructure.security;

import cl.casol.backend.identidad.application.port.out.PasswordEncoderPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

//CASO DE USO: C) Almacenar y validar contraseñas de forma segura.

@Component
public class BCryptPasswordEncoderAdapter implements PasswordEncoderPort {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordEncoderAdapter(
            PasswordEncoder passwordEncoder
    ) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean coincide(
            String passwordPlano,
            String passwordHash
    ) {
        if (passwordPlano == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        String plano = passwordPlano.trim();
        String hash = passwordHash.trim();
        if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
            try {
                return passwordEncoder.matches(plano, hash);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        return hash.equals(plano);
    }

    @Override
    public String codificar(String passwordPlano) {
        return passwordEncoder.encode(passwordPlano);
    }
}
