package cl.casol.backend.identidad.infrastructure.persistence.adapter;
import cl.casol.backend.identidad.application.port.out.AdministracionOrganizacionRepository;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.OrganizacionConflictoException;
import cl.casol.backend.identidad.infrastructure.persistence.entity.*;
import cl.casol.backend.identidad.infrastructure.persistence.repository.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class AdministracionOrganizacionRepositoryAdapter implements AdministracionOrganizacionRepository {
    private final EntityManager em;
    private final DepartamentoJpaRepository departamentos;
    private final DepartamentoContactoJpaRepository contactos;
    public AdministracionOrganizacionRepositoryAdapter(EntityManager em, DepartamentoJpaRepository departamentos,
            DepartamentoContactoJpaRepository contactos) {
        this.em=em; this.departamentos=departamentos; this.contactos=contactos;
    }
    public void bloquearEscrituras() {
        var fila=em.find(OrganizacionControlEntity.class,1,LockModeType.PESSIMISTIC_WRITE);
        if(fila==null) throw new OrganizacionConflictoException("Falta aplicar la migración autorizada de organización");
        em.refresh(fila,LockModeType.PESSIMISTIC_WRITE);
    }
    public void bloquearDepartamento(Integer id) {
        var d=em.find(DepartamentoEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(d!=null) em.refresh(d,LockModeType.PESSIMISTIC_WRITE);
    }
    public List<DepartamentoAdministrativo> listarDepartamentos() {
        return departamentos.findAll().stream().map(this::departamento)
                .sorted(Comparator.comparing(DepartamentoAdministrativo::nombre,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public Optional<DepartamentoAdministrativo> buscarDepartamento(Integer id) {
        return departamentos.findById(id).map(this::departamento);
    }
    public DepartamentoAdministrativo guardarDepartamento(DepartamentoAdministrativo d) {
        return departamento(departamentos.saveAndFlush(new DepartamentoEntity(d.id(),d.nombre(),d.activo(),d.version())));
    }
    public List<ContactoDepartamentoAdministrativo> listarContactos(Integer id) {
        return em.createQuery("select c from DepartamentoContactoEntity c where c.departamentoId=:id order by c.id",DepartamentoContactoEntity.class)
            .setParameter("id",id).getResultList().stream().map(this::contacto).toList();
    }
    public Optional<ContactoDepartamentoAdministrativo> buscarContacto(Integer id) {
        return contactos.findById(id).map(this::contacto);
    }
    public ContactoDepartamentoAdministrativo guardarContacto(ContactoDepartamentoAdministrativo c) {
        return contacto(contactos.saveAndFlush(new DepartamentoContactoEntity(c.id(),c.departamentoId(),c.tipo(),c.valor(),c.activo(),c.version())));
    }
    private DepartamentoAdministrativo departamento(DepartamentoEntity d) {
        return new DepartamentoAdministrativo(d.getId(),d.getNombre(),d.isActivo(),d.getVersion());
    }
    private ContactoDepartamentoAdministrativo contacto(DepartamentoContactoEntity c) {
        return new ContactoDepartamentoAdministrativo(c.getId(),c.getDepartamentoId(),c.getTipo(),c.getValor(),c.isActivo(),c.getVersion());
    }
}
