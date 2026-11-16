package cl.casol.backend.conocimiento.infrastructure.web;
import cl.casol.backend.conocimiento.application.service.AutoservicioPublicoService;
import cl.casol.backend.conocimiento.domain.TipoMaterial;
import cl.casol.backend.conocimiento.infrastructure.web.dto.MaterialSolucionResponse;
import cl.casol.backend.shared.infrastructure.web.ArchivoHttp;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;
import java.util.List;
@RestController @RequestMapping("/api/admin/soluciones/{id}/materiales")
public class AdminMaterialSolucionController {
 private final AutoservicioPublicoService service;
 public AdminMaterialSolucionController(AutoservicioPublicoService s){service=s;}
 public record Crear(@NotBlank @Size(max=150) String nombre,@NotNull TipoMaterial tipo,@NotBlank @Size(max=500) String url,boolean publicado){}
 public record Publicar(@NotNull Boolean publicado){}
 @GetMapping public List<MaterialSolucionResponse> listar(@PathVariable Integer id){return service.materiales(id,false).stream().map(m->MaterialSolucionResponse.from(m,false)).toList();}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public MaterialSolucionResponse crear(@PathVariable Integer id,@Valid @RequestBody Crear r){return MaterialSolucionResponse.from(service.crear(id,r.nombre(),r.tipo(),r.url(),r.publicado()),false);}
 @PostMapping(path="/archivo",consumes="multipart/form-data") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public MaterialSolucionResponse subir(@PathVariable Integer id,@RequestParam String nombre,@RequestParam TipoMaterial tipo,@RequestParam(defaultValue="false") boolean publicado,@RequestPart MultipartFile archivo){return MaterialSolucionResponse.from(service.subir(id,nombre,tipo,ArchivoHttp.convertir(archivo),publicado),false);}
 @PatchMapping("/{material}/publicacion") public MaterialSolucionResponse publicar(@PathVariable Integer id,@PathVariable Integer material,@Valid @RequestBody Publicar r){return MaterialSolucionResponse.from(service.publicarMaterial(id,material,r.publicado()),false);}
 @DeleteMapping("/{material}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void eliminar(@PathVariable Integer id,@PathVariable Integer material){service.eliminar(id,material);}
 @GetMapping("/{material}/archivo") public org.springframework.http.ResponseEntity<byte[]> archivo(@PathVariable Integer id,@PathVariable Integer material){return ArchivoHttp.respuesta(service.descargar(id,material,false));}
}
