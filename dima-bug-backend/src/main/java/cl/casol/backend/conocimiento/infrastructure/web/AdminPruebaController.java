package cl.casol.backend.conocimiento.infrastructure.web;
import cl.casol.backend.conocimiento.application.service.AdministrarPruebasService;
import cl.casol.backend.conocimiento.domain.Prueba;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
@RestController @RequestMapping("/api/admin/pruebas")
public class AdminPruebaController {
 private final AdministrarPruebasService service;
 public AdminPruebaController(AdministrarPruebasService service){this.service=service;}
 @GetMapping public List<Prueba> listar(){return service.listar();}
 @GetMapping("/{id}") public Prueba buscar(@PathVariable Integer id){return service.buscar(id);}
 @PostMapping public ResponseEntity<Prueba> crear(@Valid @RequestBody GuardarPruebaAdministrativaRequest r){var p=service.crear(r.descripcion(),r.resultadoEsperado());return ResponseEntity.created(URI.create("/api/admin/pruebas/"+p.id())).body(p);}
 @PutMapping("/{id}") public Prueba editar(@PathVariable Integer id,@Valid @RequestBody GuardarPruebaAdministrativaRequest r){return service.editar(id,r.descripcion(),r.resultadoEsperado());}
 @PatchMapping("/{id}/estado") public Prueba estado(@PathVariable Integer id,@Valid @RequestBody EstadoPruebaRequest r){return service.estado(id,r.activa());}
}