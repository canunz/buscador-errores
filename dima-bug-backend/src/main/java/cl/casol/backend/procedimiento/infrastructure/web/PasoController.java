package cl.casol.backend.procedimiento.infrastructure.web;

import cl.casol.backend.procedimiento.application.service.MantenerPasoService;
import cl.casol.backend.procedimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/procedimientos/{procedimientoId}/pasos")
public class PasoController {
    private final MantenerPasoService service;
    public PasoController(MantenerPasoService service){this.service=service;}
    @GetMapping public List<PasoResponse> listar(@PathVariable Integer procedimientoId){return service.listar(procedimientoId).stream().map(PasoResponse::from).toList();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PasoResponse crear(@PathVariable Integer procedimientoId,@Valid @RequestBody GuardarPasoRequest r){return PasoResponse.from(service.crear(procedimientoId,r.orden(),r.instruccion(),r.esCritico()));}
    @PutMapping("/{pasoId}") public PasoResponse modificar(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId,@Valid @RequestBody GuardarPasoRequest r){return PasoResponse.from(service.modificar(procedimientoId,pasoId,r.orden(),r.instruccion(),r.esCritico()));}
}
