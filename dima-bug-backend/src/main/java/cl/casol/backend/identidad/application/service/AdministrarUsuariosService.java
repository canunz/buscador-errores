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

/**
 * Servicio de administrar usuarios (módulo de usuarios y acceso).
 * Aquí van las reglas de negocio; para leer o guardar datos usa los puertos de salida.
 */
@Service
@org.springframework.transaction.annotation.Transactional
public class AdministrarUsuariosService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoderPort passwordEncoder;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private cl.casol.backend.identidad.application.port.out.CodigoAccesoRepository codigos;

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

    // Busca por id.
    public Usuario buscarPorId(Integer id) {
        return usuarioRepository.buscarPorId(id)
                .orElseThrow(UsuarioNoEncontradoException::new);
    }

    // Crea el nombre.
    public Usuario crear(String nombre, String email, String password, Integer rolId) {
        String correo = email == null ? "" : email.trim().toLowerCase();
        if (usuarioRepository.existePorEmail(correo)) {
            throw new EmailDuplicadoException();
        }

        Rol rol = obtenerRolActivo(rolId);
        LocalDateTime ahora = LocalDateTime.now();
        Usuario usuario = new Usuario(
                null, rol, nombre, correo, passwordEncoder.codificar(java.util.UUID.randomUUID().toString()), true, ahora, ahora
        );
        return usuarioRepository.guardar(usuario);
    }

    // Modifica por id.
    public Usuario modificar(Integer id, String nombre, String email, Integer rolId, String password) {
        List<Usuario> administradores = usuarioRepository.bloquearAdministradoresActivos();
        Usuario actual = usuarioRepository.buscarPorIdBloqueado(id).orElseThrow(UsuarioNoEncontradoException::new);
        String correo = email == null ? "" : email.trim().toLowerCase();
        if (usuarioRepository.existePorEmailYIdDistinto(correo, id)) {
            throw new EmailDuplicadoException();
        }

        Rol rol = obtenerRolActivo(rolId);
        if (actual.isAccesoBasico() && (!"USUARIO".equals(rol.getNombre()) || !correo.equalsIgnoreCase(actual.getEmail())))
            throw new IllegalArgumentException("El autorregistro conserva su correo verificado y su rol básico");
        protegerUltimoAdministrador(actual, actual.isActivo(), rol, administradores);
        String passwordHash = actual.getPasswordHash();
        if (password != null && !password.isBlank()) {
            passwordHash = passwordEncoder.codificar(password.trim());
        }
        Usuario modificado = new Usuario(
                actual.getId(), rol, nombre, correo, passwordHash, actual.isActivo(),
                actual.getFechaCreacion(), LocalDateTime.now(), actual.getEstadoRegistro()
        ).conUltimoIngreso(actual.getUltimoIngreso());
        return usuarioRepository.guardar(modificado);
    }

    // Cambia password.
    public Usuario cambiarPassword(Integer id, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
        Usuario actual = usuarioRepository.buscarPorIdBloqueado(id).orElseThrow(UsuarioNoEncontradoException::new);
        Usuario modificado = new Usuario(
                actual.getId(), actual.getRol(), actual.getNombre(), actual.getEmail(),
                passwordEncoder.codificar(password.trim()), actual.isActivo(),
                actual.getFechaCreacion(), LocalDateTime.now(), actual.getEstadoRegistro()
        ).conUltimoIngreso(actual.getUltimoIngreso());
        return usuarioRepository.guardar(modificado);
    }

    // Cambia estado.
    public Usuario cambiarEstado(Integer id, boolean activo, String emailAutenticado) {
        List<Usuario> administradores = usuarioRepository.bloquearAdministradoresActivos();
        Usuario actual = usuarioRepository.buscarPorIdBloqueado(id).orElseThrow(UsuarioNoEncontradoException::new);
        if (!activo && actual.getEmail().equalsIgnoreCase(emailAutenticado)) {
            throw new AutoDesactivacionException();
        }

        if (activo && actual.isAccesoBasico() && actual.getEstadoRegistro() != cl.casol.backend.identidad.domain.EstadoRegistro.AUTO_CONFIRMADO)
            throw new IllegalArgumentException("La cuenta requiere verificación de correo");
        protegerUltimoAdministrador(actual, activo, actual.getRol(), administradores);
        if (!activo && codigos != null) codigos.invalidarPendientes(id);
        Usuario modificado = new Usuario(
                actual.getId(), actual.getRol(), actual.getNombre(), actual.getEmail(),
                actual.getPasswordHash(), activo, actual.getFechaCreacion(), LocalDateTime.now(),
                !activo && actual.getEstadoRegistro() == cl.casol.backend.identidad.domain.EstadoRegistro.AUTO_PENDIENTE
                    ? cl.casol.backend.identidad.domain.EstadoRegistro.AUTO_CANCELADO : actual.getEstadoRegistro()
        ).conUltimoIngreso(actual.getUltimoIngreso());
        return usuarioRepository.guardar(modificado);
    }

    // Protege último administrador.
    private void protegerUltimoAdministrador(Usuario actual, boolean activo, Rol rol, List<Usuario> administradores) {
        if (actual.isActivo() && "ADMINISTRADOR".equalsIgnoreCase(actual.getRol().getNombre().trim())
                && (!activo || !"ADMINISTRADOR".equalsIgnoreCase(rol.getNombre().trim()))
                && administradores.stream().noneMatch(u -> !u.getId().equals(actual.getId())))
            throw new cl.casol.backend.identidad.domain.exception.UltimoAdministradorException();
    }

    // Obtiene rol activo.
    private Rol obtenerRolActivo(Integer rolId) {
        Rol rol = rolRepository.buscarPorId(rolId)
                .orElseThrow(RolNoEncontradoException::new);
        if (!rol.isActivo()) {
            throw new RolInactivoException();
        }
        return rol;
    }
}
