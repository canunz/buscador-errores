package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.SolucionAsignacionRepository;
import cl.casol.backend.conocimiento.domain.SolucionAsignacion;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SolucionAsignacionEntity;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.SolucionMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.*;
import cl.casol.backend.identidad.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class SolucionAsignacionRepositoryAdapter implements SolucionAsignacionRepository {
    private final SolucionAsignacionJpaRepository repository;
    private final SolucionJpaRepository soluciones;
    private final ResponsableJpaRepository responsables;
    private final DepartamentoJpaRepository departamentos;

    public SolucionAsignacionRepositoryAdapter(SolucionAsignacionJpaRepository repository,
            SolucionJpaRepository soluciones, ResponsableJpaRepository responsables,
            DepartamentoJpaRepository departamentos) {
        this.repository = repository; this.soluciones = soluciones;
        this.responsables = responsables; this.departamentos = departamentos;
    }
    public List<SolucionAsignacion> buscarPorSolucionOrdenadas(Integer id) {
        return repository.findBySolucionIdOrderByPrincipalDescIdAsc(id).stream()
                .map(SolucionMapper::toDomain).toList();
    }
    public Optional<SolucionAsignacion> buscarPorId(Integer id) {
        return repository.findById(id).map(SolucionMapper::toDomain);
    }
    public SolucionAsignacion guardar(SolucionAsignacion a) {
        var solucion = soluciones.getReferenceById(a.solucionId());
        var responsable = a.responsable() == null ? null : responsables.getReferenceById(a.responsable().id());
        var departamento = a.departamento() == null ? null : departamentos.getReferenceById(a.departamento().id());
        return SolucionMapper.toDomain(repository.save(new SolucionAsignacionEntity(
                a.id(), solucion, responsable, departamento, a.principal())));
    }
}
