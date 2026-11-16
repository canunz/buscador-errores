package cl.casol.backend.seguimiento.infrastructure.persistence.adapter;

import cl.casol.backend.seguimiento.application.port.out.ResultadoSolucionRepository;
import cl.casol.backend.seguimiento.domain.*;
import cl.casol.backend.seguimiento.infrastructure.persistence.mapper.ResultadoSolucionMapper;
import cl.casol.backend.seguimiento.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ResultadoSolucionRepositoryAdapter implements ResultadoSolucionRepository {
    private final ResultadoSolucionJpaRepository repository;
    public ResultadoSolucionRepositoryAdapter(ResultadoSolucionJpaRepository repository){this.repository=repository;}
    public ResultadoSolucion guardar(ResultadoSolucion r){return ResultadoSolucionMapper.toDomain(
            repository.save(ResultadoSolucionMapper.toEntity(r)));}
    public List<ResultadoSolucion> buscarPorSolucionOrdenados(Integer id){return repository
            .findBySolucionIdOrderByFechaDescIdDesc(id).stream().map(ResultadoSolucionMapper::toDomain).toList();}
    public EfectividadSolucion calcularEfectividad(Integer id){EfectividadProjection p=repository.calcularEfectividad(id);
        long total=p.getTotalAplicaciones(),funciono=p.getTotalFunciono(),noFunciono=p.getTotalNoFunciono();
        Double porcentaje=total==0?null:funciono*100.0/total;
        return new EfectividadSolucion(id,total,funciono,noFunciono,porcentaje);}
}
