package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;
import cl.casol.backend.conocimiento.application.port.out.MaterialSolucionRepository;
import cl.casol.backend.conocimiento.domain.MaterialSolucion;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.MaterialSolucionEntity;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.MaterialSolucionJpaRepository;
import org.springframework.stereotype.Component;
import java.util.*;
/**
 * Adaptador de base de datos para material solución.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class MaterialSolucionRepositoryAdapter implements MaterialSolucionRepository {
 private final MaterialSolucionJpaRepository repo;
 public MaterialSolucionRepositoryAdapter(MaterialSolucionJpaRepository repo){this.repo=repo;}
 public List<MaterialSolucion> listar(Integer id){return repo.findBySolucionIdOrderByIdAsc(id).stream().map(MaterialSolucionEntity::domain).toList();}
 public Optional<MaterialSolucion> buscar(Integer id){return repo.findById(id).map(MaterialSolucionEntity::domain);}
 public MaterialSolucion guardar(MaterialSolucion m){return repo.save(new MaterialSolucionEntity(m)).domain();}
 public void eliminar(Integer id){repo.deleteById(id);}
}
