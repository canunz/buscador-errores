package cl.casol.backend.identidad.infrastructure.web;

import cl.casol.backend.identidad.application.service.AdministrarSucursalesService;
import cl.casol.backend.identidad.domain.Sucursal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/admin/sucursales")
public class AdminSucursalController {
    private final AdministrarSucursalesService service;
    public AdminSucursalController(AdministrarSucursalesService service) { this.service=service; }
    public record Guardar(@NotBlank @Size(max=30) String codigo,@NotBlank @Size(max=100) String nombre,
            @NotBlank @Size(max=100) String responsableNombre,@NotBlank @Size(max=120) String responsableEmail,
            @PositiveOrZero Long version,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios,@Size(max=100) String ciudad,@Size(max=100) String responsableCargo) { }
    public record Estado(@NotNull Boolean activa,@NotNull @PositiveOrZero Long version) { }
    @GetMapping public List<Sucursal> listar() { return service.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Sucursal crear(@Valid @RequestBody Guardar r) { if(r.ciudad()!=null || r.responsableCargo()!=null)return service.crearConUbicacion(r.codigo(),r.nombre(),r.responsableNombre(),r.responsableEmail(),r.destinatarios(),r.ciudad(),r.responsableCargo());return r.destinatarios()==null?service.crear(r.codigo(),r.nombre(),r.responsableNombre(),r.responsableEmail()):service.crear(r.codigo(),r.nombre(),r.responsableNombre(),r.responsableEmail(),r.destinatarios()); }
    @PutMapping("/{id}") public Sucursal editar(@PathVariable Integer id,@Valid @RequestBody Guardar r) {
        if(r.ciudad()!=null || r.responsableCargo()!=null)return service.editarConUbicacion(id,r.codigo(),r.nombre(),r.responsableNombre(),r.responsableEmail(),r.version(),r.destinatarios(),r.ciudad(),r.responsableCargo());
        return r.destinatarios()==null?service.editar(id,r.codigo(),r.nombre(),r.responsableNombre(),r.responsableEmail(),r.version()):service.editar(id,r.codigo(),r.nombre(),r.responsableNombre(),r.responsableEmail(),r.version(),r.destinatarios());
    }
    @PatchMapping("/{id}/estado") public Sucursal estado(@PathVariable Integer id,@Valid @RequestBody Estado r) { return service.estado(id,r.activa(),r.version()); }
    public record Ubicacion(@Size(max=100) String ciudad,@Size(max=100) String responsableCargo,@NotNull @PositiveOrZero Long version){}
    @PutMapping("/{id}/ubicacion") public Sucursal ubicacion(@PathVariable Integer id,@Valid @RequestBody Ubicacion r){return service.ubicacion(id,r.ciudad(),r.responsableCargo(),r.version());}
    public record Contactos(@NotNull java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios,@NotNull @PositiveOrZero Long version){}
    @PutMapping("/{id}/destinatarios") public Sucursal destinatarios(@PathVariable Integer id,@Valid @RequestBody Contactos r){return service.destinatarios(id,r.destinatarios(),r.version());}
}
