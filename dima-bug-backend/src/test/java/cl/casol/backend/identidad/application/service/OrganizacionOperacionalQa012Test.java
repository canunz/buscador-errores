package cl.casol.backend.identidad.application.service;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.DepartamentoNoEncontradoException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrganizacionOperacionalQa012Test {
 final DepartamentoRepository padres=mock(DepartamentoRepository.class);
 final ResponsableRepository responsables=mock(ResponsableRepository.class);
 final DepartamentoContactoRepository contactos=mock(DepartamentoContactoRepository.class);
 final ConsultarOrganizacionService service=new ConsultarOrganizacionService(padres,responsables,contactos);
 @Test void padreInactivoExcluyeContactosOperacionales(){when(padres.buscarActivoPorId(1)).thenReturn(Optional.empty());assertThrows(DepartamentoNoEncontradoException.class,()->service.listarContactos(1));verifyNoInteractions(contactos);}
 @Test void consultaSoloContactosActivosConPadreActivo(){when(padres.buscarActivoPorId(1)).thenReturn(Optional.of(new Departamento(1,"TI",true)));when(contactos.buscarActivosPorDepartamentoOrdenadosPorId(1)).thenReturn(List.of(new DepartamentoContacto(4,1,"Anexo","619",true)));assertEquals(1,service.listarContactos(1).size());verify(contactos).buscarActivosPorDepartamentoOrdenadosPorId(1);}
}