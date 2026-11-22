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
import cl.casol.backend.identidad.domain.exception.IngresoBloqueadoException;
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
 * Ingreso con código de un solo uso para todas las cuentas activas autorizadas.
 */
@Service
public class AutenticarUsuarioService {

    public static final int MAX_INTENTOS = CodigoAcceso.MAX_INTENTOS;


    private final UsuarioRepository usuarioRepository;
    private final CodigoAccesoRepository codigoRepository;
    private final EnvioCodigoAccesoPort envioCodigo;
    private final PasswordEncoderPort encoder;
    private final Clock clock;
    private final int minutosVigencia;
    private final int segundosReenvio;
    @Value("${dimabug.auth.codigo.maximo-15-minutos:5}")
    private int maximoQuinceMinutos = 5;
    @Value("${dimabug.auth.codigo.maximo-dia:20}")
    private int maximoDia = 20;
    @Autowired(required=false)
    private cl.casol.backend.identidad.application.port.out.RegistroUniversalPort registroUniversal;

    /**
     * Indica si se aceptan correos nuevos (registro abierto). Viene apagado por defecto:
     * en ese caso solo pueden pedir código los correos que ya existen en la base de datos.
     */
    @Value("${dimabug.auth.registro-universal.habilitado:false}")
    private boolean registroAbierto = false;
    private final SecureRandom random = new SecureRandom();

    /** Controla los bloqueos por códigos mal ingresados (3 intentos, luego segundos y después minutos). */
    private final ControlBloqueoIngreso bloqueos;

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
        this.bloqueos = new ControlBloqueoIngreso(clock);
    }

    /** Respuesta pública uniforme; solo las cuentas activas y con rol activo reciben correo. */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SolicitudCodigo solicitarCodigo(String correo) {
        // Código aleatorio y seguro de 6 dígitos
        String codigo = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String hash = encoder.codificar(codigo); // Mismo trabajo costoso para cuentas desconocidas.
        SolicitudCodigo respuesta = new SolicitudCodigo(minutosVigencia * 60, segundosReenvio);
        Optional<Usuario> cuenta = registroUniversal == null ? buscar(correo)
            : registroUniversal.resolver(correo, true, encoder.codificar(java.util.UUID.randomUUID().toString()));

        // Si el correo no está en la base de datos (y no hay registro abierto), se avisa
        // que no existe y no se genera ningún código.
        if (cuenta.isEmpty() && !registroAbierto) throw new CorreoNoRegistradoException();

        Optional<Usuario> encontrado = cuenta.filter(Usuario::puedeRecibirCodigo);
        if (encontrado.isEmpty()) return respuesta;
        Usuario usuario = encontrado.get();
        codigoRepository.bloquearUsuario(usuario.getId());
        LocalDateTime ahora = LocalDateTime.now(clock);
        // Límites: 60 segundos entre códigos y un máximo cada 15 minutos y por día
        boolean reciente = codigoRepository.buscarUltimo(usuario.getId())
                .map(c -> Duration.between(c.creadoEn(), ahora).getSeconds() < segundosReenvio).orElse(false);
        if (reciente || codigoRepository.contarDesde(usuario.getId(), ahora.minusMinutes(15)) >= maximoQuinceMinutos
                || codigoRepository.contarDesde(usuario.getId(), ahora.minusDays(1)) >= maximoDia) return respuesta;
        // El código anterior deja de servir
        codigoRepository.invalidarPendientes(usuario.getId());
        CodigoAcceso registro = codigoRepository.guardar(new CodigoAcceso(null, usuario.getId(), hash,
                ahora, ahora.plusMinutes(minutosVigencia), 0, false));
        // Se deja en la cola; DespacharCodigosService lo envía por Gmail
        envioCodigo.encolar(registro, usuario.getEmail(), usuario.getNombre(), codigo, minutosVigencia);
        return respuesta;
    }

    // Indica si está autorizado.
    private boolean autorizado(Usuario u) {
        return u.puedeRecibirCodigo();
    }

    /** Compatibilidad binaria durante la transición; ya no modifica contraseñas. */
    @Deprecated
    @Transactional
    public SolicitudCodigo solicitarRecuperacion(String correo) { return solicitarCodigo(correo); }
    // Valida recuperación.
    @Deprecated
    public void validarRecuperacion(String correo, String codigo) { throw new CredencialesInvalidasException(); }
    // Completa recuperación.
    @Deprecated
    public void completarRecuperacion(String correo, String codigo, String password) { throw new CredencialesInvalidasException(); }

    /** Valida el código ingresado y, si es correcto, lo consume y devuelve el usuario autenticado. */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED, noRollbackFor = CodigoAccesoInvalidoException.class)
    public Usuario verificarCodigo(String identificador, String codigoIngresado) {
        Usuario usuario = (registroUniversal == null ? buscar(identificador)
            : registroUniversal.resolver(identificador, false, null)).orElseThrow(CodigoAccesoInvalidoException::new);
        if (!autorizado(usuario)) throw new CodigoAccesoInvalidoException();

        // Se toma el turno del usuario en la base de datos, para que dos intentos al mismo tiempo no se crucen.
        codigoRepository.bloquearUsuario(usuario.getId());

        // Si el usuario está esperando por errores anteriores, no se revisa el código hasta que pase el tiempo.
        bloqueos.exigirDesbloqueado(usuario.getId());

        try {
            coincidirCodigoPendiente(usuario, codigoIngresado, true);
        } catch (CodigoAccesoInvalidoException e) {
            // Cada código mal escrito suma un error. Si con este error se llega al límite,
            // se avisa de inmediato que quedó bloqueado y cuánto tiene que esperar.
            long segundosBloqueo = bloqueos.registrarFallo(usuario.getId());
            if (segundosBloqueo > 0) throw new IngresoBloqueadoException(segundosBloqueo);
            throw e;
        }

        // Entró bien: se olvidan los errores que tenía.
        bloqueos.limpiar(usuario.getId());
        if (usuario.getEstadoRegistro() == cl.casol.backend.identidad.domain.EstadoRegistro.AUTO_PENDIENTE)
            usuario = usuarioRepository.guardar(usuario.confirmarRegistro());
        usuarioRepository.registrarIngreso(usuario.getId(), LocalDateTime.now(clock));
        return usuario;
    }

    /** El login público por contraseña está retirado; no se borran hashes existentes. */
    @Deprecated
    public Usuario autenticar(String identificador, String password) { throw new CredencialesInvalidasException(); }

    /** Busca el código vigente del usuario y lo compara; si no calza, suma un intento fallido. */
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
        if (consumir) codigoRepository.guardar(pendiente.marcarUsado());
        return pendiente;
    }

    // Busca el correo.
    private Optional<Usuario> buscar(String correo) {
        String valor = correo == null ? "" : correo.trim().toLowerCase(java.util.Locale.ROOT);
        if (!valor.contains("@")) return Optional.empty();
        return usuarioRepository.buscarPorEmail(valor)
                .filter(u -> u.getEmail() != null && u.getEmail().trim().equalsIgnoreCase(valor));
    }
}
