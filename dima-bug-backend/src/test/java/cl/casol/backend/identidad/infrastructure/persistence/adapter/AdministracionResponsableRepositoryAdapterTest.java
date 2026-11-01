package cl.casol.backend.identidad.infrastructure.persistence.adapter;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.*;
import cl.casol.backend.identidad.infrastructure.persistence.entity.ResponsableEntity;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdministracionResponsableRepositoryAdapterTest {
    final EntityManager em=mock(EntityManager.class);
    final AdministracionResponsableRepositoryAdapter adapter=new AdministracionResponsableRepositoryAdapter(em);
    @Test void bloqueoRefrescaAntesDeCompararEstado(){var entity=new ResponsableEntity(1,"Ana",null,null,true);when(em.find(ResponsableEntity.class,4,LockModeType.PESSIMISTIC_WRITE)).thenReturn(entity);assertTrue(adapter.bloquear(4).isPresent());verify(em).refresh(entity,LockModeType.PESSIMISTIC_WRITE);}
    @Test void guardarNoPermiteTraslado(){when(em.find(ResponsableEntity.class,4,LockModeType.PESSIMISTIC_WRITE)).thenReturn(new ResponsableEntity(1,"Ana",null,null,true));assertThrows(OrganizacionConflictoException.class,()->adapter.guardar(new Responsable(4,2,"Ana",null,null,true)));verify(em,never()).flush();}
    @Test void edicionConservaPertenenciaYNoUsaMerge(){var entity=new ResponsableEntity(1,"Ana",null,null,true);when(em.find(ResponsableEntity.class,4,LockModeType.PESSIMISTIC_WRITE)).thenReturn(entity);var r=adapter.guardar(new Responsable(4,1,"Bea","Cargo","00123",false));assertEquals(1,r.departamentoId());assertFalse(r.activo());verify(em).flush();verify(em,never()).merge(any());}
}
