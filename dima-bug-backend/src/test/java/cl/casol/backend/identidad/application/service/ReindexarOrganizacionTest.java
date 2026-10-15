package cl.casol.backend.identidad.application.service;
import cl.casol.backend.identidad.application.port.out.ReferenciasOrganizacionRepository;
import cl.casol.backend.conocimiento.application.service.IndexarConocimientoService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReindexarOrganizacionTest {
 @Test void reindexaConocimientosReferenciados(){var refs=mock(ReferenciasOrganizacionRepository.class);var index=mock(IndexarConocimientoService.class);when(refs.conocimientos(1)).thenReturn(List.of(7,9));new ReindexarOrganizacionService(refs,index).actualizar(1);verify(index).indexar(7);verify(index).indexar(9);verifyNoMoreInteractions(index);}
 @Test void falloPostCommitNoRevierteCrud(){var worker=mock(ReindexarOrganizacionService.class);doThrow(new IllegalStateException("Fallo simulado")).when(worker).actualizar(1);assertDoesNotThrow(()->new ReindexarOrganizacionListener(worker).actualizar(new OrganizacionActualizada(1)));}
 @Test void listenerDelegaTrasCommit(){var worker=mock(ReindexarOrganizacionService.class);new ReindexarOrganizacionListener(worker).actualizar(new OrganizacionActualizada(1));verify(worker).actualizar(1);}
 @Test void transaccionesSeparanIndiceDeLocksOrganizacion()throws Exception{assertEquals(TransactionPhase.AFTER_COMMIT,ReindexarOrganizacionListener.class.getMethod("actualizar",OrganizacionActualizada.class).getAnnotation(TransactionalEventListener.class).phase());assertEquals(Propagation.REQUIRES_NEW,ReindexarOrganizacionService.class.getMethod("actualizar",Integer.class).getAnnotation(Transactional.class).propagation());}
}