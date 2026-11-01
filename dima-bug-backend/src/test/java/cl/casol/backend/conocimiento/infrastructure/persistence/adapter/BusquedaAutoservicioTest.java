package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.SolucionJpaRepository;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BusquedaAutoservicioTest {
    @Test @SuppressWarnings("unchecked")
    void sqlExcluyeBorradoresPrivadasInactivasYContenidoInterno() {
        var named = mock(NamedParameterJdbcTemplate.class);
        when(named.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        var adapter = new BusquedaConocimientoJdbcAdapter(mock(JdbcTemplate.class), named, mock(EntityManager.class));
        adapter.buscarAutoservicio("Impresora  PAPEL");
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(named).query(sql.capture(), params.capture(), any(RowMapper.class));
        String query = sql.getValue();
        assertTrue(query.contains("c.conocimiento_estado = 'PUBLICADO'"));
        assertTrue(query.contains("so.solucion_estado = 1"));
        assertTrue(query.contains("so.solucion_autoservicio = 1"));
        assertTrue(query.contains("LIKE :termino0") && query.contains("LIKE :termino1"));
        for (String interno : List.of("co_sintoma", "co_causa", "co_prueba", "conocimiento_comentario", "contenido_busqueda")) {
            assertFalse(query.contains(interno));
        }
        assertEquals("%impresora%", params.getValue().getValue("termino0"));
        assertEquals("%papel%", params.getValue().getValue("termino1"));
    }

    @Test void lecturaDeSolucionesExigeAmbosIndicadores() {
        var jpa = mock(SolucionJpaRepository.class);
        when(jpa.findByConocimientoIdAndActivoTrueAndAutoservicioTrueOrderByOrdenAsc(4)).thenReturn(List.of());
        assertTrue(new SolucionRepositoryAdapter(jpa).buscarActivasAutoservicioPorConocimiento(4).isEmpty());
        verify(jpa).findByConocimientoIdAndActivoTrueAndAutoservicioTrueOrderByOrdenAsc(4);
        verifyNoMoreInteractions(jpa);
    }
}
