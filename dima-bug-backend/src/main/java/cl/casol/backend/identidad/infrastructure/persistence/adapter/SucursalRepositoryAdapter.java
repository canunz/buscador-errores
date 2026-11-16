package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.SucursalRepository;
import cl.casol.backend.identidad.domain.Sucursal;
import cl.casol.backend.identidad.infrastructure.persistence.entity.SucursalEntity;
import cl.casol.backend.identidad.infrastructure.persistence.repository.SucursalJpaRepository;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class SucursalRepositoryAdapter implements SucursalRepository {
    private final SucursalJpaRepository repo;
    @PersistenceContext private EntityManager em;
    public SucursalRepositoryAdapter(SucursalJpaRepository repo) { this.repo=repo; }
    public List<Sucursal> listar() { return repo.findAllByOrderByCodigoAsc().stream().map(SucursalEntity::toDomain).toList(); }
    public Optional<Sucursal> buscar(Integer id) { return repo.findById(id).map(SucursalEntity::toDomain); }
    public Optional<Sucursal> buscarPorCodigo(String c) { return repo.findByCodigo(c).map(SucursalEntity::toDomain); }
    public void bloquear(Integer id) {
        var s=em.find(SucursalEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(s!=null) em.refresh(s,LockModeType.PESSIMISTIC_WRITE);
    }
    public Sucursal guardar(Sucursal s) { return repo.saveAndFlush(new SucursalEntity(s)).toDomain(); }
}
