package cl.casol.backend.ejecucion.infrastructure.persistence.adapter;
import cl.casol.backend.ejecucion.application.port.out.EjecucionPasoRepository;
import cl.casol.backend.ejecucion.domain.EjecucionPaso;
import cl.casol.backend.ejecucion.infrastructure.persistence.mapper.EjecucionMapper;
import cl.casol.backend.ejecucion.infrastructure.persistence.repository.EjecucionPasoJpaRepository;
import org.springframework.stereotype.Component;
import java.util.*;
@Component public class EjecucionPasoRepositoryAdapter implements EjecucionPasoRepository {
    private final EjecucionPasoJpaRepository repository;
    public EjecucionPasoRepositoryAdapter(EjecucionPasoJpaRepository repository){this.repository=repository;}
    public EjecucionPaso guardar(EjecucionPaso p){return EjecucionMapper.toDomain(repository.save(EjecucionMapper.toEntity(p)));}
    public List<EjecucionPaso> guardarTodos(List<EjecucionPaso> ps){return repository.saveAll(ps.stream().map(EjecucionMapper::toEntity).toList()).stream().map(EjecucionMapper::toDomain).toList();}
    public Optional<EjecucionPaso> buscarPorId(Integer id){return repository.findById(id).map(EjecucionMapper::toDomain);}
    public List<EjecucionPaso> buscarPorEjecucion(Integer id){return repository.findByEjecucionId(id).stream().map(EjecucionMapper::toDomain).toList();}
}
