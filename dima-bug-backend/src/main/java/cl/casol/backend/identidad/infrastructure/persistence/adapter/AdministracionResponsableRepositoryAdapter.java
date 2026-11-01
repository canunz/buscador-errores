package cl.casol.backend.identidad.infrastructure.persistence.adapter;
import cl.casol.backend.identidad.application.port.out.AdministracionResponsableRepository;
import cl.casol.backend.identidad.domain.Responsable;
import cl.casol.backend.identidad.domain.exception.*;
import cl.casol.backend.identidad.infrastructure.persistence.entity.ResponsableEntity;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.OrganizacionMapper;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class AdministracionResponsableRepositoryAdapter implements AdministracionResponsableRepository {
    private final EntityManager em;
    public AdministracionResponsableRepositoryAdapter(EntityManager em) { this.em = em; }
    public List<Responsable> listar(Integer departamentoId) {
        var q = em.createQuery("select r from ResponsableEntity r" +
                (departamentoId == null ? "" : " where r.departamentoId=:departamento") + " order by r.nombre, r.id", ResponsableEntity.class);
        if (departamentoId != null) q.setParameter("departamento", departamentoId);
        return q.getResultList().stream().map(OrganizacionMapper::toDomain).toList();
    }
    public Optional<Responsable> buscar(Integer id) {
        return Optional.ofNullable(em.find(ResponsableEntity.class, id)).map(OrganizacionMapper::toDomain);
    }
    public Optional<Responsable> bloquear(Integer id) {
        var entity = em.find(ResponsableEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (entity != null) em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return Optional.ofNullable(entity).map(OrganizacionMapper::toDomain);
    }
    public Responsable guardar(Responsable r) {
        ResponsableEntity entity;
        if (r.id() == null) {
            entity = new ResponsableEntity(r.departamentoId(), r.nombre(), r.cargo(), r.contacto(), r.activo());
            em.persist(entity);
        } else {
            entity = em.find(ResponsableEntity.class, r.id(), LockModeType.PESSIMISTIC_WRITE);
            if (entity == null) throw new ResponsableNoEncontradoException(r.id());
            if (!Objects.equals(entity.getDepartamentoId(), r.departamentoId()))
                throw new OrganizacionConflictoException("No se permite cambiar el departamento del responsable");
            entity.actualizar(r.nombre(), r.cargo(), r.contacto(), r.activo());
        }
        em.flush();
        return OrganizacionMapper.toDomain(entity);
    }
}
