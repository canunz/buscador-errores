package cl.casol.backend.identidad.infrastructure.web;
import cl.casol.backend.identidad.application.service.AdministrarResponsablesService;
import cl.casol.backend.identidad.domain.ResponsableAdministrativo;
import cl.casol.backend.identidad.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
@RestController
@RequestMapping("/api/admin/responsables")
public class AdminResponsableController {
    private final AdministrarResponsablesService service;
    public AdminResponsableController(AdministrarResponsablesService service) { this.service = service; }
    @GetMapping public List<ResponsableAdministrativo> listar(@RequestParam(required=false) Integer departamentoId) { return service.listar(departamentoId); }
    @GetMapping("/{id}") public ResponsableAdministrativo buscar(@PathVariable Integer id) { return service.buscar(id); }
    @PostMapping public ResponseEntity<ResponsableAdministrativo> crear(@Valid @RequestBody CrearResponsableRequest r) {
        var creado = service.crear(r.departamentoId(), r.nombre(), r.cargo(), r.contacto());
        return ResponseEntity.created(URI.create("/api/admin/responsables/" + creado.id())).body(creado);
    }
    @PutMapping("/{id}") public ResponsableAdministrativo editar(@PathVariable Integer id, @Valid @RequestBody EditarResponsableRequest r) {
        return service.editar(id, r.nombre(), r.cargo(), r.contacto(), r.revision());
    }
    @PatchMapping("/{id}/estado") public ResponsableAdministrativo estado(@PathVariable Integer id, @Valid @RequestBody EstadoResponsableRequest r) {
        return service.estado(id, r.activo(), r.revision());
    }
}
