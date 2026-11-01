package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.BienvenidaUsuarioPort;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@org.springframework.transaction.annotation.Transactional
public class AdministrarUsuariosService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final BienvenidaUsuarioPort bienvenida;

    public AdministrarUsuariosService(UsuarioRepository usuarioRepository,
                                      RolRepository rolRepository,
                                      PasswordEncoderPort passwordEncoder) {
        this(usuarioRepository, rolRepository, passwordEncoder, BienvenidaUsuarioPort.NINGUNO);
    }

    @Autowired
    public AdministrarUsuariosService(UsuarioRepository usuarioRepository,
                                      RolRepository rolRepository,
                                      PasswordEncoderPort passwordEncoder,
                                      BienvenidaUsuarioPort bienvenida) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.bienvenida = bienvenida;
    }

    public List<Usuario> listar() {
        return usuarioRepository.buscarTodos();
    }

    public Usuario buscarPorId(Integer id) {
        return usuarioRepository.buscarPorId(id)
                .orElseThrow(UsuarioNoEncontradoException::new);
    }

    /** El acceso es con código enviado al correo; el usuario ya no tiene contraseña propia. */
    public Usuario crear(String nombre, String email, Integer rolId) {
        String correo = email == null ? "" : email.trim().toLowerCase();
        if (usuarioRepository.existePorEmail(correo)) {
            throw new EmailDuplicadoException();
        }

        Rol rol = obtenerRolActivo(rolId);
        LocalDateTime ahora = LocalDateTime.now();
        Usuario usuario = new Usuario(
                null, rol, nombre, correo, claveInternaInutilizable(), true, ahora, ahora
        );
        Usuario creado = usuarioRepository.guardar(usuario);
        // Correo de bienvenida con el enlace a la página de inicio de sesión.
        bienvenida.enviar(creado.getEmail(), creado.getNombre(), rol.getNombre());
        return creado;
    }

    public Usuario modificar(Integer id, String nombre, String email, Integer rolId) {
        List<Usuario> administradores = usuarioRepository.bloquearAdministradoresActivos();
        Usuario actual = buscarPorId(id);
        String correo = email == null ? "" : email.trim().toLowerCase();
        if (usuarioRepository.existePorEmailYIdDistinto(correo, id)) {
            throw new EmailDuplicadoException();
        }

        Rol rol = obtenerRolActivo(rolId);
        protegerUltimoAdministrador(actual, actual.isActivo(), rol, administradores);
        Usuario modificado = new Usuario(
                actual.getId(), rol, nombre, correo, actual.getPasswordHash(), actual.isActivo(),
                actual.getFechaCreacion(), LocalDateTime.now()
        );
        return usuarioRepository.guardar(modificado);
    }

    public Usuario cambiarEstado(Integer id, boolean activo, String emailAutenticado) {
        List<Usuario> administradores = usuarioRepository.bloquearAdministradoresActivos();
        Usuario actual = buscarPorId(id);
        if (!activo && actual.getEmail().equalsIgnoreCase(emailAutenticado)) {
            throw new AutoDesactivacionException();
        }

        protegerUltimoAdministrador(actual, activo, actual.getRol(), administradores);
        Usuario modificado = new Usuario(
                actual.getId(), actual.getRol(), actual.getNombre(), actual.getEmail(),
                actual.getPasswordHash(), activo, actual.getFechaCreacion(), LocalDateTime.now()
        );
        return usuarioRepository.guardar(modificado);
    }

    /** Borra la cuenta. No se puede eliminar la propia ni al último administrador activo. */
    public void eliminar(Integer id, String emailAutenticado) {
        List<Usuario> administradores = usuarioRepository.bloquearAdministradoresActivos();
        Usuario actual = buscarPorId(id);
        if (actual.getEmail().equalsIgnoreCase(emailAutenticado)) {
            throw new cl.casol.backend.identidad.domain.exception.AutoEliminacionException();
        }
        protegerUltimoAdministrador(actual, false, actual.getRol(), administradores);
        usuarioRepository.eliminar(actual);
    }

    private void protegerUltimoAdministrador(Usuario actual, boolean activo, Rol rol, List<Usuario> administradores) {
        if (actual.isActivo() && "ADMINISTRADOR".equalsIgnoreCase(actual.getRol().getNombre().trim())
                && (!activo || !"ADMINISTRADOR".equalsIgnoreCase(rol.getNombre().trim()))
                && administradores.stream().noneMatch(u -> !u.getId().equals(actual.getId())))
            throw new cl.casol.backend.identidad.domain.exception.UltimoAdministradorException();
    }

    /**
     * La columna de contraseña sigue existiendo en la base de datos, pero ya no se usa para ingresar.
     * Se guarda un valor aleatorio que nadie conoce, solo para cumplir con la columna obligatoria.
     */
    private String claveInternaInutilizable() {
        // BCrypt acepta como máximo 72 bytes: 64 caracteres aleatorios caben de sobra.
        String aleatorio = java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
        return passwordEncoder.codificar(aleatorio);
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
