package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.PruebaAdministrativaRepository;
import cl.casol.backend.conocimiento.domain.Prueba;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
@Service @Transactional(readOnly=true)
public class AdministrarPruebasService {
 private final PruebaAdministrativaRepository repo; private final ApplicationEventPublisher eventos;
 public AdministrarPruebasService(PruebaAdministrativaRepository repo,ApplicationEventPublisher eventos){this.repo=repo;this.eventos=eventos;}
 public List<Prueba> listar(){return repo.listar();}
 public Prueba buscar(Integer id){return repo.buscar(id).orElseThrow(()->new PruebaNoEncontradaException(id));}
 @Transactional public Prueba crear(String descripcion,String resultado){String d=descripcion(descripcion);unico(d,null);return repo.guardar(new Prueba(null,d,resultado(resultado),true));}
 @Transactional public Prueba editar(Integer id,String descripcion,String resultado){repo.bloquear(id);var actual=buscar(id);String d=descripcion(descripcion);unico(d,id);var p=repo.guardar(new Prueba(id,d,resultado(resultado),actual.activa()));eventos.publishEvent(new PruebaActualizada(id));return p;}
 @Transactional public Prueba estado(Integer id,boolean activa){repo.bloquear(id);var p=buscar(id);var guardada=p.activa()==activa?p:repo.guardar(new Prueba(id,p.descripcion(),p.resultadoEsperado(),activa));eventos.publishEvent(new PruebaActualizada(id));return guardada;}
 private String descripcion(String valor){if(valor==null||valor.isBlank()||valor.length()>500)throw new PruebaInvalidaException("Descripción obligatoria, máximo 500 caracteres");return valor.strip();}
 private String resultado(String valor){if(valor!=null&&valor.length()>500)throw new PruebaInvalidaException("Resultado esperado: máximo 500 caracteres");return valor==null||valor.isBlank()?null:valor.strip();}
 private void unico(String d,Integer id){if(repo.listar().stream().anyMatch(p->!Objects.equals(p.id(),id)&&p.descripcion().strip().equals(d)))throw new PruebaConflictoException("Ya existe una prueba con esa descripción, incluso si está inactiva");}
}