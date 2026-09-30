package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.domain.DepartamentoContacto;
import cl.casol.backend.identidad.infrastructure.persistence.entity.DepartamentoContactoEntity;
import cl.casol.backend.identidad.infrastructure.persistence.repository.DepartamentoContactoJpaRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DepartamentoContactoRepositoryAdapterTest {

    @Test
    void consultaSoloContactosActivosYMapeaAlDominio() {
        DepartamentoContactoJpaRepository jpaRepository = mock(DepartamentoContactoJpaRepository.class);
        DepartamentoContactoEntity entity = mock(DepartamentoContactoEntity.class);
        when(entity.getId()).thenReturn(1);
        when(entity.getDepartamentoId()).thenReturn(7);
        when(entity.getTipo()).thenReturn("Anexo");
        when(entity.getValor()).thenReturn("618");
        when(entity.isActivo()).thenReturn(true);
        when(jpaRepository.findByDepartamentoIdAndActivoTrueOrderByIdAsc(7)).thenReturn(List.of(entity));

        List<DepartamentoContacto> resultado =
                new DepartamentoContactoRepositoryAdapter(jpaRepository)
                        .buscarActivosPorDepartamentoOrdenadosPorId(7);

        assertEquals(List.of(new DepartamentoContacto(1, 7, "Anexo", "618", true)), resultado);
        verify(jpaRepository).findByDepartamentoIdAndActivoTrueOrderByIdAsc(7);
        verifyNoMoreInteractions(jpaRepository);
    }
}
