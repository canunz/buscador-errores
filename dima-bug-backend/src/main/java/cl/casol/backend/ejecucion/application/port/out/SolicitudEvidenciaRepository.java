package cl.casol.backend.ejecucion.application.port.out;
import cl.casol.backend.ejecucion.domain.*;
import java.time.Instant;
import java.util.*;
/**
 * Repositorio Spring Data de solicitud evidencia.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SolicitudEvidenciaRepository {
    // Busca por id.
    Optional<SolicitudEvidencia> buscar(String id);
    // Busca actualizada.
    default Optional<SolicitudEvidencia> buscarActualizada(String id){return buscar(id);}
    // Devuelve por ejecución.
    List<SolicitudEvidencia> porEjecucion(Integer id);
    // Devuelve por inicio.
    Optional<SolicitudEvidencia> porInicio(Integer actor,String clave);
    // Devuelve vencidas.
    List<String> vencidas(Instant ahora);
    // Devuelve vencidas.
    default List<String> vencidas(Instant ahora,boolean excluirSimuladas){return vencidas(ahora);}
    // Devuelve avisos.
    List<SolicitudEvidencia> avisos(Integer actorId);
    // Guarda la solicitud.
    SolicitudEvidencia guardar(SolicitudEvidencia solicitud);
    // Guarda la evidencia.
    EvidenciaFotografica guardar(EvidenciaFotografica evidencia);
    // Devuelve evidencias.
    List<EvidenciaFotografica> evidencias(String solicitudId);
    // Devuelve fotos resumen.
    default List<FotoEvidenciaResumen> fotosResumen(String solicitudId){return evidencias(solicitudId).stream().map(f->new FotoEvidenciaResumen(f.id(),f.categoria(),f.sha256())).toList();}
    // Devuelve evidencia.
    Optional<EvidenciaFotografica> evidencia(Long id);
    // Registra el evento.
    void registrar(EventoAutomatizacion evento);
    // Devuelve eventos.
    List<EventoAutomatizacion> eventos(Integer ejecucionId);
    /** Serializa por actor y comprueba la cuota durable de intentos SMTP. Requiere transacción. */
    boolean cuotaCorreoDisponible(Integer actorId,Instant desde,int maximo);
}
