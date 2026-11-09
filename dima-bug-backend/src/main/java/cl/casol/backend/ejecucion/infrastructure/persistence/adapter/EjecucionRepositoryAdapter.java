package cl.casol.backend.ejecucion.infrastructure.persistence.adapter;
import cl.casol.backend.ejecucion.application.port.out.EjecucionRepository;
import cl.casol.backend.ejecucion.domain.Ejecucion;
import cl.casol.backend.ejecucion.infrastructure.persistence.mapper.EjecucionMapper;
import cl.casol.backend.ejecucion.infrastructure.persistence.repository.EjecucionJpaRepository;
import org.springframework.stereotype.Component;
import java.util.*;
@Component public class EjecucionRepositoryAdapter implements EjecucionRepository {
    private final EjecucionJpaRepository repository;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    public EjecucionRepositoryAdapter(EjecucionJpaRepository repository){this.repository=repository;}
    public Ejecucion guardar(Ejecucion e){return EjecucionMapper.toDomain(repository.save(EjecucionMapper.toEntity(e)));}
    public Optional<Ejecucion> buscarPorId(Integer id){return repository.findById(id).map(EjecucionMapper::toDomain);}
    public void bloquear(Integer id) {
        var entity = entityManager.find(cl.casol.backend.ejecucion.infrastructure.persistence.entity.EjecucionEntity.class,
                id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (entity != null) entityManager.refresh(entity, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
    public List<Ejecucion> buscarTodas(){return repository.findAllByOrderByFechaInicioDesc().stream().map(EjecucionMapper::toDomain).toList();}
    public List<Ejecucion> buscarPorUsuario(Integer id){return repository.findByUsuarioIdOrderByFechaInicioDesc(id).stream().map(EjecucionMapper::toDomain).toList();}
}
