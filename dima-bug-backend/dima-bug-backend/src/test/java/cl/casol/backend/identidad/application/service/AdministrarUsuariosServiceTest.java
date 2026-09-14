package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.PasswordEncoderPort;
import cl.casol.backend.identidad.application.port.out.RolRepository;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.AutoDesactivacionException;
import cl.casol.backend.identidad.domain.exception.EmailDuplicadoException;
import cl.casol.backend.identidad.domain.exception.RolInactivoException;
import cl.casol.backend.identidad.domain.exception.RolNoEncontradoException;
import cl.casol.backend.identidad.domain.exception.UsuarioNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdministrarUsuariosServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final RolRepository rolRepository = mock(RolRepository.class);
    private final PasswordEncoderPort passwordEncoder = mock(PasswordEncoderPort.class);
    private final AdministrarUsuariosService service =
            new AdministrarUsuariosService(usuarioRepository, rolRepository, passwordEncoder);

    @Test
    void creaUsuarioActivoConPasswordCodificada() {
        Rol rol = rol(2, "TECNICO", true);
        when(rolRepository.buscarPorId(2)).thenReturn(Optional.of(rol));
        when(passwordEncoder.codificar("ClaveInicialSegura")).thenReturn("$2a$10$hash");
        when(usuarioRepository.guardar(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        Usuario creado = service.crear("Nuevo", "nuevo@dimarsa.cl", "ClaveInicialSegura", 2);

        assertTrue(creado.isActivo());
        assertEquals("$2a$10$hash", creado.getPasswordHash());
        assertEquals(2, creado.getRol().getId());
        verify(passwordEncoder).codificar("ClaveInicialSegura");
    }

    @Test
    void rechazaEmailDuplicadoAlCrear() {
        when(usuarioRepository.existePorEmail("usado@dimarsa.cl")).thenReturn(true);
        assertThrows(EmailDuplicadoException.class,
                () -> service.crear("Nuevo", "usado@dimarsa.cl", "clave", 2));
        verify(passwordEncoder, never()).codificar("clave");
    }

    @Test
    void rechazaRolInexistente() {
        when(rolRepository.buscarPorId(99)).thenReturn(Optional.empty());
        assertThrows(RolNoEncontradoException.class,
                () -> service.crear("Nuevo", "nuevo@dimarsa.cl", "clave", 99));
    }

    @Test
    void rechazaRolInactivo() {
        when(rolRepository.buscarPorId(2)).thenReturn(Optional.of(rol(2, "TECNICO", false)));
        assertThrows(RolInactivoException.class,
                () -> service.crear("Nuevo", "nuevo@dimarsa.cl", "clave", 2));
    }

    @Test
    void modificaNombreEmailYRolSinCambiarPassword() {
        Usuario actual = usuario(5, "anterior@dimarsa.cl", true, rol(2, "TECNICO", true));
        Rol administrador = rol(1, "ADMINISTRADOR", true);
        when(usuarioRepository.buscarPorId(5)).thenReturn(Optional.of(actual));
        when(rolRepository.buscarPorId(1)).thenReturn(Optional.of(administrador));
        when(usuarioRepository.guardar(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        Usuario modificado = service.modificar(5, "Nombre nuevo", "nuevo@dimarsa.cl", 1);

        assertEquals("Nombre nuevo", modificado.getNombre());
        assertEquals("nuevo@dimarsa.cl", modificado.getEmail());
        assertEquals("hash-existente", modificado.getPasswordHash());
        assertEquals("ADMINISTRADOR", modificado.getRol().getNombre());
    }

    @Test
    void rechazaEmailDeOtroUsuarioAlModificar() {
        when(usuarioRepository.buscarPorId(5)).thenReturn(Optional.of(
                usuario(5, "anterior@dimarsa.cl", true, rol(2, "TECNICO", true))));
        when(usuarioRepository.existePorEmailYIdDistinto("usado@dimarsa.cl", 5)).thenReturn(true);

        assertThrows(EmailDuplicadoException.class,
                () -> service.modificar(5, "Nombre", "usado@dimarsa.cl", 2));
    }

    @Test
    void desactivaUsuarioSinEliminarlo() {
        Usuario actual = usuario(5, "tecnico@dimarsa.cl", true, rol(2, "TECNICO", true));
        when(usuarioRepository.buscarPorId(5)).thenReturn(Optional.of(actual));
        when(usuarioRepository.guardar(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        Usuario modificado = service.cambiarEstado(5, false, "admin@dimarsa.cl");

        assertFalse(modificado.isActivo());
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).guardar(captor.capture());
        assertEquals(5, captor.getValue().getId());
    }

    @Test
    void administradorNoPuedeDesactivarseASiMismo() {
        when(usuarioRepository.buscarPorId(1)).thenReturn(Optional.of(
                usuario(1, "admin@dimarsa.cl", true, rol(1, "ADMINISTRADOR", true))));

        assertThrows(AutoDesactivacionException.class,
                () -> service.cambiarEstado(1, false, "ADMIN@dimarsa.cl"));
        verify(usuarioRepository, never()).guardar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usuarioInexistenteDevuelveError() {
        when(usuarioRepository.buscarPorId(404)).thenReturn(Optional.empty());
        assertThrows(UsuarioNoEncontradoException.class, () -> service.buscarPorId(404));
    }

    private Rol rol(int id, String nombre, boolean activo) {
        return new Rol(id, nombre, nombre, activo, null, null);
    }

    private Usuario usuario(int id, String email, boolean activo, Rol rol) {
        return new Usuario(id, rol, "Usuario", email, "hash-existente", activo, null, null);
    }
}
