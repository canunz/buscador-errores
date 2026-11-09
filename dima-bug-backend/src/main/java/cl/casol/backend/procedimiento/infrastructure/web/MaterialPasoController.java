package cl.casol.backend.procedimiento.infrastructure.web;

import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import cl.casol.backend.procedimiento.application.service.MantenerMaterialPasoService;
import cl.casol.backend.procedimiento.application.service.MaterialPasoArchivoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales")
public class MaterialPasoController {
    private final MantenerMaterialPasoService service;
    private final MaterialPasoArchivoService archivos;
    private final cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria;
    public MaterialPasoController(MantenerMaterialPasoService service, MaterialPasoArchivoService archivos,cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria) {
        this.auditoria=auditoria;this.service = service;
        this.archivos = archivos;
    }
    @GetMapping public List<MaterialApoyoResponse> listar(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId){return service.listar(procedimientoId,pasoId).stream().map(m -> MaterialApoyoResponse.fromPaso(m,procedimientoId,pasoId)).toList();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @org.springframework.transaction.annotation.Transactional
    public MaterialApoyoResponse crear(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId,@Valid @RequestBody GuardarMaterialApoyoRequest r,org.springframework.security.core.Authentication a){cl.casol.backend.shared.infrastructure.web.ArchivoHttp.validarEnlace(r.url());var m=service.crear(procedimientoId,pasoId,r.nombre(),r.tipo(),r.url());auditoria.registrar(procedimientoId,"MATERIAL_CREADO",a.getName(),null,m);return MaterialApoyoResponse.fromPaso(m,procedimientoId,pasoId);}
    @PutMapping("/{materialId}") @org.springframework.transaction.annotation.Transactional
    public MaterialApoyoResponse modificar(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId,@PathVariable Integer materialId,@Valid @RequestBody GuardarMaterialApoyoRequest r,org.springframework.security.core.Authentication a){cl.casol.backend.shared.infrastructure.web.ArchivoHttp.validarEnlace(r.url());var antes=service.listar(procedimientoId,pasoId).stream().filter(m->m.id().equals(materialId)).findFirst().orElse(null);var m=service.modificar(procedimientoId,pasoId,materialId,r.nombre(),r.tipo(),r.url());auditoria.registrar(procedimientoId,"MATERIAL_MODIFICADO",a.getName(),antes,m);return MaterialApoyoResponse.fromPaso(m,procedimientoId,pasoId);}
    @DeleteMapping("/{materialId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId,@PathVariable Integer materialId){archivos.eliminar(procedimientoId,pasoId,materialId);}
}
