package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.DepartamentoRepository;
import cl.casol.backend.identidad.application.port.out.ResponsableRepository;
import cl.casol.backend.identidad.domain.Departamento;
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
    private ConsultarOrganizacionService service;

    @BeforeEach void setUp() {
        departamentos = mock(DepartamentoRepository.class);
        responsables = mock(ResponsableRepository.class);
        service = new ConsultarOrganizacionService(departamentos, responsables);
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
}
