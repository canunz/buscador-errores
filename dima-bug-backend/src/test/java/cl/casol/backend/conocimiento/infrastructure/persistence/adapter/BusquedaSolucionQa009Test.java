package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusquedaSolucionQa009Test {
    @Test void indiceExcluyeSolucionesYAsignacionesInactivasEIncluyeTitulo() {
        List<String> consultas=new ArrayList<>();
        JdbcTemplate jdbc=mock(JdbcTemplate.class, invocation -> {
            if (invocation.getMethod().getName().equals("query")) {
                String sql=invocation.getArgument(0); consultas.add(sql);
                return sql.contains("SELECT c.conocimiento_titulo") ? List.of(List.of("Caso")) : List.of();
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        var adapter=new BusquedaConocimientoJdbcAdapter(jdbc,mock(NamedParameterJdbcTemplate.class),mock(EntityManager.class));
        assertTrue(adapter.obtener(4).isPresent());
        String soluciones=consultas.stream().filter(s -> s.contains("SELECT solucion_titulo")).findFirst().orElseThrow();
        assertTrue(soluciones.contains("solucion_descripcion")); assertTrue(soluciones.contains("solucion_estado = 1"));
        String asignaciones=consultas.stream().filter(s -> s.contains("JOIN co_solucion_asignacion")).findFirst().orElseThrow();
        assertTrue(asignaciones.contains("so.solucion_estado = 1"));
    }
}
