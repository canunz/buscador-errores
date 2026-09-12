package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.PasswordEncoderPort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.CredencialesInvalidasException;
import cl.casol.backend.identidad.domain.exception.UsuarioInactivoException;
import org.springframework.stereotype.Service;

// Paso 10: Servicio de aplicación para autenticar usuarios
@Service
public class AutenticarUsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoderPort passwordEncoder;

    public AutenticarUsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoderPort passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario autenticar(String email, String password) {

        // 1. Buscar usuario por email
        Usuario usuario = usuarioRepository
                .buscarPorEmail(email)
                .orElseThrow(CredencialesInvalidasException::new);

        // 2. Verificar que el usuario esté activo
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }

        // 3. Comparar contraseña ingresada con el hash BCrypt
        boolean passwordCorrecta = passwordEncoder.coincide(
                password,
                usuario.getPasswordHash()
        );

        // 4. Rechazar contraseña incorrecta
        if (!passwordCorrecta) {
            throw new CredencialesInvalidasException();
        }

        // 5. Autenticación exitosa
        return usuario;
    }
}