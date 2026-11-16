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
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    public PasoRepositoryAdapter(PasoJpaRepository repository){this.repository=repository;}
    public List<Paso> buscarPorProcedimientoOrdenados(Integer id){return repository.findByProcedimientoIdOrderByOrdenAsc(id).stream().map(PasoMapper::toDomain).toList();}
    public Optional<Paso> buscarPorId(Integer id){return repository.findById(id).map(PasoMapper::toDomain);}
    public boolean existeOrden(Integer p,Integer o){return repository.existsByProcedimientoIdAndOrden(p,o);}
    public boolean existeOrdenExcluyendoPaso(Integer p,Integer o,Integer id){return repository.existsByProcedimientoIdAndOrdenAndIdNot(p,o,id);}
    public Paso guardar(Paso paso){var entity=PasoMapper.toEntity(paso);if(paso.id()!=null)repository.findById(paso.id()).ifPresent(v->entity.setHistorial(v.getHistorial()));return PasoMapper.toDomain(repository.save(entity));}
    public Paso configurar(Paso paso,Integer actorId){
        var anterior=repository.findById(paso.id()).orElseThrow();
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        java.util.List<Object> historial=anterior.getHistorial()==null?new java.util.ArrayList<>():new java.util.ArrayList<>(json.readValue(anterior.getHistorial(),java.util.List.class));
        var antes=PasoMapper.toDomain(anterior);var evento=new java.util.LinkedHashMap<String,Object>();
        evento.put("actorId",actorId);evento.put("fecha",java.time.Instant.now().toString());evento.put("antes",antes);evento.put("despues",paso);historial.add(evento);
        var entity=PasoMapper.toEntity(paso);entity.setHistorial(json.writeValueAsString(historial));
        return PasoMapper.toDomain(repository.save(entity));
    }
    public void eliminar(Integer id){em.createQuery("delete from MaterialApoyoEntity m where m.pasoId=:id").setParameter("id",id).executeUpdate();repository.deleteById(id);em.flush();}
    public void reordenar(List<Paso> ordenados){
        int max=ordenados.stream().mapToInt(Paso::orden).max().orElse(0);
        if(max>Integer.MAX_VALUE-ordenados.size())throw new cl.casol.backend.procedimiento.domain.exception.EstadoProcedimientoInvalidoException("No se puede reservar el orden temporal");
        em.flush();for(int i=0;i<ordenados.size();i++)em.createQuery("update PasoEntity p set p.orden=:orden where p.id=:id").setParameter("orden",max+i+1).setParameter("id",ordenados.get(i).id()).executeUpdate();
        for(int i=0;i<ordenados.size();i++)em.createQuery("update PasoEntity p set p.orden=:orden where p.id=:id").setParameter("orden",i+1).setParameter("id",ordenados.get(i).id()).executeUpdate();em.clear();
    }

}
