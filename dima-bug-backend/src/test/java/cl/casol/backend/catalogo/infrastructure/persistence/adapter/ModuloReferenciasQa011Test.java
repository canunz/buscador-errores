package cl.casol.backend.catalogo.infrastructure.persistence.adapter;
import cl.casol.backend.catalogo.infrastructure.persistence.repository.*;
import cl.casol.backend.conocimiento.application.service.IndexarConocimientoService;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModuloReferenciasQa011Test {
    @Test void conteoIncluyeConocimientosEliminadosSinFiltroDeEstado() {
        EntityManager em=mock(EntityManager.class);
        @SuppressWarnings("unchecked") TypedQuery<Long> query=mock(TypedQuery.class);
        when(em.createQuery(anyString(),eq(Long.class))).thenReturn(query);
        when(query.setParameter("id",1)).thenReturn(query); when(query.getSingleResult()).thenReturn(1L);
        var adapter=new CatalogoAdministrativoRepositoryAdapter(em,mock(HardwareJpaRepository.class),mock(SistemaJpaRepository.class),
            mock(ModuloJpaRepository.class),mock(FrecuenciaJpaRepository.class),mock(IndexarConocimientoService.class));
        assertTrue(adapter.moduloReferenciado(1));
        var sql=org.mockito.ArgumentCaptor.forClass(String.class); verify(em).createQuery(sql.capture(),eq(Long.class));
        assertTrue(sql.getValue().contains("c.modulo.id = :id")); assertFalse(sql.getValue().toLowerCase().contains("estado"));
    }
}
