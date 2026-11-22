package cl.casol.backend.procedimiento.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import cl.casol.backend.identidad.infrastructure.persistence.repository.UsuarioJpaRepository;
import cl.casol.backend.procedimiento.application.port.out.ProcedimientoRepository;
import cl.casol.backend.procedimiento.domain.Procedimiento;
import cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ProcedimientoMapper;
import cl.casol.backend.procedimiento.infrastructure.persistence.repository.ProcedimientoJpaRepository;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Adaptador de base de datos para procedimiento.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class ProcedimientoRepositoryAdapter implements ProcedimientoRepository {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    // Indica si tiene ejecuciones.
    public boolean tieneEjecuciones(Integer id){return entityManager.createQuery("select count(e) from EjecucionEntity e where e.procedimientoId=:id",Long.class).setParameter("id",id).getSingleResult()>0;}
    // Bloquea por id.
    public void bloquear(Integer id) {
        var p=entityManager.find(cl.casol.backend.procedimiento.infrastructure.persistence.entity.ProcedimientoEntity.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(p!=null)entityManager.refresh(p,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
    private final ProcedimientoJpaRepository repository;
    private final UsuarioJpaRepository usuarios;
    public ProcedimientoRepositoryAdapter(ProcedimientoJpaRepository repository, UsuarioJpaRepository usuarios) {
        this.repository=repository; this.usuarios=usuarios;
    }
    // Busca todos.
    public List<Procedimiento> buscarTodos() {
        return repository.listarConRelaciones().stream().map(ProcedimientoMapper::toDomain).toList();
    }
    // Busca por id.
    public Optional<Procedimiento> buscarPorId(Integer id) {
        return repository.findById(id).map(ProcedimientoMapper::toDomain);
    }
    public Procedimiento guardar(Procedimiento p) {
        UsuarioEntity creador=usuarios.getReferenceById(p.creadoPor().getId());
        UsuarioEntity modificador=p.modificadoPor()==null?null:usuarios.getReferenceById(p.modificadoPor().getId());
        return ProcedimientoMapper.toDomain(repository.saveAndFlush(ProcedimientoMapper.toEntity(p, creador, modificador)));
    }
}
