package cl.casol.backend.identidad.application.service;
import cl.casol.backend.identidad.application.port.out.AdministracionOrganizacionRepository;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
@Service @Transactional(readOnly=true)
public class AdministrarOrganizacionService {
    private final AdministracionOrganizacionRepository repo;
    private final ApplicationEventPublisher eventos;
    public AdministrarOrganizacionService(AdministracionOrganizacionRepository repo,ApplicationEventPublisher eventos) {
        this.repo=repo; this.eventos=eventos;
    }
    public List<DepartamentoAdministrativo> listar() { return repo.listarDepartamentos(); }
    public DepartamentoAdministrativo buscar(Integer id) {
        return repo.buscarDepartamento(id).orElseThrow(()->new DepartamentoNoEncontradoException(id));
    }
    @Transactional public DepartamentoAdministrativo crear(String nombre) {
        repo.bloquearEscrituras(); String n=nombre(nombre); unico(n,null);
        return repo.guardarDepartamento(new DepartamentoAdministrativo(null,n,true,null));
    }
    @Transactional public DepartamentoAdministrativo editar(Integer id,String nombre,Long version) {
        bloquear(id); var actual=buscar(id); version(actual.version(),version);
        String n=nombre(nombre); unico(n,id);
        var guardado=repo.guardarDepartamento(new DepartamentoAdministrativo(id,n,actual.activo(),actual.version()));
        eventos.publishEvent(new OrganizacionActualizada(id)); return guardado;
    }
    @Transactional public DepartamentoAdministrativo estado(Integer id,boolean activo,Long version) {
        bloquear(id); var actual=buscar(id); version(actual.version(),version);
        var guardado=actual.activo()==activo ? actual : repo.guardarDepartamento(new DepartamentoAdministrativo(id,actual.nombre(),activo,actual.version()));
        eventos.publishEvent(new OrganizacionActualizada(id)); return guardado;
    }
    public List<ContactoDepartamentoAdministrativo> contactos(Integer padre) {
        buscar(padre); return repo.listarContactos(padre);
    }
    public ContactoDepartamentoAdministrativo contacto(Integer padre,Integer id) {
        buscar(padre); return repo.buscarContacto(id).filter(c->padre.equals(c.departamentoId()))
            .orElseThrow(()->new ContactoDepartamentoNoEncontradoException("Contacto inexistente o ajeno al departamento"));
    }
    @Transactional public ContactoDepartamentoAdministrativo crearContacto(Integer padre,String tipo,String valor,boolean activo) {
        bloquear(padre); var d=buscar(padre); if(activo) disponible(d);
        String t=texto(tipo,50,"Tipo"),v=texto(valor,100,"Valor"); unicoContacto(padre,t,v,null);
        return repo.guardarContacto(new ContactoDepartamentoAdministrativo(null,padre,t,v,activo,null));
    }
    @Transactional public ContactoDepartamentoAdministrativo editarContacto(Integer padre,Integer id,String tipo,String valor,Long version) {
        bloquear(padre); var c=contacto(padre,id); version(c.version(),version);
        String t=texto(tipo,50,"Tipo"),v=texto(valor,100,"Valor"); unicoContacto(padre,t,v,id);
        return repo.guardarContacto(new ContactoDepartamentoAdministrativo(id,padre,t,v,c.activo(),c.version()));
    }
    @Transactional public ContactoDepartamentoAdministrativo estadoContacto(Integer padre,Integer id,boolean activo,Long version) {
        bloquear(padre); var d=buscar(padre); var c=contacto(padre,id); version(c.version(),version);
        if(activo && !c.activo()) disponible(d);
        return c.activo()==activo ? c : repo.guardarContacto(new ContactoDepartamentoAdministrativo(id,padre,c.tipo(),c.valor(),activo,c.version()));
    }
    private void bloquear(Integer id) { repo.bloquearEscrituras(); repo.bloquearDepartamento(id); }
    private String nombre(String n) { return texto(n,255,"Nombre"); }
    private String texto(String texto,int limite,String campo) {
        if(texto==null || texto.isBlank() || texto.length()>limite)
            throw new OrganizacionInvalidaException(campo+" obligatorio; máximo "+limite+" caracteres");
        return texto.strip();
    }
    private void unico(String n,Integer id) {
        if(repo.listarDepartamentos().stream().anyMatch(d->!Objects.equals(id,d.id()) && d.nombre().strip().equalsIgnoreCase(n)))
            throw new OrganizacionConflictoException("Ya existe un departamento con ese nombre, incluso si está inactivo");
    }
    private void unicoContacto(Integer padre,String tipo,String valor,Integer id) {
        if(repo.listarContactos(padre).stream().anyMatch(c->!Objects.equals(id,c.id()) && c.tipo().strip().equalsIgnoreCase(tipo) && c.valor().strip().equals(valor)))
            throw new OrganizacionConflictoException("Ese tipo y valor de contacto ya existen en este departamento");
    }
    private void version(Long actual,Long enviada) {
        if(enviada==null || enviada<0) throw new OrganizacionInvalidaException("Versión de edición obligatoria");
        if(!Objects.equals(actual,enviada)) throw new OrganizacionConflictoException("El registro cambió en otra sesión. Recarga y revisa los cambios antes de guardar");
    }
    private void disponible(DepartamentoAdministrativo d) {
        if(!d.activo()) throw new OrganizacionConflictoException("Reactiva el departamento antes de crear o reactivar contactos");
    }
}
