package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.PasswordEncoderPort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.CredencialesInvalidasException;
import cl.casol.backend.identidad.domain.exception.UsuarioInactivoException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AutenticarUsuarioServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoderPort passwordEncoder = mock(PasswordEncoderPort.class);
    private final AutenticarUsuarioService service =
            new AutenticarUsuarioService(usuarioRepository, passwordEncoder);

    @Test
    void autenticaUsuarioActivoConPasswordCorrecta() {
        Usuario usuario = usuario(true);
        when(usuarioRepository.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.coincide("password", "hash-bcrypt")).thenReturn(true);

        Usuario resultado = service.autenticar("user@example.com", "password");

        assertSame(usuario, resultado);
        verify(passwordEncoder).coincide("password", "hash-bcrypt");
    }

    @Test
    void rechazaEmailInexistenteComoCredencialesInvalidas() {
        when(usuarioRepository.buscarPorEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(CredencialesInvalidasException.class,
                () -> service.autenticar("missing@example.com", "password"));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void rechazaPasswordIncorrectaComoCredencialesInvalidas() {
        when(usuarioRepository.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(passwordEncoder.coincide("bad-password", "hash-bcrypt")).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class,
                () -> service.autenticar("user@example.com", "bad-password"));
    }

    @Test
    void rechazaUsuarioInactivo() {
        when(usuarioRepository.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(false)));

        assertThrows(UsuarioInactivoException.class,
                () -> service.autenticar("user@example.com", "password"));
        verifyNoInteractions(passwordEncoder);
    }

    private Usuario usuario(boolean activo) {
        Rol rol = new Rol(1, "USUARIO", null, true, null, null);
        return new Usuario(1, rol, "Usuario", "user@example.com", "hash-bcrypt",
                activo, null, null);
    }
}
