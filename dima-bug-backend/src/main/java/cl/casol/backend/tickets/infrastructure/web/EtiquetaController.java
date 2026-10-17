package cl.casol.backend.tickets.infrastructure.web;

import cl.casol.backend.tickets.application.port.in.GestionarEtiquetasUseCase;
import cl.casol.backend.tickets.infrastructure.web.dto.CambiarEstadoEtiquetaRequest;
import cl.casol.backend.tickets.infrastructure.web.dto.EtiquetaResponse;
import cl.casol.backend.tickets.infrastructure.web.dto.GuardarEtiquetaRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/etiquetas")
public class EtiquetaController {
    private final GestionarEtiquetasUseCase etiquetas;

    public EtiquetaController(GestionarEtiquetasUseCase etiquetas) {
        this.etiquetas = etiquetas;
    }

    /** Activas para todos. El administrador puede pedir también las inactivas con incluirInactivas=true. */
    @GetMapping
    public List<EtiquetaResponse> listar(@RequestParam(defaultValue = "false") boolean incluirInactivas,
            Authentication authentication) {
        return etiquetas.listar(incluirInactivas, authentication.getName()).stream()
                .map(EtiquetaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EtiquetaResponse crear(@Valid @RequestBody GuardarEtiquetaRequest request, Authentication authentication) {
        return EtiquetaResponse.from(etiquetas.crear(request.nombre(), request.color(), request.nivel(), authentication.getName()));
    }

    @PutMapping("/{id}")
    public EtiquetaResponse editar(@PathVariable Long id, @Valid @RequestBody GuardarEtiquetaRequest request,
            Authentication authentication) {
        return EtiquetaResponse.from(etiquetas.editar(id, request.nombre(), request.color(), request.nivel(), authentication.getName()));
    }

    @PatchMapping("/{id}/estado")
    public EtiquetaResponse cambiarEstado(@PathVariable Long id,
            @Valid @RequestBody CambiarEstadoEtiquetaRequest request, Authentication authentication) {
        return EtiquetaResponse.from(etiquetas.cambiarEstado(id, request.activa(), authentication.getName()));
    }
}
