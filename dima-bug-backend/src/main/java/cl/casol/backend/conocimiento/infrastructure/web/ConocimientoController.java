package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MantenerConocimientoService;
import cl.casol.backend.conocimiento.application.service.BuscarConocimientoService;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import cl.casol.backend.conocimiento.domain.exception.ClasificacionInvalidaException;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conocimientos")
public class ConocimientoController {
    private final MantenerConocimientoService service;
    private final BuscarConocimientoService buscador;

    public ConocimientoController(MantenerConocimientoService service, BuscarConocimientoService buscador) {
        this.service = service;
        this.buscador = buscador;
    }

    @GetMapping("/buscar")
    public List<BusquedaConocimientoResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Integer hardwareId,
            @RequestParam(required = false) Integer sistemaId,
            @RequestParam(required = false) Integer moduloId,
            @RequestParam(required = false) Integer frecuenciaId) {
        return buscador.buscar(texto, hardwareId, sistemaId, moduloId, frecuenciaId).stream()
                .map(BusquedaConocimientoResponse::from).toList();
    }

    @GetMapping
    public List<ConocimientoResponse> listar() {
        return service.listar().stream().map(ConocimientoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ConocimientoResponse buscar(@PathVariable Integer id) {
        return ConocimientoResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConocimientoResponse crear(@Valid @RequestBody GuardarConocimientoRequest request,
            Authentication authentication) {
        return ConocimientoResponse.from(service.crear(request.titulo(), request.descripcion(),
                request.hardwareId(), request.sistemaId(), request.moduloId(), request.frecuenciaId(),
                request.comentario(), authentication.getName()));
    }

    @PutMapping("/{id}")
    public ConocimientoResponse modificar(@PathVariable Integer id,
            @Valid @RequestBody GuardarConocimientoRequest request, Authentication authentication) {
        return ConocimientoResponse.from(service.modificar(id, request.titulo(), request.descripcion(),
                request.hardwareId(), request.sistemaId(), request.moduloId(), request.frecuenciaId(),
                request.comentario(), authentication.getName()));
    }

    @PatchMapping("/{id}/estado")
    public ConocimientoResponse cambiarEstado(@PathVariable Integer id,
            @Valid @RequestBody CambiarEstadoConocimientoRequest request, Authentication authentication) {
        if (request.estado() == EstadoConocimiento.ELIMINADO) {
            throw new ClasificacionInvalidaException("El estado ELIMINADO solo puede asignarse mediante DELETE");
        }
        if (request.estado() == EstadoConocimiento.PUBLICADO
                && !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))) {
            throw new AccessDeniedException("Solo un administrador puede publicar conocimiento");
        }
        return ConocimientoResponse.from(service.cambiarEstado(id, request.estado(), authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id, Authentication authentication) {
        service.eliminar(id, authentication.getName());
    }
}
