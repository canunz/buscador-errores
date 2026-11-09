package cl.casol.backend.identidad.infrastructure.web;

import cl.casol.backend.identidad.application.service.AdministrarSucursalesService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/sucursales")
public class SucursalController {
    private final AdministrarSucursalesService service;
    public SucursalController(AdministrarSucursalesService service) { this.service=service; }
    public record Disponible(Integer id,String codigo,String nombre,String responsableNombre,String ciudad) { }
    @GetMapping public List<Disponible> listar() {
        return service.disponibles().stream().map(s->new Disponible(s.id(),s.codigo(),s.nombre(),s.responsableNombre(),s.ciudad())).toList();
    }
    public record Destinatario(Integer id,String nombre,String responsableNombre,String responsableEmail,boolean activa,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios,String ciudad,String responsableCargo) { }
    @GetMapping("/{id}/destinatario") public Destinatario destinatario(@PathVariable Integer id) {
        var s=service.buscar(id);return new Destinatario(s.id(),s.nombre(),s.responsableNombre(),s.responsableEmail(),s.activa(),s.contactos(),s.ciudad(),s.responsableCargo());
    }
}
