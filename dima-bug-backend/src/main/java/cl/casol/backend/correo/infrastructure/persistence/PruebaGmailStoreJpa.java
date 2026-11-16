package cl.casol.backend.correo.infrastructure.persistence;
import cl.casol.backend.correo.application.port.out.PruebaGmailStore;
import cl.casol.backend.correo.application.service.GmailException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.*;
import java.time.*;import java.util.*;
@Repository @Transactional(timeout=8)
public class PruebaGmailStoreJpa implements PruebaGmailStore {
    @PersistenceContext EntityManager em;private final Clock reloj;
    public PruebaGmailStoreJpa(Clock reloj){this.reloj=reloj;}
    private Prueba dto(PruebaGmailEntity p){return new Prueba(p.id,p.actor,p.remitente,p.destinatario,p.estado,p.creada,p.intento,p.finalizada,p.mensajeId,p.resultado);}
    @Transactional(readOnly=true) public Optional<Prueba> buscar(String id){return Optional.ofNullable(em.find(PruebaGmailEntity.class,id)).map(this::dto);}
    @Transactional(readOnly=true) public Optional<Prueba> ultima(String destinatario){return em.createQuery("select p from PruebaGmailEntity p where p.destinatario=:d order by p.creada desc,p.id desc",PruebaGmailEntity.class).setParameter("d",destinatario).setMaxResults(1).getResultStream().findFirst().map(this::dto);}
    private void evento(PruebaGmailEntity p){var e=new PruebaGmailEventoEntity();e.prueba=p.id;e.actor=p.actor;e.destinatario=p.destinatario;e.estado=p.estado.name();e.fecha=reloj.instant();e.mensajeId=p.mensajeId;e.resultado=p.resultado;em.persist(e);}
    public Prueba preparar(String clave,String anterior,Integer actor,String remitente,String destinatario){
        // Serializa las pruebas de todos los administradores sin modificar los tokens de la cuenta.
        em.createNativeQuery("select id from gm_cuenta_oauth where id=1 for update",Integer.class).getSingleResult();
        var previa=em.find(PruebaGmailEntity.class,clave);
        if(previa!=null){if(!previa.destinatario.equals(destinatario))throw new GmailException(GmailException.Tipo.CONFLICTO);return dto(previa);}
        var ultima=ultima(destinatario);
        if(ultima.isPresent() && (ultima.get().estado()!=Estado.FALLIDO || !Objects.equals(anterior,ultima.get().id())))return ultima.get();
        long intentos=em.createQuery("select count(p) from PruebaGmailEntity p where p.destinatario=:d and p.creada>=:desde",Long.class).setParameter("d",destinatario).setParameter("desde",reloj.instant().minusSeconds(3600)).getSingleResult();
        if(intentos>=3)throw new GmailException(GmailException.Tipo.CONFLICTO);
        var p=new PruebaGmailEntity();p.id=clave;p.actor=actor;p.remitente=remitente;p.destinatario=destinatario;p.estado=Estado.PENDIENTE;p.creada=reloj.instant();em.persist(p);evento(p);em.flush();return dto(p);
    }
    public boolean reservar(String id){
        int n=em.createQuery("update PruebaGmailEntity p set p.estado=:enviando,p.intento=:ahora,p.version=p.version+1 where p.id=:id and p.estado=:pendiente").setParameter("enviando",Estado.ENVIANDO).setParameter("ahora",reloj.instant()).setParameter("id",id).setParameter("pendiente",Estado.PENDIENTE).executeUpdate();
        em.clear();if(n!=1)return false;evento(em.find(PruebaGmailEntity.class,id));em.flush();return true;
    }
    public Prueba finalizar(String id,Estado estado,String mensajeId,String resultado){var p=em.find(PruebaGmailEntity.class,id,LockModeType.PESSIMISTIC_WRITE);if(p.estado!=Estado.ENVIANDO)return dto(p);p.estado=estado;p.mensajeId=mensajeId;p.resultado=resultado;p.finalizada=reloj.instant();evento(p);em.flush();return dto(p);}
}
