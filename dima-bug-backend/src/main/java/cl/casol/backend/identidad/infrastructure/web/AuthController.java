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

/**
 * Ingreso: Administrador/Soporte con contraseña; el resto con código al correo.
 * POST /api/auth/codigo           → modo CODIGO o PASSWORD según el rol.
 * POST /api/auth/codigo/verificar → código de 6 dígitos.
 * POST /api/auth/login            → correo y contraseña (solo staff).
 */
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

    @PostMapping("/codigo")
    public ResponseEntity<SolicitarCodigoResponse> solicitarCodigo(
            @Valid @RequestBody SolicitarCodigoRequest request
    ) {
        SolicitudCodigo solicitud = autenticarUsuarioService.solicitarCodigo(request.identificador());
        if (SolicitudCodigo.PASSWORD.equals(solicitud.modo())) {
            return ResponseEntity.ok(new SolicitarCodigoResponse(
                    "Ingrese su contraseña.",
                    0,
                    0,
                    SolicitudCodigo.PASSWORD
            ));
        }
        return ResponseEntity.ok(new SolicitarCodigoResponse(
                "Enviamos un código de acceso a su correo.",
                solicitud.segundosVigencia(),
                solicitud.segundosReenvio(),
                SolicitudCodigo.CODIGO
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario = autenticarUsuarioService.autenticar(request.identificador(), request.clave());
        String token = tokenService.generarToken(usuario);
        return ResponseEntity.ok(new LoginResponse(
                token,
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol().getNombre()
        ));
    }

    @PostMapping("/codigo/verificar")
    public ResponseEntity<LoginResponse> verificarCodigo(
            @Valid @RequestBody VerificarCodigoRequest request
    ) {
        Usuario usuario = autenticarUsuarioService.verificarCodigo(
                request.identificador(),
                request.codigo()
        );

        String token = tokenService.generarToken(usuario);

        return ResponseEntity.ok(new LoginResponse(
                token,
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol().getNombre()
        ));
    }

    @PostMapping("/recuperar/email")
    public ResponseEntity<SolicitarCodigoResponse> solicitarRecuperacion(
            @Valid @RequestBody SolicitarCodigoRequest request
    ) {
        SolicitudCodigo solicitud = autenticarUsuarioService.solicitarRecuperacion(request.identificador());
        return ResponseEntity.ok(new SolicitarCodigoResponse(
                "Enviamos un código de 6 dígitos a su correo.",
                solicitud.segundosVigencia(),
                solicitud.segundosReenvio(),
                SolicitudCodigo.CODIGO
        ));
    }

    @PostMapping("/recuperar/codigo")
    public ResponseEntity<Map<String, String>> validarRecuperacion(
            @Valid @RequestBody VerificarCodigoRequest request
    ) {
        autenticarUsuarioService.validarRecuperacion(request.identificador(), request.codigo());
        return ResponseEntity.ok(Map.of("message", "Código validado. Ya puede definir su contraseña."));
    }

    @PostMapping("/recuperar/password")
    public ResponseEntity<Map<String, String>> completarRecuperacion(
            @Valid @RequestBody RecuperarPasswordRequest request
    ) {
        autenticarUsuarioService.completarRecuperacion(
                request.identificador(),
                request.codigo(),
                request.clave()
        );
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada. Ya puede iniciar sesión."));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                Map.of(
                        "email", authentication.getName(),
                        "estado", "AUTENTICADO"
                )
        );
    }
}
