package cl.casol.backend.catalogo.application.service;

import cl.casol.backend.catalogo.application.port.out.ModuloRepository;
import cl.casol.backend.catalogo.application.port.out.SistemaRepository;
import cl.casol.backend.catalogo.domain.Modulo;
import cl.casol.backend.catalogo.domain.Sistema;
import cl.casol.backend.catalogo.domain.exception.CatalogoNoEncontradoException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ConsultarSistemasServiceTest {
    private final SistemaRepository sistemas = mock(SistemaRepository.class);
    private final ModuloRepository modulos = mock(ModuloRepository.class);
    private final ConsultarSistemasService service = new ConsultarSistemasService(sistemas, modulos);

    @Test
    void listaModulosSoloCuandoElSistemaExisteYEstaActivo() {
        when(sistemas.buscarActivoPorId(1)).thenReturn(Optional.of(new Sistema(1, "Jadima", null, true)));
        when(modulos.buscarActivosPorSistema(1)).thenReturn(List.of(new Modulo(2, 1, "Ventas", true)));

        assertEquals("Ventas", service.listarModulos(1).getFirst().nombre());
        verify(modulos).buscarActivosPorSistema(1);
    }

    @Test
    void sistemaInexistenteProduce404DeDominioYNoConsultaModulos() {
        when(sistemas.buscarActivoPorId(99)).thenReturn(Optional.empty());

        assertThrows(CatalogoNoEncontradoException.class, () -> service.listarModulos(99));
        verifyNoInteractions(modulos);
    }
}
