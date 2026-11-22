package cl.casol.backend.catalogo.infrastructure.web;
import cl.casol.backend.catalogo.application.service.AdministrarCatalogosService;
import cl.casol.backend.catalogo.domain.TipoCatalogo;
import cl.casol.backend.catalogo.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/**
 * Controlador REST de admin catálogo. Atiende las rutas que empiezan con /api/admin/catalogos/{tipo}.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/admin/catalogos/{tipo}")
public class AdminCatalogoController {
    private final AdministrarCatalogosService service;
    public AdminCatalogoController(AdministrarCatalogosService service) { this.service=service; }
    // GET /api/admin/catalogos/{tipo}: listar.
    @GetMapping public List<CatalogoAdministrativoResponse> listar(@PathVariable TipoCatalogo tipo) {
        return service.listar(tipo).stream().map(CatalogoAdministrativoResponse::from).toList();
    }
    // GET /api/admin/catalogos/{tipo}/{id}: buscar.
    @GetMapping("/{id}") public CatalogoAdministrativoResponse buscar(@PathVariable TipoCatalogo tipo,@PathVariable Integer id) {
        return CatalogoAdministrativoResponse.from(service.buscar(tipo,id));
    }
    // POST /api/admin/catalogos/{tipo}: crear.
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CatalogoAdministrativoResponse crear(@PathVariable TipoCatalogo tipo,@Valid @RequestBody GuardarCatalogoRequest r) {
        return CatalogoAdministrativoResponse.from(service.guardar(tipo,null,r.toDomain()));
    }
    // PUT /api/admin/catalogos/{tipo}/{id}: modificar.
    @PutMapping("/{id}") public CatalogoAdministrativoResponse modificar(@PathVariable TipoCatalogo tipo,@PathVariable Integer id,@Valid @RequestBody GuardarCatalogoRequest r) {
        return CatalogoAdministrativoResponse.from(service.guardar(tipo,id,r.toDomain()));
    }
    // PATCH /api/admin/catalogos/{tipo}/{id}/estado: estado.
    @PatchMapping("/{id}/estado") public CatalogoAdministrativoResponse estado(@PathVariable TipoCatalogo tipo,@PathVariable Integer id,@Valid @RequestBody CambiarEstadoCatalogoRequest r) {
        return CatalogoAdministrativoResponse.from(service.cambiarEstado(tipo,id,r.activo()));
    }
}
