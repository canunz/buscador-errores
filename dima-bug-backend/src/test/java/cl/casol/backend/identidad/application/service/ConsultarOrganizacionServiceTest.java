package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.DepartamentoRepository;
import cl.casol.backend.identidad.application.port.out.DepartamentoContactoRepository;
import cl.casol.backend.identidad.application.port.out.ResponsableRepository;
import cl.casol.backend.identidad.domain.Departamento;
import cl.casol.backend.identidad.domain.DepartamentoContacto;
import cl.casol.backend.identidad.domain.exception.DepartamentoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultarOrganizacionServiceTest {
    private DepartamentoRepository departamentos;
    private ResponsableRepository responsables;
    private DepartamentoContactoRepository contactos;
    private ConsultarOrganizacionService service;

    @BeforeEach void setUp() {
        departamentos = mock(DepartamentoRepository.class);
        responsables = mock(ResponsableRepository.class);
        contactos = mock(DepartamentoContactoRepository.class);
        service = new ConsultarOrganizacionService(departamentos, responsables, contactos);
    }

    @Test void departamentoActivoSinResponsablesDevuelveListaVacia() {
        when(departamentos.buscarActivoPorId(1)).thenReturn(Optional.of(new Departamento(1, "TI", true)));
        when(responsables.buscarActivosPorDepartamentoOrdenadosPorNombre(1)).thenReturn(List.of());
        assertTrue(service.listarResponsables(1).isEmpty());
    }

    @Test void departamentoInexistenteOInactivoNoConsultaResponsables() {
        when(departamentos.buscarActivoPorId(9)).thenReturn(Optional.empty());
        assertThrows(DepartamentoNoEncontradoException.class, () -> service.listarResponsables(9));
        verifyNoInteractions(responsables);
    }

    @Test void departamentoActivoConContactosDevuelveSoloLosEntregadosPorRepositorioActivo() {
        when(departamentos.buscarActivoPorId(1)).thenReturn(Optional.of(new Departamento(1, "TI", true)));
        when(contactos.buscarActivosPorDepartamentoOrdenadosPorId(1)).thenReturn(List.of(
                new DepartamentoContacto(1, 1, "Anexo", "618", true),
                new DepartamentoContacto(3, 1, "Teléfono", "652292618", true)));

        List<DepartamentoContacto> resultado = service.listarContactos(1);

        assertEquals(2, resultado.size());
        assertEquals("618", resultado.getFirst().valor());
    }

    @Test void departamentoActivoSinContactosDevuelveListaVacia() {
        when(departamentos.buscarActivoPorId(1)).thenReturn(Optional.of(new Departamento(1, "TI", true)));
        when(contactos.buscarActivosPorDepartamentoOrdenadosPorId(1)).thenReturn(List.of());
        assertTrue(service.listarContactos(1).isEmpty());
    }

    @Test void departamentoInexistenteOInactivoNoConsultaContactos() {
        when(departamentos.buscarActivoPorId(9)).thenReturn(Optional.empty());
        assertThrows(DepartamentoNoEncontradoException.class, () -> service.listarContactos(9));
        verifyNoInteractions(contactos);
    }
}
