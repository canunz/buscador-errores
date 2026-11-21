package cl.casol.backend.tickets.infrastructure.web;
import cl.casol.backend.tickets.application.service.CrearSolicitudService;
import cl.casol.backend.tickets.infrastructure.web.dto.TicketDetalleResponse;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

/**
 * Controlador REST de solicitud. Atiende las rutas que empiezan con /api/tickets/solicitudes.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/tickets/solicitudes")
public class SolicitudController {
    private final CrearSolicitudService service;
    public SolicitudController(CrearSolicitudService service) {this.service=service;}
    // GET /api/tickets/solicitudes/limites: límites.
    @GetMapping("/limites")
    public Map<String,Long> limites() {return Map.of("cantidad",(long)service.maxFotos(),"bytes",service.maxBytes());}
    // GET /api/tickets/solicitudes/catalogos: catálogos.
    @GetMapping("/catalogos")
    public CrearSolicitudService.Catalogos catalogos(Authentication auth) {return service.catalogos(auth.getName());}
    // POST /api/tickets/solicitudes: crear.
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetalleResponse crear(@RequestParam String titulo,@RequestParam String descripcion,
            @RequestParam(required=false) List<MultipartFile> fotos,
            @jakarta.validation.Valid @ModelAttribute cl.casol.backend.tickets.infrastructure.web.dto.DatosSolicitanteRequest solicitante,
            Authentication auth) throws java.io.IOException {
        if(titulo.isBlank() || titulo.length()>150 || descripcion.isBlank() || descripcion.length()>10000)
            throw new TicketInvalidoException("Completa el título y la descripción dentro de los límites indicados");
        var archivos=fotos==null?List.<MultipartFile>of():fotos;
        if(archivos.size()>service.maxFotos()) throw new TicketInvalidoException("Demasiadas fotografías");
        List<CrearSolicitudService.Foto> datos=new ArrayList<>();
        for(var f:archivos) {
            if(f.getSize()>service.maxBytes()) throw new TicketInvalidoException("La fotografía supera el tamaño permitido");
            datos.add(new CrearSolicitudService.Foto(f.getOriginalFilename(),f.getBytes()));
        }
        return TicketDetalleResponse.from(service.crear(titulo.strip(),descripcion.strip(),datos,solicitante.entrada(),auth.getName()));
    }
}
