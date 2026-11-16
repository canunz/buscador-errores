package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusquedaConocimientoJdbcAdapterTest {

    @Test
    void eliminarSoloBorraLaProyeccionDeBusqueda() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        adapter(jdbc, mock(NamedParameterJdbcTemplate.class)).eliminar(7);
        verify(jdbc).update("DELETE FROM co_conocimiento_busqueda WHERE conocimiento_id = ?", 7);
    }
    @Test
    void upsertSirveParaCrearYActualizarLaMismaFila() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BusquedaConocimientoJdbcAdapter adapter = adapter(jdbc, mock(NamedParameterJdbcTemplate.class));

        adapter.guardar(10, "documento uno");
        adapter.guardar(10, "documento actualizado");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(sql.capture(), eq(10), anyString());
        assertTrue(sql.getAllValues().stream().allMatch(query ->
                query.contains("INSERT INTO co_conocimiento_busqueda")
                        && query.contains("ON DUPLICATE KEY UPDATE")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void busquedaTextualUsaFulltextPublicadoFiltrosAndYRelevanciaDescendente() {
        NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
        when(named.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        BusquedaConocimientoJdbcAdapter adapter = adapter(mock(JdbcTemplate.class), named);

        assertTrue(adapter.buscar("firewall", 1, 6, 2, 3).isEmpty());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(named).query(sql.capture(), params.capture(), any(RowMapper.class));
        String query = sql.getValue();
        assertTrue(query.contains("MATCH(cb.contenido_busqueda) AGAINST (:texto IN NATURAL LANGUAGE MODE)"));
        assertTrue(query.contains("c.conocimiento_estado = 'PUBLICADO'"));
        assertTrue(query.contains("c.hardware_id = :hardwareId"));
        assertTrue(query.contains("c.sistema_id = :sistemaId"));
        assertTrue(query.contains("c.modulo_id = :moduloId"));
        assertTrue(query.contains("c.frecuencia_id = :frecuenciaId"));
        assertTrue(query.contains("ORDER BY relevancia DESC"));
        assertEquals("firewall", params.getValue().getValue("texto"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void busquedaSinTextoNoUsaFulltextYPermiteFiltrosNulos() {
        NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
        when(named.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        BusquedaConocimientoJdbcAdapter adapter = adapter(mock(JdbcTemplate.class), named);

        adapter.buscar(null, null, null, null, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(named).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertFalse(sql.getValue().contains("MATCH("));
        assertTrue(sql.getValue().contains("c.conocimiento_estado = 'PUBLICADO'"));
    }

    private BusquedaConocimientoJdbcAdapter adapter(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        return new BusquedaConocimientoJdbcAdapter(jdbc, named, mock(EntityManager.class));
    }
}
