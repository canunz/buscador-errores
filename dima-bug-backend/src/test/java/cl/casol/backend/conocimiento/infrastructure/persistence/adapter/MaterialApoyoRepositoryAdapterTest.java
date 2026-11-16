package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.MaterialApoyoEntity;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.MaterialApoyoMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.MaterialApoyoJpaRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialApoyoRepositoryAdapterTest {
    @Test void eliminaPorId() {
        MaterialApoyoJpaRepository repository=mock(MaterialApoyoJpaRepository.class);
        new MaterialApoyoRepositoryAdapter(repository).eliminar(3);
        verify(repository).deleteById(3);
        verify(repository, never()).flush();
    }
    @Test void consultaExcluyeMaterialesDePaso() {
        MaterialApoyoJpaRepository repository=mock(MaterialApoyoJpaRepository.class);
        when(repository.findByConocimientoIdAndPasoIdIsNullOrderByIdAsc(4)).thenReturn(List.of());
        new MaterialApoyoRepositoryAdapter(repository).buscarDirectosPorConocimiento(4);
        verify(repository).findByConocimientoIdAndPasoIdIsNullOrderByIdAsc(4);
    }
    @Test void mapperPreservaDestinoConocimiento() {
        MaterialApoyoEntity entity=MaterialApoyoMapper.toEntity(
                new MaterialApoyo(1,4,null,"Manual",TipoMaterial.PDF,"url"));
        assertEquals(4,entity.getConocimientoId()); assertNull(entity.getPasoId());
    }
    @Test void mapperPreservaDestinoPasoConConocimientoNulo() {
        MaterialApoyoEntity entity=MaterialApoyoMapper.toEntity(
                new MaterialApoyo(1,null,99,"Manual",TipoMaterial.PDF,"url"));
        assertNull(entity.getConocimientoId()); assertEquals(99,entity.getPasoId());
    }
    @Test void consultaDePasoExcluyeMaterialesDeConocimiento() {
        MaterialApoyoJpaRepository repository=mock(MaterialApoyoJpaRepository.class);
        when(repository.findByPasoIdAndConocimientoIdIsNullOrderByIdAsc(99)).thenReturn(List.of());
        new MaterialApoyoRepositoryAdapter(repository).buscarPorPaso(99);
        verify(repository).findByPasoIdAndConocimientoIdIsNullOrderByIdAsc(99);
    }
}
