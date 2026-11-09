package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.CodigoAccesoRepository;
import cl.casol.backend.identidad.application.port.out.EnvioCodigoAccesoPort;
import cl.casol.backend.identidad.application.port.out.PasswordEncoderPort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.CodigoAcceso;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.CodigoAccesoInvalidoException;
import cl.casol.backend.identidad.domain.exception.CorreoNoRegistradoException;
import cl.casol.backend.identidad.domain.exception.CredencialesInvalidasException;
import cl.casol.backend.identidad.domain.exception.ReenvioCodigoAnticipadoException;
import cl.casol.backend.identidad.domain.exception.UsuarioInactivoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Ingreso: Administrador y Soporte con contraseña; el resto con código de 6 dígitos al correo.
 */
@Service
public class AutenticarUsuarioService {

    public static final int MAX_INTENTOS = 5;

    private static final Logger log = LoggerFactory.getLogger(AutenticarUsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final CodigoAccesoRepository codigoRepository;
    private final EnvioCodigoAccesoPort envioCodigo;
    private final PasswordEncoderPort encoder;
    private final Clock clock;
    private final int minutosVigencia;
    private final int segundosReenvio;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public AutenticarUsuarioService(
            UsuarioRepository usuarioRepository,
            CodigoAccesoRepository codigoRepository,
            EnvioCodigoAccesoPort envioCodigo,
            PasswordEncoderPort encoder,
            @Value("${dimabug.auth.codigo.minutos-vigencia:5}") int minutosVigencia,
            @Value("${dimabug.auth.codigo.segundos-reenvio:4}") int segundosReenvio
    ) {
        this(usuarioRepository, codigoRepository, envioCodigo, encoder, Clock.systemDefaultZone(),
                minutosVigencia, segundosReenvio);
    }

    AutenticarUsuarioService(
            UsuarioRepository usuarioRepository,
            CodigoAccesoRepository codigoRepository,
            EnvioCodigoAccesoPort envioCodigo,
            PasswordEncoderPort encoder,
            Clock clock,
            int minutosVigencia,
            int segundosReenvio
    ) {
        this.usuarioRepository = usuarioRepository;
        this.codigoRepository = codigoRepository;
        this.envioCodigo = envioCodigo;
        this.encoder = encoder;
        this.clock = clock;
        this.minutosVigencia = Math.max(1, minutosVigencia);
        this.segundosReenvio = Math.max(0, segundosReenvio);
    }

    /**
     * Valida que el correo pertenezca a un usuario activo y le envía un código nuevo.
     * Si el correo no está registrado o la cuenta está inactiva, no se envía nada.
     */
    @Transactional
    public SolicitudCodigo solicitarCodigo(String correo) {
        Optional<Usuario> encontrado = buscar(correo);
        if (encontrado.isEmpty()) {
            log.info("Código de acceso rechazado: el correo '{}' no está registrado", correo);
            throw new CorreoNoRegistradoException();
        }
        Usuario usuario = encontrado.get();
        if (!usuario.isActivo()) {
            log.info("Código de acceso rechazado: la cuenta '{}' está inactiva", usuario.getEmail());
            throw new UsuarioInactivoException();
        }
        if (ingresaConPassword(usuario)) {
            return SolicitudCodigo.password();
        }
        return emitirCodigo(usuario, false);
    }

    /** Código al correo para definir una contraseña nueva. */
    @Transactional
    public SolicitudCodigo solicitarRecuperacion(String correo) {
        Usuario usuario = exigirCuentaActiva(correo);
        return emitirCodigo(usuario, true);
    }

    /** Comprueba el código de recuperación sin consumirlo. */
    @Transactional(noRollbackFor = CodigoAccesoInvalidoException.class)
    public void validarRecuperacion(String identificador, String codigoIngresado) {
        Usuario usuario = exigirCuentaActiva(identificador);
        coincidirCodigoPendiente(usuario, codigoIngresado, false);
    }

    /** Consume el código y guarda la contraseña nueva. */
    @Transactional(noRollbackFor = CodigoAccesoInvalidoException.class)
    public void completarRecuperacion(String identificador, String codigoIngresado, String passwordNueva) {
        if (passwordNueva == null || passwordNueva.trim().length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres");
        }
        Usuario usuario = exigirCuentaActiva(identificador);
        coincidirCodigoPendiente(usuario, codigoIngresado, true);
        Usuario actualizado = new Usuario(
                usuario.getId(), usuario.getRol(), usuario.getNombre(), usuario.getEmail(),
                encoder.codificar(passwordNueva.trim()), usuario.isActivo(),
                usuario.getFechaCreacion(), LocalDateTime.now(clock)
        );
        usuarioRepository.guardar(actualizado);
        log.info("Contraseña restablecida para '{}'", usuario.getEmail());
    }

    /** Valida el código ingresado y, si es correcto, lo consume y devuelve el usuario autenticado. */
    @Transactional(noRollbackFor = CodigoAccesoInvalidoException.class)
    public Usuario verificarCodigo(String identificador, String codigoIngresado) {
        Usuario usuario = buscar(identificador).orElseThrow(CodigoAccesoInvalidoException::new);
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }
        if (ingresaConPassword(usuario)) {
            throw new CredencialesInvalidasException();
        }

        coincidirCodigoPendiente(usuario, codigoIngresado, true);
        usuarioRepository.registrarIngreso(usuario.getId(), LocalDateTime.now(clock));
        return usuario;
    }

    /** Administrador y Soporte ingresan con contraseña. */
    @Transactional
    public Usuario autenticar(String identificador, String password) {
        Usuario usuario = buscar(identificador).orElseThrow(CredencialesInvalidasException::new);
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }
        if (!ingresaConPassword(usuario) || !encoder.coincide(password, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        usuarioRepository.registrarIngreso(usuario.getId(), LocalDateTime.now(clock));
        return usuario;
    }

    static boolean ingresaConPassword(Usuario usuario) {
        if (usuario == null || usuario.getRol() == null || usuario.getRol().getNombre() == null) {
            return false;
        }
        String rol = usuario.getRol().getNombre().trim().toUpperCase();
        return "ADMINISTRADOR".equals(rol)
                || "SOPORTE".equals(rol)
                || "SOPORTE TI".equals(rol)
                || "TECNICO".equals(rol)
                || "TÉCNICO".equals(rol)
                || "TI".equals(rol);
    }

    private SolicitudCodigo emitirCodigo(Usuario usuario, boolean recuperacion) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        codigoRepository.buscarUltimo(usuario.getId()).ifPresent(ultimo -> {
            long transcurridos = Duration.between(ultimo.creadoEn(), ahora).getSeconds();
            if (transcurridos < segundosReenvio) {
                throw new ReenvioCodigoAnticipadoException(segundosReenvio - transcurridos);
            }
        });

        String codigo = String.format("%06d", random.nextInt(1_000_000));
        codigoRepository.invalidarPendientes(usuario.getId());
        codigoRepository.guardar(new CodigoAcceso(
                null, usuario.getId(), encoder.codificar(codigo), ahora,
                ahora.plusMinutes(minutosVigencia), 0, false));

        log.info("Código {} generado para el usuario registrado '{}'", recuperacion ? "de recuperación" : "de acceso", usuario.getEmail());
        if (recuperacion) {
            envioCodigo.enviarRecuperacion(usuario.getEmail(), usuario.getNombre(), codigo, minutosVigencia);
        } else {
            envioCodigo.enviar(usuario.getEmail(), usuario.getNombre(), codigo, minutosVigencia);
        }
        return new SolicitudCodigo(minutosVigencia * 60, segundosReenvio);
    }

    private Usuario exigirCuentaActiva(String correo) {
        Usuario usuario = buscar(correo).orElseThrow(CorreoNoRegistradoException::new);
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }
        return usuario;
    }

    private CodigoAcceso coincidirCodigoPendiente(Usuario usuario, String codigoIngresado, boolean consumir) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        CodigoAcceso pendiente = codigoRepository.buscarPendiente(usuario.getId())
                .filter(c -> c.disponible(ahora, MAX_INTENTOS))
                .orElseThrow(CodigoAccesoInvalidoException::new);

        String codigo = codigoIngresado == null ? "" : codigoIngresado.replaceAll("\\s", "");
        if (!codigo.matches("\\d{6}") || !encoder.coincide(codigo, pendiente.codigoHash())) {
            codigoRepository.guardar(pendiente.conIntentoFallido());
            throw new CodigoAccesoInvalidoException();
        }
        if (consumir) {
            pendiente = pendiente.marcarUsado();
            codigoRepository.guardar(pendiente);
        }
        return pendiente;
    }

    /** Solo se ingresa con el correo completo (no con el nombre de usuario). */
    private Optional<Usuario> buscar(String correo) {
        String valor = correo == null ? "" : correo.trim();
        if (!valor.contains("@")) {
            return Optional.empty();
        }
        return usuarioRepository.buscarPorEmail(valor)
                .filter(u -> u.getEmail() != null && u.getEmail().trim().equalsIgnoreCase(valor));
    }
}
