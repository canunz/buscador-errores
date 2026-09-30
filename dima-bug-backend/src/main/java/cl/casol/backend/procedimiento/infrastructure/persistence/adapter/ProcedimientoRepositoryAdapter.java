package cl.casol.backend.procedimiento.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import cl.casol.backend.identidad.infrastructure.persistence.repository.UsuarioJpaRepository;
import cl.casol.backend.procedimiento.application.port.out.ProcedimientoRepository;
import cl.casol.backend.procedimiento.domain.Procedimiento;
import cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ProcedimientoMapper;
import cl.casol.backend.procedimiento.infrastructure.persistence.repository.ProcedimientoJpaRepository;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ProcedimientoRepositoryAdapter implements ProcedimientoRepository {
    private final ProcedimientoJpaRepository repository;
    private final UsuarioJpaRepository usuarios;
    public ProcedimientoRepositoryAdapter(ProcedimientoJpaRepository repository, UsuarioJpaRepository usuarios) {
        this.repository=repository; this.usuarios=usuarios;
    }
    public List<Procedimiento> buscarTodos() {
        return repository.findAllByOrderByFechaCreacionDesc().stream().map(ProcedimientoMapper::toDomain).toList();
    }
    public Optional<Procedimiento> buscarPorId(Integer id) {
        return repository.findById(id).map(ProcedimientoMapper::toDomain);
    }
    public Procedimiento guardar(Procedimiento p) {
        UsuarioEntity creador=usuarios.getReferenceById(p.creadoPor().getId());
        UsuarioEntity modificador=p.modificadoPor()==null?null:usuarios.getReferenceById(p.modificadoPor().getId());
        return ProcedimientoMapper.toDomain(repository.save(ProcedimientoMapper.toEntity(p, creador, modificador)));
    }
}
