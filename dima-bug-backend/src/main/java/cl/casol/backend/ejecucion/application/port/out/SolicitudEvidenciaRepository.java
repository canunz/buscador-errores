package cl.casol.backend.ejecucion.application.port.out;
import cl.casol.backend.ejecucion.domain.*;
import java.time.Instant;
import java.util.*;
public interface SolicitudEvidenciaRepository {
    Optional<SolicitudEvidencia> buscar(String id);
    default Optional<SolicitudEvidencia> buscarActualizada(String id){return buscar(id);}
    List<SolicitudEvidencia> porEjecucion(Integer id);
    Optional<SolicitudEvidencia> porInicio(Integer actor,String clave);
    List<String> vencidas(Instant ahora);
    default List<String> vencidas(Instant ahora,boolean excluirSimuladas){return vencidas(ahora);}
    List<SolicitudEvidencia> avisos(Integer actorId);
    SolicitudEvidencia guardar(SolicitudEvidencia solicitud);
    EvidenciaFotografica guardar(EvidenciaFotografica evidencia);
    List<EvidenciaFotografica> evidencias(String solicitudId);
    default List<FotoEvidenciaResumen> fotosResumen(String solicitudId){return evidencias(solicitudId).stream().map(f->new FotoEvidenciaResumen(f.id(),f.categoria(),f.sha256())).toList();}
    Optional<EvidenciaFotografica> evidencia(Long id);
    void registrar(EventoAutomatizacion evento);
    List<EventoAutomatizacion> eventos(Integer ejecucionId);
    /** Serializa por actor y comprueba la cuota durable de intentos SMTP. Requiere transacción. */
    boolean cuotaCorreoDisponible(Integer actorId,Instant desde,int maximo);
}
