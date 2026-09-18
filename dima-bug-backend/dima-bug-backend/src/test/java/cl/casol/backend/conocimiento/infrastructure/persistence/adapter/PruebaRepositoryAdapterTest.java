package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.infrastructure.persistence.repository.ConocimientoPruebaJpaRepository;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.PruebaJpaRepository;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class PruebaRepositoryAdapterTest {

    @Test void catalogoUsaConsultaQueFiltraActivasYOrdenaPorDescripcion() {
        PruebaJpaRepository repository = mock(PruebaJpaRepository.class);
        when(repository.findByActivaTrueOrderByDescripcionAsc()).thenReturn(java.util.List.of());
        new PruebaRepositoryAdapter(repository).buscarActivasOrdenadasPorDescripcion();
        verify(repository).findByActivaTrueOrderByDescripcionAsc();
    }

    @Test void asociacionesUsanConsultaQueFiltraPruebasActivasYOrdenaPorOrden() {
        ConocimientoPruebaJpaRepository repository = mock(ConocimientoPruebaJpaRepository.class);
        PruebaJpaRepository pruebas = mock(PruebaJpaRepository.class);
        when(repository.findByIdConocimientoIdAndPruebaActivaTrueOrderByOrdenAsc(4))
                .thenReturn(java.util.List.of());
        new ConocimientoPruebaRepositoryAdapter(repository, pruebas)
                .buscarActivasPorConocimientoOrdenadas(4);
        verify(repository).findByIdConocimientoIdAndPruebaActivaTrueOrderByOrdenAsc(4);
    }
}
