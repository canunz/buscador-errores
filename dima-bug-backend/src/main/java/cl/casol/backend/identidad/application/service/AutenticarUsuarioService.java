package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.CodigoAccesoRepository;
import cl.casol.backend.identidad.application.port.out.EnvioCodigoAccesoPort;
import cl.casol.backend.identidad.application.port.out.PasswordEncoderPort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.CodigoAcceso;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.CodigoAccesoInvalidoException;
import cl.casol.backend.identidad.domain.exception.CorreoNoRegistradoException;
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
 * Inicio de sesión sin contraseña: el usuario pide un código, lo recibe en su correo y lo ingresa.
 * El código es de 6 dígitos, se usa una sola vez, vence a los pocos minutos y cada solicitud
 * genera uno nuevo (el anterior queda sin efecto).
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
            @Value("${dimabug.auth.codigo.segundos-reenvio:60}") int segundosReenvio
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

        log.info("Código de acceso generado para el usuario registrado '{}'", usuario.getEmail());
        envioCodigo.enviar(usuario.getEmail(), usuario.getNombre(), codigo, minutosVigencia);
        return new SolicitudCodigo(minutosVigencia * 60, segundosReenvio);
    }

    /** Valida el código ingresado y, si es correcto, lo consume y devuelve el usuario autenticado. */
    @Transactional(noRollbackFor = CodigoAccesoInvalidoException.class)
    public Usuario verificarCodigo(String identificador, String codigoIngresado) {
        Usuario usuario = buscar(identificador).orElseThrow(CodigoAccesoInvalidoException::new);
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        CodigoAcceso pendiente = codigoRepository.buscarPendiente(usuario.getId())
                .filter(c -> c.disponible(ahora, MAX_INTENTOS))
                .orElseThrow(CodigoAccesoInvalidoException::new);

        String codigo = codigoIngresado == null ? "" : codigoIngresado.replaceAll("\\s", "");
        if (!codigo.matches("\\d{6}") || !encoder.coincide(codigo, pendiente.codigoHash())) {
            codigoRepository.guardar(pendiente.conIntentoFallido());
            throw new CodigoAccesoInvalidoException();
        }

        codigoRepository.guardar(pendiente.marcarUsado());
        return usuario;
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
