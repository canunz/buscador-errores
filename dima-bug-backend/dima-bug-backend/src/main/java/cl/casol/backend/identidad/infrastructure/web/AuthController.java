package cl.casol.backend.identidad.infrastructure.web;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.service.AutenticarUsuarioService;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.web.dto.LoginRequest;
import cl.casol.backend.identidad.infrastructure.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        Usuario usuario = autenticarUsuarioService.autenticar(
                request.email(),
                request.password()
        );

        String token = tokenService.generarToken(usuario);

        LoginResponse response = new LoginResponse(
                token,
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol().getNombre()
        );

        return ResponseEntity.ok(response);
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