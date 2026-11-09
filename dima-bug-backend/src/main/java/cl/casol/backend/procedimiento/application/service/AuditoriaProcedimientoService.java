package cl.casol.backend.procedimiento.application.service;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.procedimiento.domain.Procedimiento;
import cl.casol.backend.procedimiento.infrastructure.persistence.entity.AuditoriaProcedimientoEntity;
import jakarta.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.*;

@Service
public class AuditoriaProcedimientoService {
    @PersistenceContext private EntityManager em;
    private final UsuarioRepository usuarios;private final Clock reloj;
    public AuditoriaProcedimientoService(UsuarioRepository usuarios,Clock reloj){this.usuarios=usuarios;this.reloj=reloj;}
    public Usuario administrador(String email){return usuarios.buscarPorEmail(email).filter(u->u.isActivo() && u.getRol()!=null && "ADMINISTRADOR".equalsIgnoreCase(u.getRol().getNombre().strip())).orElseThrow(()->new AccessDeniedException("Solo un administrador puede modificar procedimientos"));}
    @Transactional(propagation=Propagation.MANDATORY)
    public void registrar(Integer id,String accion,String email,Object antes,Object despues){registrar(id,accion,administrador(email).getId(),antes,despues);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void registrar(Integer id,String accion,Integer actor,Object antes,Object despues){
        var json=tools.jackson.databind.json.JsonMapper.builder().build();em.persist(new AuditoriaProcedimientoEntity(id,actor,reloj.instant(),accion,antes==null?null:json.writeValueAsString(antes),despues==null?null:json.writeValueAsString(despues)));
    }
    /** Lista explícita: nunca serializa Usuario ni hashes de contraseñas en auditoría. */
    public static Map<String,Object> datos(Procedimiento p){var d=new LinkedHashMap<String,Object>();d.put("id",p.id());d.put("nombre",p.nombre());d.put("descripcion",p.descripcion());d.put("estado",p.estado());d.put("datosEditoriales",p.datos());d.put("hardwareId",p.hardwareId());return d;}
    public record Evento(Long id,Integer procedimientoId,Integer actorId,java.time.Instant fecha,String accion,String antes,String despues){}
    @Transactional(readOnly=true,timeout=8)
    public List<Evento> listar(Integer id,String email){administrador(email);return em.createQuery("select a from AuditoriaProcedimientoEntity a where a.procedimientoId=:id order by a.id desc",AuditoriaProcedimientoEntity.class).setParameter("id",id).getResultList().stream().map(a->new Evento(a.getId(),a.getProcedimientoId(),a.getActorId(),a.getFecha(),a.getAccion(),a.getAntes(),a.getDespues())).toList();}
}
