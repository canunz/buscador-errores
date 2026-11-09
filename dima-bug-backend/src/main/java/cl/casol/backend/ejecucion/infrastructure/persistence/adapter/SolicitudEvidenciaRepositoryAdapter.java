package cl.casol.backend.ejecucion.infrastructure.persistence.adapter;
import cl.casol.backend.ejecucion.application.port.out.SolicitudEvidenciaRepository;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.infrastructure.persistence.entity.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
@Repository
public class SolicitudEvidenciaRepositoryAdapter implements SolicitudEvidenciaRepository {
    @PersistenceContext private EntityManager em;
    public Optional<SolicitudEvidencia> buscar(String id) { return Optional.ofNullable(em.find(SolicitudEvidenciaEntity.class,id)).map(SolicitudEvidenciaEntity::toDomain); }
    public Optional<SolicitudEvidencia> buscarActualizada(String id){
        var entity=em.find(SolicitudEvidenciaEntity.class,id);if(entity==null)return Optional.empty();
        em.refresh(entity);return Optional.of(entity.toDomain());
    }
    public List<SolicitudEvidencia> porEjecucion(Integer id) { return em.createQuery("select s from SolicitudEvidenciaEntity s where s.ejecucionId=:id order by s.ejecucionPasoId,s.ronda",SolicitudEvidenciaEntity.class).setParameter("id",id).getResultList().stream().map(SolicitudEvidenciaEntity::toDomain).toList(); }
    public Optional<SolicitudEvidencia> porInicio(Integer actor,String clave) { return em.createQuery("select s from SolicitudEvidenciaEntity s where s.actorId=:actor and s.claveInicio=:clave",SolicitudEvidenciaEntity.class).setParameter("actor",actor).setParameter("clave",clave).getResultStream().findFirst().map(SolicitudEvidenciaEntity::toDomain); }
    public List<String> vencidas(Instant ahora){return vencidas(ahora,false);}
    public List<String> vencidas(Instant ahora,boolean excluirSimuladas) { return em.createQuery("select s.id from SolicitudEvidenciaEntity s where s.estado in :estado and s.proximoSeguimiento<=:ahora and (:excluir=false or s.mensajeEnviadoId is null or s.mensajeEnviadoId not like 'SIMULADO-%') order by s.proximoSeguimiento",String.class).setParameter("estado",List.of(SolicitudEvidencia.Estado.ESPERANDO_EVIDENCIA,SolicitudEvidencia.Estado.ESPERANDO_CONFIRMACION)).setParameter("ahora",ahora).setParameter("excluir",excluirSimuladas).setMaxResults(50).getResultList(); }
    public List<SolicitudEvidencia> avisos(Integer actorId) {
        return em.createQuery("select s from SolicitudEvidenciaEntity s where s.actorId=:actor and (s.estado in :recibida or (s.estado in :espera and s.reglaAplicada>0)) and exists(select e.id from EjecucionEntity e where e.id=s.ejecucionId and e.estado=:curso) order by s.fechaEnvio desc",SolicitudEvidenciaEntity.class)
            .setParameter("actor",actorId).setParameter("recibida",List.of(SolicitudEvidencia.Estado.EVIDENCIA_RECIBIDA,SolicitudEvidencia.Estado.CONFIRMACION_RECIBIDA,SolicitudEvidencia.Estado.ACCION_PENDIENTE_APROBACION,SolicitudEvidencia.Estado.ENVIADA_PENDIENTE_APROBACION)).setParameter("espera",List.of(SolicitudEvidencia.Estado.ESPERANDO_EVIDENCIA,SolicitudEvidencia.Estado.ESPERANDO_CONFIRMACION)).setParameter("curso",EstadoEjecucion.EN_CURSO).setMaxResults(50).getResultList().stream().map(SolicitudEvidenciaEntity::toDomain).toList();
    }
    public SolicitudEvidencia guardar(SolicitudEvidencia s) {
        var entity=new SolicitudEvidenciaEntity(s);
        if(s.version()==null)em.persist(entity);else entity=em.merge(entity);
        em.flush();return entity.toDomain();
    }
    public EvidenciaFotografica guardar(EvidenciaFotografica e) {var entity=new EvidenciaFotograficaEntity(e);em.persist(entity);em.flush();return entity.toDomain();}
    public List<EvidenciaFotografica> evidencias(String id) {return em.createQuery("select e from EvidenciaFotograficaEntity e where e.solicitudId=:id order by e.categoria",EvidenciaFotograficaEntity.class).setParameter("id",id).getResultList().stream().map(EvidenciaFotograficaEntity::toDomain).toList();}
    public Optional<EvidenciaFotografica> evidencia(Long id) {return Optional.ofNullable(em.find(EvidenciaFotograficaEntity.class,id)).map(EvidenciaFotograficaEntity::toDomain);}
    public List<FotoEvidenciaResumen> fotosResumen(String id){return em.createQuery("select new cl.casol.backend.ejecucion.domain.FotoEvidenciaResumen(e.id,e.categoria,e.sha256) from EvidenciaFotograficaEntity e where e.solicitudId=:id order by e.categoria",FotoEvidenciaResumen.class).setParameter("id",id).getResultList();}
    public void registrar(EventoAutomatizacion e) {em.persist(new EventoAutomatizacionEntity(e));}
    public List<EventoAutomatizacion> eventos(Integer id) {return em.createQuery("select e from EventoAutomatizacionEntity e where e.ejecucionId=:id order by e.id",EventoAutomatizacionEntity.class).setParameter("id",id).getResultList().stream().map(EventoAutomatizacionEntity::toDomain).toList();}
    public boolean cuotaCorreoDisponible(Integer actor,Instant desde,int maximo){
        // Bloqueo de una sola cuenta: dos ejecuciones concurrentes no eluden la cuota.
        em.createNativeQuery("select usuario_id from u_usuario where usuario_id=:id for update",Integer.class).setParameter("id",actor).getSingleResult();
        return em.createQuery("select count(e) from EventoAutomatizacionEntity e where e.actorId=:actor and e.fecha>=:desde and e.tipo in :tipos",Long.class)
            .setParameter("actor",actor).setParameter("desde",desde).setParameter("tipos",List.of("SMTP_INICIADO","RECORDATORIO_SMTP_INICIADO","GMAIL_INICIADO","RECORDATORIO_GMAIL_INICIADO")).getSingleResult()<maximo;
    }
}
