package cl.casol.backend.procedimiento.infrastructure.persistence.adapter;

import cl.casol.backend.procedimiento.application.port.out.PasoRepository;
import cl.casol.backend.procedimiento.domain.Paso;
import cl.casol.backend.procedimiento.infrastructure.persistence.mapper.PasoMapper;
import cl.casol.backend.procedimiento.infrastructure.persistence.repository.PasoJpaRepository;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PasoRepositoryAdapter implements PasoRepository {
    private final PasoJpaRepository repository;
    public PasoRepositoryAdapter(PasoJpaRepository repository){this.repository=repository;}
    public List<Paso> buscarPorProcedimientoOrdenados(Integer id){return repository.findByProcedimientoIdOrderByOrdenAsc(id).stream().map(PasoMapper::toDomain).toList();}
    public Optional<Paso> buscarPorId(Integer id){return repository.findById(id).map(PasoMapper::toDomain);}
    public boolean existeOrden(Integer p,Integer o){return repository.existsByProcedimientoIdAndOrden(p,o);}
    public boolean existeOrdenExcluyendoPaso(Integer p,Integer o,Integer id){return repository.existsByProcedimientoIdAndOrdenAndIdNot(p,o,id);}
    public Paso guardar(Paso paso){return PasoMapper.toDomain(repository.save(PasoMapper.toEntity(paso)));}
}
