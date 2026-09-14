package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MantenerConocimientoService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conocimientos")
public class ConocimientoController {
    private final MantenerConocimientoService service;

    public ConocimientoController(MantenerConocimientoService service) { this.service = service; }

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
        return ConocimientoResponse.from(service.cambiarEstado(id, request.estado(), authentication.getName()));
    }
}
