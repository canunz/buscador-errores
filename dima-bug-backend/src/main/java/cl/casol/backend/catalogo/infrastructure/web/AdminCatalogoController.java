package cl.casol.backend.catalogo.infrastructure.web;
import cl.casol.backend.catalogo.application.service.AdministrarCatalogosService;
import cl.casol.backend.catalogo.domain.TipoCatalogo;
import cl.casol.backend.catalogo.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/admin/catalogos/{tipo}")
public class AdminCatalogoController {
    private final AdministrarCatalogosService service;
    public AdminCatalogoController(AdministrarCatalogosService service) { this.service=service; }
    @GetMapping public List<CatalogoAdministrativoResponse> listar(@PathVariable TipoCatalogo tipo) {
        return service.listar(tipo).stream().map(CatalogoAdministrativoResponse::from).toList();
    }
    @GetMapping("/{id}") public CatalogoAdministrativoResponse buscar(@PathVariable TipoCatalogo tipo,@PathVariable Integer id) {
        return CatalogoAdministrativoResponse.from(service.buscar(tipo,id));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CatalogoAdministrativoResponse crear(@PathVariable TipoCatalogo tipo,@Valid @RequestBody GuardarCatalogoRequest r) {
        return CatalogoAdministrativoResponse.from(service.guardar(tipo,null,r.toDomain()));
    }
    @PutMapping("/{id}") public CatalogoAdministrativoResponse modificar(@PathVariable TipoCatalogo tipo,@PathVariable Integer id,@Valid @RequestBody GuardarCatalogoRequest r) {
        return CatalogoAdministrativoResponse.from(service.guardar(tipo,id,r.toDomain()));
    }
    @PatchMapping("/{id}/estado") public CatalogoAdministrativoResponse estado(@PathVariable TipoCatalogo tipo,@PathVariable Integer id,@Valid @RequestBody CambiarEstadoCatalogoRequest r) {
        return CatalogoAdministrativoResponse.from(service.cambiarEstado(tipo,id,r.activo()));
    }
}
