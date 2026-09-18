package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MantenerMaterialApoyoService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.GuardarMaterialApoyoRequest;
import cl.casol.backend.conocimiento.infrastructure.web.dto.MaterialApoyoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/materiales")
public class MaterialApoyoController {
    private final MantenerMaterialApoyoService service;

    public MaterialApoyoController(MantenerMaterialApoyoService service) { this.service = service; }

    @GetMapping
    public List<MaterialApoyoResponse> listar(@PathVariable Integer conocimientoId) {
        return service.listar(conocimientoId).stream().map(MaterialApoyoResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialApoyoResponse crear(@PathVariable Integer conocimientoId,
            @Valid @RequestBody GuardarMaterialApoyoRequest request) {
        return MaterialApoyoResponse.from(
                service.crear(conocimientoId, request.nombre(), request.tipo(), request.url()));
    }

    @PutMapping("/{materialId}")
    public MaterialApoyoResponse modificar(@PathVariable Integer conocimientoId, @PathVariable Integer materialId,
            @Valid @RequestBody GuardarMaterialApoyoRequest request) {
        return MaterialApoyoResponse.from(
                service.modificar(conocimientoId, materialId, request.nombre(), request.tipo(), request.url()));
    }
}
