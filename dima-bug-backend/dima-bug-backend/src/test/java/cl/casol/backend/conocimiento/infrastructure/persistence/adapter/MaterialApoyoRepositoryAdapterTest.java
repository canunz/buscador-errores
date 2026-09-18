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
    @Test void consultaExcluyeMaterialesDePaso() {
        MaterialApoyoJpaRepository repository=mock(MaterialApoyoJpaRepository.class);
        when(repository.findByConocimientoIdAndPasoIdIsNullOrderByIdAsc(4)).thenReturn(List.of());
        new MaterialApoyoRepositoryAdapter(repository).buscarDirectosPorConocimiento(4);
        verify(repository).findByConocimientoIdAndPasoIdIsNullOrderByIdAsc(4);
    }
    @Test void mapperDePersistenciaSiempreFuerzaPasoNulo() {
        MaterialApoyo material=new MaterialApoyo(1,4,99,"Manual",TipoMaterial.PDF,"url");
        MaterialApoyoEntity entity=MaterialApoyoMapper.toEntity(material);
        assertEquals(4,entity.getConocimientoId()); assertNull(entity.getPasoId());
    }
}
