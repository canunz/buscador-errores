package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MantenerMaterialApoyoService;
import cl.casol.backend.conocimiento.application.service.MaterialConocimientoArchivoService;
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
    private final MaterialConocimientoArchivoService archivos;

    public MaterialApoyoController(MantenerMaterialApoyoService service, MaterialConocimientoArchivoService archivos) {
        this.service = service;
        this.archivos = archivos;
    }

    @GetMapping
    public List<MaterialApoyoResponse> listar(@PathVariable Integer conocimientoId) {
        return service.listar(conocimientoId).stream().map(MaterialApoyoResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialApoyoResponse crear(@PathVariable Integer conocimientoId,
            @Valid @RequestBody GuardarMaterialApoyoRequest request) {
        cl.casol.backend.shared.infrastructure.web.ArchivoHttp.validarEnlace(request.url());
        return MaterialApoyoResponse.from(
                service.crear(conocimientoId, request.nombre(), request.tipo(), request.url()));
    }

    @PutMapping("/{materialId}")
    public MaterialApoyoResponse modificar(@PathVariable Integer conocimientoId, @PathVariable Integer materialId,
            @Valid @RequestBody GuardarMaterialApoyoRequest request) {
        cl.casol.backend.shared.infrastructure.web.ArchivoHttp.validarEnlace(request.url());
        return MaterialApoyoResponse.from(
                service.modificar(conocimientoId, materialId, request.nombre(), request.tipo(), request.url()));
    }

    @DeleteMapping("/{materialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer conocimientoId, @PathVariable Integer materialId) {
        archivos.eliminar(conocimientoId, materialId);
    }
}
