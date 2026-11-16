package cl.casol.backend.identidad.infrastructure.web;
import cl.casol.backend.identidad.application.service.AdministrarOrganizacionService;
import cl.casol.backend.identidad.infrastructure.web.dto.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
@RestController @RequestMapping("/api/admin/departamentos/{departamentoId}/contactos")
public class AdminDepartamentoContactoController {
    private final AdministrarOrganizacionService service;
    public AdminDepartamentoContactoController(AdministrarOrganizacionService service) { this.service=service; }
    @GetMapping public List<ContactoDepartamentoAdminResponse> listar(@PathVariable Integer departamentoId) {
        boolean activo=service.buscar(departamentoId).activo();
        return service.contactos(departamentoId).stream().map(c->ContactoDepartamentoAdminResponse.from(c,activo)).toList();
    }
    @GetMapping("/{id}") public ContactoDepartamentoAdminResponse buscar(@PathVariable Integer departamentoId,@PathVariable Integer id) {
        return ContactoDepartamentoAdminResponse.from(service.contacto(departamentoId,id),service.buscar(departamentoId).activo());
    }
    @PostMapping public ResponseEntity<ContactoDepartamentoAdminResponse> crear(@PathVariable Integer departamentoId,@Valid @RequestBody CrearContactoDepartamentoRequest r) {
        var c=service.crearContacto(departamentoId,r.tipo(),r.valor(),r.activo()==null||r.activo());
        return ResponseEntity.created(URI.create("/api/admin/departamentos/"+departamentoId+"/contactos/"+c.id()))
            .body(ContactoDepartamentoAdminResponse.from(c,service.buscar(departamentoId).activo()));
    }
    @PutMapping("/{id}") public ContactoDepartamentoAdminResponse editar(@PathVariable Integer departamentoId,@PathVariable Integer id,@Valid @RequestBody EditarContactoDepartamentoRequest r) {
        return ContactoDepartamentoAdminResponse.from(service.editarContacto(departamentoId,id,r.tipo(),r.valor(),r.version()),service.buscar(departamentoId).activo());
    }
    @PatchMapping("/{id}/estado") public ContactoDepartamentoAdminResponse estado(@PathVariable Integer departamentoId,@PathVariable Integer id,@Valid @RequestBody EstadoOrganizacionRequest r) {
        return ContactoDepartamentoAdminResponse.from(service.estadoContacto(departamentoId,id,r.activo(),r.version()),service.buscar(departamentoId).activo());
    }
}
