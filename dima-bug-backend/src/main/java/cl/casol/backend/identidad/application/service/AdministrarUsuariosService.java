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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdministrarUsuariosService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoderPort passwordEncoder;

    public AdministrarUsuariosService(UsuarioRepository usuarioRepository,
                                      RolRepository rolRepository,
                                      PasswordEncoderPort passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> listar() {
        return usuarioRepository.buscarTodos();
    }

    public Usuario buscarPorId(Integer id) {
        return usuarioRepository.buscarPorId(id)
                .orElseThrow(UsuarioNoEncontradoException::new);
    }

    public Usuario crear(String nombre, String email, String password, Integer rolId) {
        if (usuarioRepository.existePorEmail(email)) {
            throw new EmailDuplicadoException();
        }

        Rol rol = obtenerRolActivo(rolId);
        LocalDateTime ahora = LocalDateTime.now();
        Usuario usuario = new Usuario(
                null, rol, nombre, email, passwordEncoder.codificar(password), true, ahora, ahora
        );
        return usuarioRepository.guardar(usuario);
    }

    public Usuario modificar(Integer id, String nombre, String email, Integer rolId) {
        Usuario actual = buscarPorId(id);
        if (usuarioRepository.existePorEmailYIdDistinto(email, id)) {
            throw new EmailDuplicadoException();
        }

        Rol rol = obtenerRolActivo(rolId);
        Usuario modificado = new Usuario(
                actual.getId(), rol, nombre, email, actual.getPasswordHash(), actual.isActivo(),
                actual.getFechaCreacion(), LocalDateTime.now()
        );
        return usuarioRepository.guardar(modificado);
    }

    public Usuario cambiarEstado(Integer id, boolean activo, String emailAutenticado) {
        Usuario actual = buscarPorId(id);
        if (!activo && actual.getEmail().equalsIgnoreCase(emailAutenticado)) {
            throw new AutoDesactivacionException();
        }

        Usuario modificado = new Usuario(
                actual.getId(), actual.getRol(), actual.getNombre(), actual.getEmail(),
                actual.getPasswordHash(), activo, actual.getFechaCreacion(), LocalDateTime.now()
        );
        return usuarioRepository.guardar(modificado);
    }

    private Rol obtenerRolActivo(Integer rolId) {
        Rol rol = rolRepository.buscarPorId(rolId)
                .orElseThrow(RolNoEncontradoException::new);
        if (!rol.isActivo()) {
            throw new RolInactivoException();
        }
        return rol;
    }
}
