package cl.casol.backend.identidad.infrastructure.web;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.service.AutenticarUsuarioService;
import cl.casol.backend.identidad.application.service.SolicitudCodigo;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.web.dto.LoginRequest;
import cl.casol.backend.identidad.infrastructure.web.dto.LoginResponse;
import cl.casol.backend.identidad.infrastructure.web.dto.RecuperarPasswordRequest;
import cl.casol.backend.identidad.infrastructure.web.dto.SolicitarCodigoRequest;
import cl.casol.backend.identidad.infrastructure.web.dto.SolicitarCodigoResponse;
import cl.casol.backend.identidad.infrastructure.web.dto.VerificarCodigoRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Ingreso mediante código para todos los roles. Los contratos retirados responden 410. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticarUsuarioService autenticarUsuarioService;
    private final TokenServicePort tokenService;

    public AuthController(
            AutenticarUsuarioService autenticarUsuarioService,
            TokenServicePort tokenService
    ) {
        this.autenticarUsuarioService = autenticarUsuarioService;
        this.tokenService = tokenService;
    }

    /** Paso 1 del login: recibe el correo y pide que se envíe un código. */
    @PostMapping("/codigo")
    public ResponseEntity<SolicitarCodigoResponse> solicitarCodigo(
            @Valid @RequestBody SolicitarCodigoRequest request
    ) {
        SolicitudCodigo solicitud = autenticarUsuarioService.solicitarCodigo(request.identificador());
        return ResponseEntity.accepted().body(new SolicitarCodigoResponse(
                "Si la cuenta está habilitada, recibirá un código. Revise también spam.",
                solicitud.segundosVigencia(), solicitud.segundosReenvio(), SolicitudCodigo.CODIGO));
    }

    // Devuelve login.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.status(410).build();
    }

    /** Paso 2 del login: revisa el código y, si está bien, entrega el token JWT. */
    @PostMapping("/codigo/verificar")
    public ResponseEntity<LoginResponse> verificarCodigo(
            @Valid @RequestBody VerificarCodigoRequest request
    ) {
        Usuario usuario = autenticarUsuarioService.verificarCodigo(
                request.identificador(),
                request.codigo()
        );

        // Código correcto: se crea el token con el que el usuario navega
        String token = tokenService.generarToken(usuario);

        return ResponseEntity.ok(new LoginResponse(
                token,
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol().getNombre(), usuario.isAccesoBasico()
        ));
    }

    // Solicita recuperación.
    @PostMapping("/recuperar/email")
    public ResponseEntity<SolicitarCodigoResponse> solicitarRecuperacion(
            @Valid @RequestBody SolicitarCodigoRequest request
    ) {
        return solicitarCodigo(request);
    }

    /** Contratos antiguos retirados sin eliminar datos ni columnas. */
    @PostMapping("/recuperar/codigo")
    public ResponseEntity<Map<String, String>> validarRecuperacion(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(410).body(Map.of("message", "Ingrese mediante un código desde el inicio de sesión."));
    }

    // Completa recuperación.
    @PostMapping("/recuperar/password")
    public ResponseEntity<Map<String, String>> completarRecuperacion(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(410).body(Map.of("message", "Ingrese mediante un código desde el inicio de sesión."));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                Map.of(
                        "email", authentication.getName(),
                        "estado", "AUTENTICADO",
                        "accesoBasico", authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("SCOPE_TICKETS_PROPIOS"))
                )
        );
    }
}
