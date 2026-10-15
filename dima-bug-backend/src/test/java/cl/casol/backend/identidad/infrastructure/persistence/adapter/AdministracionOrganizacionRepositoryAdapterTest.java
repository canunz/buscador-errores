package cl.casol.backend.identidad.infrastructure.persistence.adapter;
import cl.casol.backend.identidad.infrastructure.persistence.entity.*;
import cl.casol.backend.identidad.infrastructure.persistence.repository.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.OrganizacionConflictoException;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
class AdministracionOrganizacionRepositoryAdapterTest {
 final EntityManager em=mock(EntityManager.class);
 final DepartamentoJpaRepository deps=mock(DepartamentoJpaRepository.class);
 final DepartamentoContactoJpaRepository contactos=mock(DepartamentoContactoJpaRepository.class);
 final AdministracionOrganizacionRepositoryAdapter adapter=new AdministracionOrganizacionRepositoryAdapter(em,deps,contactos);
 @Test void listaIncluyeInactivosConVersion(){when(deps.findAll()).thenReturn(List.of(new DepartamentoEntity(1,"TI",false,3L)));assertFalse(adapter.listarDepartamentos().getFirst().activo());assertEquals(3L,adapter.listarDepartamentos().getFirst().version());}
 @Test void bloqueoRefrescaPadreBajoLock(){var d=new DepartamentoEntity(1,"TI",true,3L);when(em.find(DepartamentoEntity.class,1,LockModeType.PESSIMISTIC_WRITE)).thenReturn(d);adapter.bloquearDepartamento(1);verify(em).refresh(d,LockModeType.PESSIMISTIC_WRITE);}
 @Test void fallaSinFilaDeControl(){assertThrows(OrganizacionConflictoException.class,adapter::bloquearEscrituras);}
 @Test void guardaDepartamentoSinPerderEstadoVersion(){when(deps.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));var d=new DepartamentoAdministrativo(1,"TI",false,3L);assertEquals(d,adapter.guardarDepartamento(d));}
 @Test void guardaContactoSinPerderRelacionVersion(){when(contactos.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));var c=new ContactoDepartamentoAdministrativo(4,1,"Anexo","619",false,3L);assertEquals(c,adapter.guardarContacto(c));}
 @Test void modelosDeclaranVersionJpa()throws Exception{assertNotNull(DepartamentoEntity.class.getDeclaredField("version").getAnnotation(Version.class));assertNotNull(DepartamentoContactoEntity.class.getDeclaredField("version").getAnnotation(Version.class));}
}
