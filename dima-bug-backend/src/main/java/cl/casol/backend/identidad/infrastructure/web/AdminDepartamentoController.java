package cl.casol.backend.identidad.infrastructure.web;
import cl.casol.backend.identidad.application.service.AdministrarOrganizacionService;
import cl.casol.backend.identidad.domain.DepartamentoAdministrativo;
import cl.casol.backend.identidad.infrastructure.web.dto.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
/**
 * Controlador REST de admin departamento. Atiende las rutas que empiezan con /api/admin/departamentos.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController @RequestMapping("/api/admin/departamentos")
public class AdminDepartamentoController {
    private final AdministrarOrganizacionService service;
    public AdminDepartamentoController(AdministrarOrganizacionService service) { this.service=service; }
    // GET /api/admin/departamentos: listar.
    @GetMapping public List<DepartamentoAdministrativo> listar() { return service.listar(); }
    @GetMapping("/{id}") public DepartamentoAdministrativo buscar(@PathVariable Integer id) { return service.buscar(id); }
    @PostMapping public ResponseEntity<DepartamentoAdministrativo> crear(@Valid @RequestBody CrearDepartamentoRequest r) {
        var d=service.crear(r.nombre()); return ResponseEntity.created(URI.create("/api/admin/departamentos/"+d.id())).body(d);
    }
    // PUT /api/admin/departamentos/{id}: editar.
    @PutMapping("/{id}") public DepartamentoAdministrativo editar(@PathVariable Integer id,@Valid @RequestBody EditarDepartamentoRequest r) {
        return service.editar(id,r.nombre(),r.version());
    }
    // PATCH /api/admin/departamentos/{id}/estado: estado.
    @PatchMapping("/{id}/estado") public DepartamentoAdministrativo estado(@PathVariable Integer id,@Valid @RequestBody EstadoOrganizacionRequest r) {
        return service.estado(id,r.activo(),r.version());
    }
}
