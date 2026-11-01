package cl.casol.backend.identidad.infrastructure.web;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.service.AutenticarUsuarioService;
import cl.casol.backend.identidad.application.service.SolicitudCodigo;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.web.dto.LoginResponse;
import cl.casol.backend.identidad.infrastructure.web.dto.SolicitarCodigoRequest;
import cl.casol.backend.identidad.infrastructure.web.dto.SolicitarCodigoResponse;
import cl.casol.backend.identidad.infrastructure.web.dto.VerificarCodigoRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Inicio de sesión con código de acceso enviado al correo (sin contraseña).
 * 1) POST /api/auth/codigo           → valida que el correo exista y envía un código nuevo (también reenvía).
 * 2) POST /api/auth/codigo/verificar → valida el código y entrega el JWT.
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
        return ResponseEntity.ok(new SolicitarCodigoResponse(
                "Enviamos un código de acceso a su correo.",
                solicitud.segundosVigencia(),
                solicitud.segundosReenvio()
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
