package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MaterialConocimientoArchivoService;
import cl.casol.backend.conocimiento.domain.TipoMaterial;
import cl.casol.backend.conocimiento.infrastructure.web.dto.MaterialApoyoResponse;
import cl.casol.backend.shared.infrastructure.web.ArchivoHttp;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controlador REST de material conocimiento archivo. Atiende las rutas que empiezan con /api/conocimientos/{conocimientoId}/materiales.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/materiales")
public class MaterialConocimientoArchivoController {
    private final MaterialConocimientoArchivoService service;
    public MaterialConocimientoArchivoController(MaterialConocimientoArchivoService service) { this.service = service; }
    // POST /api/conocimientos/{conocimientoId}/materiales/archivo: subir.
    @PostMapping(value = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialApoyoResponse subir(@PathVariable Integer conocimientoId, @RequestParam String nombre,
            @RequestParam TipoMaterial tipo, @RequestParam MultipartFile archivo) {
        return MaterialApoyoResponse.from(service.subir(conocimientoId, nombre, tipo, ArchivoHttp.convertir(archivo)));
    }
    // GET /api/conocimientos/{conocimientoId}/materiales/{materialId}/archivo: descargar.
    @GetMapping("/{materialId}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable Integer conocimientoId, @PathVariable Integer materialId) {
        return ArchivoHttp.respuesta(service.descargar(conocimientoId, materialId));
    }
}
