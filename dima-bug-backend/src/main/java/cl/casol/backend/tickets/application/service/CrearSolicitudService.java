package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.*;
import cl.casol.backend.tickets.application.port.out.*;
import cl.casol.backend.tickets.domain.*;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.util.*;
import java.time.LocalDateTime;

/** Atomic ticket + photographs. Files are compensated on transaction rollback. */
@Service
public class CrearSolicitudService {
    public record Foto(String nombre, byte[] contenido) {}
    public record DatosEntrada(String nombreCompleto, Integer sucursalId, Integer departamentoId,
            String contacto, String equipo, String codigoUsuario) {}
    public record Catalogos(List<CatalogoSolicitantePort.Opcion> sucursales,
            List<CatalogoSolicitantePort.Opcion> departamentos) {}
    private final CatalogoSolicitantePort catalogoSolicitante;
    private final CrearTicketUseCase crear;
    private final ContextoTicketService contexto;
    private final AdjuntoRepositoryPort adjuntos;
    private final AlmacenAdjuntosPort almacen;
    private final int maxFotos;
    private final long maxBytes;
    public CrearSolicitudService(CrearTicketUseCase crear, ContextoTicketService contexto,
            AdjuntoRepositoryPort adjuntos, AlmacenAdjuntosPort almacen, CatalogoSolicitantePort catalogoSolicitante,
            @Value("${dimabug.solicitudes.max-fotos:5}") int maxFotos,
            @Value("${dimabug.solicitudes.max-bytes:5242880}") long maxBytes) {
        if(maxFotos<1 || maxFotos>10 || maxBytes<1 || maxBytes>AdjuntoTicket.MAX_BYTES)
            throw new IllegalArgumentException("Límites de fotografías inválidos");
        this.catalogoSolicitante=catalogoSolicitante;
        this.crear=crear; this.contexto=contexto; this.adjuntos=adjuntos; this.almacen=almacen;
        this.maxFotos=maxFotos; this.maxBytes=maxBytes;
    }
    public int maxFotos() { return maxFotos; }
    public long maxBytes() { return maxBytes; }
    @Transactional(readOnly=true)
    public Catalogos catalogos(String email) {
        contexto.actor(email);
        return new Catalogos(catalogoSolicitante.sucursales(), catalogoSolicitante.departamentos());
    }
    @Transactional
    public TicketVista crear(String titulo,String descripcion,List<Foto> fotos,DatosEntrada entrada,String email) {
        contexto.actor(email);
        if (entrada==null) throw new TicketInvalidoException("Completa los datos del solicitante");
        var sucursal=catalogoSolicitante.sucursalActiva(entrada.sucursalId())
                .orElseThrow(()->new TicketInvalidoException("Selecciona una sucursal activa"));
        var departamento=catalogoSolicitante.departamentoActivo(entrada.departamentoId())
                .orElseThrow(()->new TicketInvalidoException("Selecciona un área o departamento activo"));
        var datosSolicitante=new DatosSolicitante(entrada.nombreCompleto(),sucursal.id(),sucursal.nombre(),
                departamento.id(),departamento.nombre(),entrada.contacto(),entrada.equipo(),entrada.codigoUsuario());
        if(fotos==null || fotos.size()>maxFotos) throw new TicketInvalidoException("Demasiadas fotografías");
        for(Foto f:fotos) validar(f.contenido());
        if(!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Se requiere una transacción supervisada");
        List<String> referencias=new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status==STATUS_ROLLED_BACK) for(String ref:referencias) almacen.eliminar(ref);
                if(status==STATUS_UNKNOWN) org.slf4j.LoggerFactory.getLogger(CrearSolicitudService.class)
                    .error("Resultado transaccional desconocido; revisar consistencia de fotografías antes de cualquier limpieza");
            }
        });
        TicketVista vista=crear.crear(new CrearTicketUseCase.Comando(titulo,descripcion,PrioridadTicket.MEDIA,null,null,null,datosSolicitante),email);
        ActorTicket actor=contexto.actor(email);
        for(Foto f:fotos) {
            String ref=almacen.guardar(f.contenido()); referencias.add(ref);
            adjuntos.guardar(AdjuntoTicket.nuevo(vista.ticket().getId(),null,f.nombre(),f.contenido(),ref,actor.id(),LocalDateTime.now()));
        }
        return contexto.detalle(vista.ticket(),actor);
    }
    void validar(byte[] b) {
        if(b==null || b.length==0 || b.length>maxBytes) throw new TicketInvalidoException("Fotografía vacía o demasiado grande");
        String mime=FormatoAdjunto.mime(b);
        if(!Set.of("image/png","image/jpeg","image/webp").contains(mime))
            throw new TicketInvalidoException("Utiliza fotografías JPG, PNG o WEBP");
        try {
            try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(b))) {
                var readers=javax.imageio.ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw new IllegalArgumentException();
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    dimensiones(reader.getWidth(0),reader.getHeight(0));
                    if(reader.read(0)==null) throw new IllegalArgumentException();
                } finally {reader.dispose();}
            }
        } catch(Exception ex) { throw new TicketInvalidoException("La fotografía está dañada o su formato no es válido"); }
    }
    private static void dimensiones(int w,int h) {if(w<1 || h<1 || (long)w*h>20_000_000) throw new IllegalArgumentException();}
}
