package cl.casol.backend.ejecucion.infrastructure.persistence.adapter;
import cl.casol.backend.ejecucion.application.port.out.EjecucionPasoRepository;
import cl.casol.backend.ejecucion.domain.EjecucionPaso;
import cl.casol.backend.ejecucion.infrastructure.persistence.mapper.EjecucionMapper;
import cl.casol.backend.ejecucion.infrastructure.persistence.repository.EjecucionPasoJpaRepository;
import org.springframework.stereotype.Component;
import java.util.*;
/**
 * Adaptador de base de datos para ejecución paso.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component public class EjecucionPasoRepositoryAdapter implements EjecucionPasoRepository {
    private final EjecucionPasoJpaRepository repository;
    public EjecucionPasoRepositoryAdapter(EjecucionPasoJpaRepository repository){this.repository=repository;}
    public EjecucionPaso guardar(EjecucionPaso p){return EjecucionMapper.toDomain(repository.save(EjecucionMapper.toEntity(p)));}
    // Guarda todos.
    public List<EjecucionPaso> guardarTodos(List<EjecucionPaso> ps){return repository.saveAll(ps.stream().map(EjecucionMapper::toEntity).toList()).stream().map(EjecucionMapper::toDomain).toList();}
    // Busca por id.
    public Optional<EjecucionPaso> buscarPorId(Integer id){return repository.findById(id).map(EjecucionMapper::toDomain);}
    // Busca por ejecución.
    public List<EjecucionPaso> buscarPorEjecucion(Integer id){return repository.findByEjecucionId(id).stream().map(EjecucionMapper::toDomain).toList();}
}
