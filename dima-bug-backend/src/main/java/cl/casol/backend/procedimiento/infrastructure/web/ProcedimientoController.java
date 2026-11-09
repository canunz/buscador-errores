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
    private final cl.casol.backend.procedimiento.application.service.EditarProcedimientoAdminService editar;
    private final cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria;
    public ProcedimientoController(MantenerProcedimientoService service,cl.casol.backend.procedimiento.application.service.EditarProcedimientoAdminService editar,cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria){this.service=service;this.editar=editar;this.auditoria=auditoria;}
    @PostMapping("/{id}/edicion") public ProcedimientoResponse preparar(@PathVariable Integer id,Authentication a){return ProcedimientoResponse.from(editar.preparar(id,a.getName()));}
    @GetMapping("/{id}/historial") public java.util.List<cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService.Evento> historial(@PathVariable Integer id,Authentication a){return auditoria.listar(id,a.getName());}
    @GetMapping public List<ProcedimientoResponse> listar(Authentication a){return service.listarVisibles(a.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))).stream().map(ProcedimientoResponse::from).toList();}
    @GetMapping("/{id}") public ProcedimientoResponse buscar(@PathVariable Integer id){return ProcedimientoResponse.from(service.buscar(id));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProcedimientoResponse crear(@Valid @RequestBody GuardarProcedimientoRequest r,Authentication a){return ProcedimientoResponse.from(r.hardwareProporcionado()?editar.guardar(null,r.nombre(),r.descripcion(),r.sistemaId(),r.moduloId(),r.informacionComplementaria(),r.estado(),a.getName(),r.hardwareId()):editar.guardar(null,r.nombre(),r.descripcion(),r.sistemaId(),r.moduloId(),r.informacionComplementaria(),r.estado(),a.getName()));}
    @PutMapping("/{id}") public ProcedimientoResponse modificar(@PathVariable Integer id,@Valid @RequestBody GuardarProcedimientoRequest r,Authentication a){
        boolean legado=r.estado()==null && r.sistemaId()==null && r.moduloId()==null && r.informacionComplementaria()==null;
        var d=legado?service.buscar(id).datos():null;
        var p=r.hardwareProporcionado()?editar.guardar(id,r.nombre(),r.descripcion(),legado?d.sistemaId():r.sistemaId(),legado?d.moduloId():r.moduloId(),legado?d.informacionComplementaria():r.informacionComplementaria(),r.estado(),a.getName(),r.hardwareId()):editar.guardar(id,r.nombre(),r.descripcion(),legado?d.sistemaId():r.sistemaId(),legado?d.moduloId():r.moduloId(),legado?d.informacionComplementaria():r.informacionComplementaria(),r.estado(),a.getName());
        return ProcedimientoResponse.from(p);
    }
    @PatchMapping("/{id}/estado") public ProcedimientoResponse publicar(@PathVariable Integer id,@Valid @RequestBody CambiarEstadoProcedimientoRequest r,Authentication a){
        if(!a.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))) throw new AccessDeniedException("Solo un administrador puede publicar procedimientos");
        return ProcedimientoResponse.from(r.estado()==EstadoProcedimiento.PUBLICADO?editar.publicar(id,a.getName()):editar.preparar(id,a.getName()));
    }
}
