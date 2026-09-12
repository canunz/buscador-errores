package cl.casol.backend.identidad.infrastructure.security;

import cl.casol.backend.identidad.application.port.out.PasswordEncoderPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

//Paso 9

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
        return passwordEncoder.matches(
                passwordPlano,
                passwordHash
        );
    }
}