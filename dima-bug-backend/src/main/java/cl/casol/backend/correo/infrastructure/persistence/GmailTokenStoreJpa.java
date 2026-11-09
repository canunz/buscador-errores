package cl.casol.backend.correo.infrastructure.persistence;

import cl.casol.backend.correo.application.port.out.GmailTokenStore;
import cl.casol.backend.correo.application.service.GmailException;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import static cl.casol.backend.correo.application.service.GmailException.Tipo.*;

@Repository @Transactional(timeout=8)
public class GmailTokenStoreJpa implements GmailTokenStore {
    @PersistenceContext EntityManager em;private final Clock reloj;
    public GmailTokenStoreJpa(Clock reloj){this.reloj=reloj;}
    private Cuenta modelo(GmailCuentaEntity e){return new Cuenta(e.version,e.estado,e.acceso,e.refresco,e.revocacion,e.expira,e.actor);}
    private GmailCuentaEntity bloquear(){
        var e=em.find(GmailCuentaEntity.class,1,LockModeType.PESSIMISTIC_WRITE);
        if(e==null){e=new GmailCuentaEntity();e.id=1;em.persist(e);em.flush();}return e;
    }
    private void auditar(String tipo,Integer actor){var a=new GmailAuditoriaEntity();a.tipo=tipo;a.actor=actor;a.fecha=reloj.instant();em.persist(a);}
    @Transactional(readOnly=true) public Optional<Cuenta> cuenta(){return Optional.ofNullable(em.find(GmailCuentaEntity.class,1)).map(this::modelo);}
    public long versionActual(){return bloquear().version;}
    public void preparar(Intento i,Instant ahora){
        em.createQuery("delete from GmailIntentoEntity e where e.expira<:ahora").setParameter("ahora",ahora).executeUpdate();
        var cuenta=bloquear();if(cuenta.revocacion!=null || "ACTIVA".equals(cuenta.estado))throw new GmailException(CONFLICTO);
        long activos=em.createQuery("select count(e) from GmailIntentoEntity e where e.actor=:actor and e.consumido=false and e.expira>:ahora",Long.class).setParameter("actor",i.actor()).setParameter("ahora",ahora).getSingleResult();
        if(activos>=3)throw new GmailException(ESTADO);
        var e=new GmailIntentoEntity();e.estado=i.hashEstado();e.navegador=i.hashNavegador();e.verificador=i.verificadorCifrado();e.actor=i.actor();e.expira=i.expira();e.versionCuenta=i.versionCuenta();em.persist(e);auditar("AUTORIZACION_INICIADA",i.actor());
    }
    public Intento consumir(String hash,String navegador,Instant ahora){
        var e=em.find(GmailIntentoEntity.class,hash,LockModeType.PESSIMISTIC_WRITE);
        if(e==null || e.consumido || !e.expira.isAfter(ahora) || !MessageDigest.isEqual(e.navegador.getBytes(StandardCharsets.UTF_8),navegador.getBytes(StandardCharsets.UTF_8)))throw new GmailException(ESTADO);
        e.consumido=true;em.flush();return new Intento(e.estado,e.navegador,e.verificador,e.actor,e.expira,e.versionCuenta);
    }
    public Cuenta autorizar(long version,String acceso,String refresco,Instant expira,Integer actor){
        var e=bloquear();if(e.version!=version || e.revocacion!=null)throw new GmailException(CONFLICTO);
        e.acceso=acceso;e.refresco=refresco;e.expira=expira;e.estado="ACTIVA";e.actor=actor;auditar("CUENTA_AUTORIZADA",actor);em.flush();return modelo(e);
    }
    public Cuenta renovar(long version,String acceso,String refresco,Instant expira){
        var e=bloquear();if(e.version!=version || !"ACTIVA".equals(e.estado))throw new GmailException(CONFLICTO);
        e.acceso=acceso;e.refresco=refresco;e.expira=expira;auditar("TOKEN_RENOVADO",e.actor);em.flush();return modelo(e);
    }
    public void invalidar(long version){var e=bloquear();if(e.version!=version)return;e.acceso=null;e.refresco=null;e.expira=null;e.estado="REAUTORIZAR";auditar("REAUTORIZACION_REQUERIDA",e.actor);}
    public Cuenta revocarLocal(Integer actor){
        var e=bloquear();if(e.revocacion==null)e.revocacion=e.refresco;e.acceso=null;e.refresco=null;e.expira=null;e.estado="REVOCADA";auditar("REVOCACION_LOCAL",actor);em.flush();return modelo(e);
    }
    public void confirmarRevocacion(long version,Integer actor){var e=bloquear();if(e.version!=version || !"REVOCADA".equals(e.estado))throw new GmailException(CONFLICTO);e.revocacion=null;auditar("REVOCACION_GOOGLE_CONFIRMADA",actor);}
}
