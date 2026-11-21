package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.MaterialApoyoRepository;
import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.MaterialApoyoMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.MaterialApoyoJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de base de datos para material apoyo.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class MaterialApoyoRepositoryAdapter implements MaterialApoyoRepository {
    private final MaterialApoyoJpaRepository repository;

    public MaterialApoyoRepositoryAdapter(MaterialApoyoJpaRepository repository) { this.repository = repository; }

    // Busca directos por conocimiento.
    @Override
    public List<MaterialApoyo> buscarDirectosPorConocimiento(Integer conocimientoId) {
        return repository.findByConocimientoIdAndPasoIdIsNullOrderByIdAsc(conocimientoId).stream()
                .map(MaterialApoyoMapper::toDomain).toList();
    }

    // Busca por paso.
    @Override
    public List<MaterialApoyo> buscarPorPaso(Integer pasoId) {
        return repository.findByPasoIdAndConocimientoIdIsNullOrderByIdAsc(pasoId).stream()
                .map(MaterialApoyoMapper::toDomain).toList();
    }

    // Busca por id.
    @Override
    public Optional<MaterialApoyo> buscarPorId(Integer id) {
        return repository.findById(id).map(MaterialApoyoMapper::toDomain);
    }

    // Elimina por id.
    @Override
    public void eliminar(Integer id) {
        repository.deleteById(id);
    }

    // Guarda el material.
    @Override
    public MaterialApoyo guardar(MaterialApoyo material) {
        return MaterialApoyoMapper.toDomain(repository.save(MaterialApoyoMapper.toEntity(material)));
    }
}
