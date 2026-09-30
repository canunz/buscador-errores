package cl.casol.backend.procedimiento.infrastructure.web;

import cl.casol.backend.procedimiento.application.service.MantenerProcedimientoService;
import cl.casol.backend.procedimiento.domain.EstadoProcedimiento;
import cl.casol.backend.procedimiento.domain.exception.EstadoProcedimientoInvalidoException;
import cl.casol.backend.procedimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/procedimientos")
public class ProcedimientoController {
    private final MantenerProcedimientoService service;
    public ProcedimientoController(MantenerProcedimientoService service){this.service=service;}
    @GetMapping public List<ProcedimientoResponse> listar(){return service.listar().stream().map(ProcedimientoResponse::from).toList();}
    @GetMapping("/{id}") public ProcedimientoResponse buscar(@PathVariable Integer id){return ProcedimientoResponse.from(service.buscar(id));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProcedimientoResponse crear(@Valid @RequestBody GuardarProcedimientoRequest r,Authentication a){return ProcedimientoResponse.from(service.crear(r.nombre(),r.descripcion(),a.getName()));}
    @PutMapping("/{id}") public ProcedimientoResponse modificar(@PathVariable Integer id,@Valid @RequestBody GuardarProcedimientoRequest r,Authentication a){return ProcedimientoResponse.from(service.modificar(id,r.nombre(),r.descripcion(),a.getName()));}
    @PatchMapping("/{id}/estado") public ProcedimientoResponse publicar(@PathVariable Integer id,@Valid @RequestBody CambiarEstadoProcedimientoRequest r,Authentication a){
        if(!a.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))) throw new AccessDeniedException("Solo un administrador puede publicar procedimientos");
        if(r.estado()!=EstadoProcedimiento.PUBLICADO) throw new EstadoProcedimientoInvalidoException("El único cambio permitido es BORRADOR a PUBLICADO");
        return ProcedimientoResponse.from(service.publicar(id,a.getName()));
    }
}
