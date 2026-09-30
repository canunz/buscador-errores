package cl.casol.backend.procedimiento.infrastructure.web;

import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import cl.casol.backend.procedimiento.application.service.MantenerMaterialPasoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales")
public class MaterialPasoController {
    private final MantenerMaterialPasoService service;
    public MaterialPasoController(MantenerMaterialPasoService service){this.service=service;}
    @GetMapping public List<MaterialApoyoResponse> listar(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId){return service.listar(procedimientoId,pasoId).stream().map(MaterialApoyoResponse::from).toList();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MaterialApoyoResponse crear(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId,@Valid @RequestBody GuardarMaterialApoyoRequest r){return MaterialApoyoResponse.from(service.crear(procedimientoId,pasoId,r.nombre(),r.tipo(),r.url()));}
    @PutMapping("/{materialId}") public MaterialApoyoResponse modificar(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId,@PathVariable Integer materialId,@Valid @RequestBody GuardarMaterialApoyoRequest r){return MaterialApoyoResponse.from(service.modificar(procedimientoId,pasoId,materialId,r.nombre(),r.tipo(),r.url()));}
}
