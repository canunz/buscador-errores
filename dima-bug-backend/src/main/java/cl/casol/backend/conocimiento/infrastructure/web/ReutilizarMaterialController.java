package cl.casol.backend.conocimiento.infrastructure.web;
import cl.casol.backend.conocimiento.application.service.ReutilizarMaterialService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/materiales/reutilizaciones")
public class ReutilizarMaterialController {
    private final ReutilizarMaterialService service;
    public ReutilizarMaterialController(ReutilizarMaterialService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MaterialApoyoResponse reutilizar(@PathVariable Integer conocimientoId,@Valid @RequestBody ReutilizarMaterialRequest r){
        return MaterialApoyoResponse.from(service.reutilizar(r.conocimientoOrigenId(),r.materialOrigenId(),conocimientoId));
    }
}
