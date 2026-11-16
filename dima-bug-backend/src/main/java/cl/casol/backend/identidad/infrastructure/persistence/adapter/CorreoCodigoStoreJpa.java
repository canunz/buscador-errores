package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.CorreoCodigoStore;
import cl.casol.backend.identidad.infrastructure.persistence.entity.CorreoCodigoEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Repository
public class CorreoCodigoStoreJpa implements CorreoCodigoStore {
    @PersistenceContext private EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public void guardar(String id, Long codigoId, String cifrado, LocalDateTime expira, Estado estado) {
        var e = new CorreoCodigoEntity();
        e.id = id; e.codigoId = codigoId; e.contenidoCifrado = cifrado;
        e.expira = expira; e.estado = estado; e.intentos = 0;
        e.proximoIntento = LocalDateTime.now(); e.actualizadoEn = e.proximoIntento;
        em.persist(e);
    }

    @Transactional(readOnly = true)
    public List<String> pendientes(LocalDateTime ahora) {
        // Incluye ENVIANDO abandonado: se marca incierto, nunca se vuelve a enviar a ciegas.
        return em.createQuery("select e.id from CorreoCodigoEntity e where "
                + "(e.estado in :estados and e.proximoIntento <= :ahora) "
                + "or (e.contenidoCifrado is not null and e.expira <= :ahora) order by e.proximoIntento", String.class)
                .setParameter("estados", List.of(Estado.PENDIENTE, Estado.ERROR_TEMPORAL, Estado.ENVIANDO))
                .setParameter("ahora", ahora).setMaxResults(20).getResultList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Trabajo> reservar(String id, LocalDateTime ahora) {
        var e = em.find(CorreoCodigoEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (e == null) return Optional.empty();
        if (!e.expira.isAfter(ahora)) {
            e.contenidoCifrado = null;
            if (e.estado == Estado.PENDIENTE || e.estado == Estado.ERROR_TEMPORAL) e.estado = Estado.EXPIRADO;
            else if (e.estado == Estado.ENVIANDO) e.estado = Estado.INCIERTO;
            return Optional.empty();
        }
        if (e.proximoIntento.isAfter(ahora)) return Optional.empty();
        if (e.estado == Estado.ENVIANDO) {
            e.estado = Estado.INCIERTO; e.contenidoCifrado = null; e.actualizadoEn = ahora;
            return Optional.empty();
        }
        if (e.estado != Estado.PENDIENTE && e.estado != Estado.ERROR_TEMPORAL) return Optional.empty();
        e.estado = Estado.ENVIANDO; e.intentos++; e.actualizadoEn = ahora;
        e.proximoIntento = ahora.plusSeconds(60);
        return Optional.of(new Trabajo(e.id, e.codigoId, e.contenidoCifrado, e.expira, e.intentos));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void terminar(String id, Estado estado, String referencia, LocalDateTime proximoIntento) {
        var e = em.find(CorreoCodigoEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (e == null || e.estado != Estado.ENVIANDO) return;
        e.estado = estado; e.referencia = referencia; e.actualizadoEn = LocalDateTime.now();
        e.proximoIntento = proximoIntento;
        if (estado != Estado.ERROR_TEMPORAL) e.contenidoCifrado = null;
    }
}
