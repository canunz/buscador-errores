package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;
import cl.casol.backend.conocimiento.application.port.out.PruebaAdministrativaRepository;
import cl.casol.backend.conocimiento.domain.Prueba;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.PruebaEntity;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.PruebaMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.PruebaJpaRepository;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class PruebaAdministrativaRepositoryAdapter implements PruebaAdministrativaRepository {
 private final PruebaJpaRepository repo; private final EntityManager em;
 public PruebaAdministrativaRepositoryAdapter(PruebaJpaRepository repo,EntityManager em){this.repo=repo;this.em=em;}
 public List<Prueba> listar(){return repo.findAll().stream().map(PruebaMapper::toDomain).sorted(Comparator.comparing(Prueba::descripcion).thenComparing(Prueba::id)).toList();}
 public Optional<Prueba> buscar(Integer id){return repo.findById(id).map(PruebaMapper::toDomain);}
 public Prueba guardar(Prueba p){return PruebaMapper.toDomain(repo.saveAndFlush(new PruebaEntity(p.id(),p.descripcion(),p.resultadoEsperado(),p.activa())));}
 public void bloquear(Integer id){var p=em.find(PruebaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);if(p!=null)em.refresh(p,LockModeType.PESSIMISTIC_WRITE);}
 public List<Integer> conocimientos(Integer id){return em.createQuery("select distinct c.id.conocimientoId from ConocimientoPruebaEntity c where c.id.pruebaId=:id",Integer.class).setParameter("id",id).getResultList();}
}