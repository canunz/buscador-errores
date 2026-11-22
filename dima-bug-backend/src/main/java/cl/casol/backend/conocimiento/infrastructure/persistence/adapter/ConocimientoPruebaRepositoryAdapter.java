package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.ConocimientoPruebaRepository;
import cl.casol.backend.conocimiento.domain.ConocimientoPrueba;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoPruebaId;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.PruebaMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.ConocimientoPruebaJpaRepository;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.PruebaJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de base de datos para conocimiento prueba.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class ConocimientoPruebaRepositoryAdapter implements ConocimientoPruebaRepository {
    // Busca por conocimiento ordenadas.
    public List<ConocimientoPrueba> buscarPorConocimientoOrdenadas(Integer id) { return repository.findByIdConocimientoIdOrderByOrdenAscIdPruebaIdAsc(id).stream().map(PruebaMapper::toDomain).toList(); }
    private final ConocimientoPruebaJpaRepository repository;
    private final PruebaJpaRepository pruebas;

    public ConocimientoPruebaRepositoryAdapter(ConocimientoPruebaJpaRepository repository,
            PruebaJpaRepository pruebas) {
        this.repository = repository;
        this.pruebas = pruebas;
    }

    // Busca activas por conocimiento ordenadas.
    @Override
    public List<ConocimientoPrueba> buscarActivasPorConocimientoOrdenadas(Integer conocimientoId) {
        return repository.findByIdConocimientoIdAndPruebaActivaTrueOrderByOrdenAsc(conocimientoId).stream()
                .map(PruebaMapper::toDomain).toList();
    }

    // Busca por ids.
    @Override
    public Optional<ConocimientoPrueba> buscarPorIds(Integer conocimientoId, Integer pruebaId) {
        return repository.findById(new ConocimientoPruebaId(conocimientoId, pruebaId)).map(PruebaMapper::toDomain);
    }

    // Indica si existe.
    @Override
    public boolean existe(Integer conocimientoId, Integer pruebaId) {
        return repository.existsById(new ConocimientoPruebaId(conocimientoId, pruebaId));
    }

    // Guarda la asociación.
    @Override
    public ConocimientoPrueba guardar(ConocimientoPrueba asociacion) {
        var prueba = pruebas.getReferenceById(asociacion.prueba().id());
        return PruebaMapper.toDomain(repository.save(PruebaMapper.toEntity(asociacion, prueba)));
    }
}
