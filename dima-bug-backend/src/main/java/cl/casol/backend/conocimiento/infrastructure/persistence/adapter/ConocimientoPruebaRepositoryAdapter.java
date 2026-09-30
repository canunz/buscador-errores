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

@Component
public class ConocimientoPruebaRepositoryAdapter implements ConocimientoPruebaRepository {
    private final ConocimientoPruebaJpaRepository repository;
    private final PruebaJpaRepository pruebas;

    public ConocimientoPruebaRepositoryAdapter(ConocimientoPruebaJpaRepository repository,
            PruebaJpaRepository pruebas) {
        this.repository = repository;
        this.pruebas = pruebas;
    }

    @Override
    public List<ConocimientoPrueba> buscarActivasPorConocimientoOrdenadas(Integer conocimientoId) {
        return repository.findByIdConocimientoIdAndPruebaActivaTrueOrderByOrdenAsc(conocimientoId).stream()
                .map(PruebaMapper::toDomain).toList();
    }

    @Override
    public Optional<ConocimientoPrueba> buscarPorIds(Integer conocimientoId, Integer pruebaId) {
        return repository.findById(new ConocimientoPruebaId(conocimientoId, pruebaId)).map(PruebaMapper::toDomain);
    }

    @Override
    public boolean existe(Integer conocimientoId, Integer pruebaId) {
        return repository.existsById(new ConocimientoPruebaId(conocimientoId, pruebaId));
    }

    @Override
    public ConocimientoPrueba guardar(ConocimientoPrueba asociacion) {
        var prueba = pruebas.getReferenceById(asociacion.prueba().id());
        return PruebaMapper.toDomain(repository.save(PruebaMapper.toEntity(asociacion, prueba)));
    }
}
