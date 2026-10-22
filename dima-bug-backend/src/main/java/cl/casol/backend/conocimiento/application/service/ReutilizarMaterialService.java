package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.shared.application.archivo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

@Service
public class ReutilizarMaterialService {
    private final ConocimientoRepository conocimientos;
    private final MaterialApoyoRepository materiales;
    private final ArchivoMaterialService archivos;
    private final MantenerMaterialApoyoService mantener;
    public ReutilizarMaterialService(ConocimientoRepository conocimientos,MaterialApoyoRepository materiales,
            ArchivoMaterialService archivos,MantenerMaterialApoyoService mantener){
        this.conocimientos=conocimientos;this.materiales=materiales;this.archivos=archivos;this.mantener=mantener;
    }
    @Transactional
    public MaterialApoyo reutilizar(Integer origenId,Integer materialId,Integer destinoId){
        if(Objects.equals(origenId,destinoId))throw new ClasificacionInvalidaException("Origen y destino deben ser diferentes");
        conocimientos.bloquear(Math.min(origenId,destinoId));conocimientos.bloquear(Math.max(origenId,destinoId));
        var origen=conocimientos.buscarPorId(origenId).orElseThrow(()->new ConocimientoNoEncontradoException(origenId));
        conocimientos.buscarPorId(destinoId).orElseThrow(()->new ConocimientoNoEncontradoException(destinoId));
        if(origen.getEstado()!=EstadoConocimiento.PUBLICADO)throw new ClasificacionInvalidaException("Solo se reutilizan materiales de conocimientos PUBLICADO");
        var original=materiales.buscarPorId(materialId).filter(m->Objects.equals(m.conocimientoId(),origenId)&&m.pasoId()==null)
                .orElseThrow(()->new MaterialApoyoNoEncontradoException(materialId));
        var actuales=materiales.buscarDirectosPorConocimiento(destinoId);
        if(local(original)){
            var descarga=archivos.descargar(original);
            byte[] huella=huella(descarga.contenido());
            for(var m:actuales)if(local(m)&&MessageDigest.isEqual(huella,huella(archivos.descargar(m).contenido())))throw new MaterialDuplicadoException();
            return archivos.crear(original.nombre(),original.tipo(),new ArchivoSubido(descarga.mime(),descarga.contenido()),
                    ref->mantener.crear(destinoId,original.nombre(),original.tipo(),ref));
        }
        URI enlace=enlace(original.url());
        if(actuales.stream().filter(m->!local(m)).anyMatch(m->enlace.equals(enlace(m.url()))))throw new MaterialDuplicadoException();
        return mantener.crear(destinoId,original.nombre(),original.tipo(),original.url());
    }
    private boolean local(MaterialApoyo m){return m.url()!=null&&m.url().startsWith(FormatoArchivo.PREFIJO);}
    private URI enlace(String valor){
        try{
            URI u=URI.create(valor.strip()).normalize();
            String path=u.getPath()==null?"":u.getPath();
            if(!u.isAbsolute()||u.getHost()==null||u.getUserInfo()!=null
                    ||!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))
                    ||path.matches(".*/api/(conocimientos|procedimientos)/.*"))throw new IllegalArgumentException();
            return u;
        }catch(IllegalArgumentException|NullPointerException ex){throw new ClasificacionInvalidaException("Solo se reutilizan enlaces HTTP(S) externos o archivos administrados; no referencias a descargas privadas");}
    }
    private byte[] huella(byte[] contenido){try{return MessageDigest.getInstance("SHA-256").digest(contenido);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
