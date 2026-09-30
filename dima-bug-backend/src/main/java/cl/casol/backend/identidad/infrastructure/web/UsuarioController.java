package cl.casol.backend.identidad.infrastructure.web;

import cl.casol.backend.identidad.application.service.AdministrarUsuariosService;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.web.dto.CambiarEstadoUsuarioRequest;
import cl.casol.backend.identidad.infrastructure.web.dto.CrearUsuarioRequest;
import cl.casol.backend.identidad.infrastructure.web.dto.ModificarUsuarioRequest;
import cl.casol.backend.identidad.infrastructure.web.dto.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final AdministrarUsuariosService service;

    public UsuarioController(AdministrarUsuariosService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return service.listar().stream().map(UsuarioResponse::from).toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(@PathVariable Integer id) {
        return UsuarioResponse.from(service.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        Usuario usuario = service.crear(
                request.nombre(), request.email(), request.password(), request.rolId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(usuario));
    }

    @PutMapping("/{id}")
    public UsuarioResponse modificar(@PathVariable Integer id,
                                     @Valid @RequestBody ModificarUsuarioRequest request) {
        return UsuarioResponse.from(
                service.modificar(id, request.nombre(), request.email(), request.rolId())
        );
    }

    @PatchMapping("/{id}/estado")
    public UsuarioResponse cambiarEstado(@PathVariable Integer id,
                                         @Valid @RequestBody CambiarEstadoUsuarioRequest request,
                                         Authentication authentication) {
        return UsuarioResponse.from(
                service.cambiarEstado(id, request.activo(), authentication.getName())
        );
    }
}
