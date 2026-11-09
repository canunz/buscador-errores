package cl.casol.backend.ejecucion.infrastructure.web;
import cl.casol.backend.ejecucion.application.service.AutomatizarEvidenciaService;
import cl.casol.backend.ejecucion.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.io.IOException;
@RestController @RequestMapping("/api/ejecuciones")
public class AutomatizacionEjecucionController {
    private final AutomatizarEvidenciaService service;
    public AutomatizacionEjecucionController(AutomatizarEvidenciaService service){this.service=service;}
    public record Foto(Long id,EvidenciaFotografica.Categoria categoria,String sha256) { }
    public record Solicitud(SolicitudEvidencia detalle,List<Foto> fotos,boolean simulado) { }
    public record Estado(List<Solicitud> solicitudes,List<EventoAutomatizacion> actividad,boolean disponible,boolean simulado,List<AutomatizarEvidenciaService.Comunicacion> comunicaciones) { }
    public record Simular(@NotBlank @Size(max=120) String remitente,@NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String mensajeId) { }
    public record Decision(@NotNull @PositiveOrZero Long version,@NotNull Boolean aprobar,Boolean definitiva,@Size(max=1000) String motivo,Boolean solicitarNuevamente) { }
    public record Tiempo(@Min(0) @Max(8760) int horas) { }
    public record Aviso(String id,String titulo,String detalle,java.time.Instant fecha,String ruta) { }
    @GetMapping("/avisos-evidencia") public List<Aviso> avisos(Authentication a) {
        return service.avisos(a.getName()).stream().map(s->new Aviso("evidencia-"+s.id(),
            s.estado()==SolicitudEvidencia.Estado.EVIDENCIA_RECIBIDA?"Fotografías listas para revisar":"Solicitud de fotografías escalada",
            (s.contexto().sucursalNombre()==null?s.contexto().responsableEmail():s.contexto().sucursalNombre())+" · ronda "+s.ronda()+(s.mensajeEnviadoId()!=null && s.mensajeEnviadoId().startsWith("SIMULADO-")?" · SIMULACIÓN":""),s.fechaEnvio(),"/ejecuciones/"+s.ejecucionId())).toList();
    }
    @GetMapping("/{id}/automatizacion") public AutomatizarEvidenciaService.LecturaAutomatizacion estado(@PathVariable Integer id,Authentication a){return service.consultarEstado(id,a.getName());}
    public record Enviar(@Positive Integer sucursalId,@NotEmpty @Size(max=20) List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios,@Size(max=200) String asunto,@Size(max=2000) String mensaje){}
    @PostMapping("/{id}/pasos/{paso}/correo") public SolicitudEvidencia enviar(@PathVariable Integer id,@PathVariable Integer paso,@Valid @RequestBody Enviar r,Authentication a){
        var preparada=service.enviarPaso(id,paso,r.sucursalId(),r.destinatarios(),r.asunto(),r.mensaje(),a.getName());
        // La entrega SMTP se intenta después del commit. Leer el resultado registrado, sin reenviar.
        return service.resultadoEnvio(id,preparada.id(),a.getName());
    }
    @PostMapping("/{id}/solicitudes-evidencia/{solicitud}/simular-respuesta")
    public SolicitudEvidencia simular(@PathVariable Integer id,@PathVariable String solicitud,@Valid @RequestBody Simular r,Authentication a){
        return service.recibirSimulada(id,solicitud,r.remitente(),r.mensajeId(),service.muestras(id,solicitud,a.getName()),a.getName());
    }
    @PostMapping(path="/{id}/solicitudes-evidencia/{solicitud}/evidencias-simuladas",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public SolicitudEvidencia fotos(@PathVariable Integer id,@PathVariable String solicitud,@RequestParam String remitente,@RequestParam String mensajeId,
            @RequestPart(required=false) MultipartFile mueble,@RequestPart(required=false) MultipartFile red,@RequestPart(required=false) MultipartFile electricidad,@RequestPart(name="foto_general",required=false) MultipartFile fotoGeneral,Authentication a) throws IOException {
        var archivos=archivos(mueble,red,electricidad,fotoGeneral);
        return service.recibirSimulada(id,solicitud,remitente,mensajeId,archivos,a.getName());
    }
    @PostMapping(path="/{id}/solicitudes-evidencia/{solicitud}/evidencias",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public SolicitudEvidencia recibirFotos(@PathVariable Integer id,@PathVariable String solicitud,@RequestParam String remitente,@RequestParam String mensajeId,
            @RequestPart(required=false) MultipartFile mueble,@RequestPart(required=false) MultipartFile red,@RequestPart(required=false) MultipartFile electricidad,@RequestPart(name="foto_general",required=false) MultipartFile fotoGeneral,Authentication a) throws IOException {
        var archivos=archivos(mueble,red,electricidad,fotoGeneral);
        return service.recibir(id,solicitud,remitente,mensajeId,archivos,a.getName());
    }
    private Map<EvidenciaFotografica.Categoria,byte[]> archivos(MultipartFile... datos) throws IOException {
        var archivos=new EnumMap<EvidenciaFotografica.Categoria,byte[]>(EvidenciaFotografica.Categoria.class);
        var categorias=EvidenciaFotografica.Categoria.values();
        for(int i=0;i<datos.length;i++)if(datos[i]!=null){
            if(datos[i].getSize()>2*1024*1024)throw new cl.casol.backend.ejecucion.domain.exception.ReglaEjecucionException("Máximo 2 MB por fotografía");
            archivos.put(categorias[i],datos[i].getBytes());
        }
        return archivos;
    }
    public record Confirmar(@NotBlank @Size(max=120) String remitente,@NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String mensajeId,@NotBlank @Size(max=1000) String texto){}
    @PostMapping("/{id}/solicitudes-evidencia/{solicitud}/simular-confirmacion") public SolicitudEvidencia confirmar(@PathVariable Integer id,@PathVariable String solicitud,@Valid @RequestBody Confirmar r,Authentication a){return service.confirmarRespuesta(id,solicitud,r.remitente(),r.mensajeId(),r.texto(),a.getName());}
    @PostMapping("/{id}/solicitudes-evidencia/{solicitud}/decision")
    public SolicitudEvidencia decidir(@PathVariable Integer id,@PathVariable String solicitud,@Valid @RequestBody Decision r,Authentication a){return r.solicitarNuevamente()==null?service.decidir(id,solicitud,r.version(),r.aprobar(),Boolean.TRUE.equals(r.definitiva()),r.motivo(),a.getName()):service.decidir(id,solicitud,r.version(),r.aprobar(),Boolean.TRUE.equals(r.definitiva()),r.motivo(),a.getName(),r.solicitarNuevamente());}
    public record Repetir(@NotNull @PositiveOrZero Long version) {}
    @PostMapping("/{id}/solicitudes-evidencia/{solicitud}/nueva-solicitud")
    public SolicitudEvidencia repetir(@PathVariable Integer id,@PathVariable String solicitud,@Valid @RequestBody Repetir r,Authentication a){
        var preparada=service.solicitarNuevamente(id,solicitud,r.version(),a.getName());
        return service.resultadoEnvio(id,preparada.id(),a.getName());
    }
    @PostMapping("/{id}/solicitudes-evidencia/{solicitud}/simular-tiempo")
    public void tiempo(@PathVariable Integer id,@PathVariable String solicitud,@Valid @RequestBody Tiempo r,Authentication a){service.simularTiempo(id,solicitud,r.horas(),a.getName());}
    @GetMapping("/{id}/solicitudes-evidencia/{solicitud}/fotos/{foto}")
    public ResponseEntity<byte[]> descargar(@PathVariable Integer id,@PathVariable String solicitud,@PathVariable Long foto,Authentication a){
        var evidencia=service.descargar(id,solicitud,foto,a.getName());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=\"evidencia-"+foto+".jpg\"").cacheControl(CacheControl.noStore()).body(evidencia.datos());
    }
}
