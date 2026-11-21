package cl.casol.backend.conocimiento.infrastructure.web;
import cl.casol.backend.conocimiento.application.service.ReutilizarMaterialService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
/**
 * Controlador REST de reutilizar material. Atiende las rutas que empiezan con /api/conocimientos/{conocimientoId}/materiales/reutilizaciones.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/materiales/reutilizaciones")
public class ReutilizarMaterialController {
    private final ReutilizarMaterialService service;
    public ReutilizarMaterialController(ReutilizarMaterialService service){this.service=service;}
    // POST /api/conocimientos/{conocimientoId}/materiales/reutilizaciones: reutilizar.
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MaterialApoyoResponse reutilizar(@PathVariable Integer conocimientoId,@Valid @RequestBody ReutilizarMaterialRequest r){
        return MaterialApoyoResponse.from(service.reutilizar(r.conocimientoOrigenId(),r.materialOrigenId(),conocimientoId));
    }
}
