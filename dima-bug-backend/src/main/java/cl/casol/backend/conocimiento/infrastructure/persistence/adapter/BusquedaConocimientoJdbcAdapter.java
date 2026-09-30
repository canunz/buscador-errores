package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.BusquedaConocimientoRepository;
import cl.casol.backend.conocimiento.application.port.out.DocumentoBusquedaConocimientoRepository;
import cl.casol.backend.conocimiento.domain.DocumentoBusquedaConocimiento;
import cl.casol.backend.conocimiento.domain.ResultadoBusquedaConocimiento;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class BusquedaConocimientoJdbcAdapter
        implements DocumentoBusquedaConocimientoRepository, BusquedaConocimientoRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final EntityManager entityManager;

    public BusquedaConocimientoJdbcAdapter(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc,
            EntityManager entityManager) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<DocumentoBusquedaConocimiento> obtener(Integer conocimientoId) {
        entityManager.flush();
        List<List<String>> base = jdbc.query("""
                SELECT c.conocimiento_titulo, c.conocimiento_descripcion, c.conocimiento_comentario,
                       h.hardware_nombre, h.hardware_sistema_operativo,
                       s.sistema_nombre, s.sistema_descripcion, m.modulo_nombre, f.frecuencia_nombre
                  FROM co_conocimiento c
                  LEFT JOIN cl_hardware h ON h.hardware_id = c.hardware_id
                  LEFT JOIN cl_sistema s ON s.sistema_id = c.sistema_id
                  LEFT JOIN cl_modulo m ON m.modulo_id = c.modulo_id
                  LEFT JOIN cl_frecuencia f ON f.frecuencia_id = c.frecuencia_id
                 WHERE c.conocimiento_id = ?
                """, (rs, row) -> valores(rs, 9), conocimientoId);
        if (base.isEmpty()) return Optional.empty();

        return Optional.of(new DocumentoBusquedaConocimiento(
                base.getFirst(),
                columna("SELECT sintoma_descripcion FROM co_sintoma WHERE conocimiento_id = ? ORDER BY sintoma_orden, sintoma_id", conocimientoId),
                columna("SELECT causa_descripcion FROM co_causa WHERE conocimiento_id = ? ORDER BY causa_orden, causa_id", conocimientoId),
                columnas("""
                        SELECT p.prueba_descripcion, p.prueba_resultado_esperado
                          FROM co_conocimiento_prueba cp
                          JOIN co_prueba p ON p.prueba_id = cp.prueba_id AND p.prueba_estado = 1
                         WHERE cp.conocimiento_id = ?
                         ORDER BY cp.prueba_orden, p.prueba_id
                        """, 2, conocimientoId),
                columna("SELECT solucion_descripcion FROM co_solucion WHERE conocimiento_id = ? ORDER BY solucion_orden, solucion_id", conocimientoId),
                columnas("""
                        SELECT CASE WHEN r.responsable_estado = 1 THEN r.responsable_nombre END,
                               CASE WHEN r.responsable_estado = 1 THEN r.responsable_cargo END,
                               CASE WHEN d.departamento_estado = 1 THEN d.departamento_nombre END
                          FROM co_solucion so
                          JOIN co_solucion_asignacion a ON a.solucion_id = so.solucion_id
                          LEFT JOIN u_responsable r ON r.responsable_id = a.responsable_id
                          LEFT JOIN u_departamento d ON d.departamento_id = a.departamento_id
                         WHERE so.conocimiento_id = ?
                         ORDER BY so.solucion_orden, so.solucion_id, a.asignacion_principal DESC, a.asignacion_id
                        """, 3, conocimientoId),
                columna("""
                        SELECT material_nombre FROM co_material_apoyo
                         WHERE conocimiento_id = ? AND paso_id IS NULL ORDER BY material_id
                        """, conocimientoId)));
    }

    @Override
    public void guardar(Integer conocimientoId, String contenido) {
        jdbc.update("""
                INSERT INTO co_conocimiento_busqueda (conocimiento_id, contenido_busqueda)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE contenido_busqueda = VALUES(contenido_busqueda)
                """, conocimientoId, contenido);
    }

    @Override
    public void eliminar(Integer conocimientoId) {
        jdbc.update("DELETE FROM co_conocimiento_busqueda WHERE conocimiento_id = ?", conocimientoId);
    }

    @Override
    public List<ResultadoBusquedaConocimiento> buscar(String texto, Integer hardwareId, Integer sistemaId,
            Integer moduloId, Integer frecuenciaId) {
        boolean conTexto = texto != null;
        StringBuilder sql = new StringBuilder("""
                SELECT c.conocimiento_id, c.conocimiento_titulo,
                       h.hardware_id, h.hardware_nombre,
                       s.sistema_id, s.sistema_nombre,
                       m.modulo_id, m.modulo_nombre,
                       f.frecuencia_id, f.frecuencia_nombre,
                """);
        sql.append(conTexto
                ? " MATCH(cb.contenido_busqueda) AGAINST (:texto IN NATURAL LANGUAGE MODE) AS relevancia "
                : " NULL AS relevancia ");
        sql.append("""
                  FROM co_conocimiento c
                  LEFT JOIN cl_hardware h ON h.hardware_id = c.hardware_id
                  LEFT JOIN cl_sistema s ON s.sistema_id = c.sistema_id
                  LEFT JOIN cl_modulo m ON m.modulo_id = c.modulo_id
                  LEFT JOIN cl_frecuencia f ON f.frecuencia_id = c.frecuencia_id
                """);
        if (conTexto) sql.append(" JOIN co_conocimiento_busqueda cb ON cb.conocimiento_id = c.conocimiento_id ");
        sql.append(" WHERE c.conocimiento_estado = 'PUBLICADO' ");

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (conTexto) {
            sql.append(" AND MATCH(cb.contenido_busqueda) AGAINST (:texto IN NATURAL LANGUAGE MODE) > 0 ");
            params.addValue("texto", texto);
        }
        agregarFiltro(sql, params, "hardwareId", "c.hardware_id", hardwareId);
        agregarFiltro(sql, params, "sistemaId", "c.sistema_id", sistemaId);
        agregarFiltro(sql, params, "moduloId", "c.modulo_id", moduloId);
        agregarFiltro(sql, params, "frecuenciaId", "c.frecuencia_id", frecuenciaId);
        sql.append(conTexto ? " ORDER BY relevancia DESC, c.conocimiento_id " : " ORDER BY c.conocimiento_titulo, c.conocimiento_id ");

        return namedJdbc.query(sql.toString(), params, (rs, row) -> new ResultadoBusquedaConocimiento(
                rs.getObject("conocimiento_id", Integer.class), rs.getString("conocimiento_titulo"),
                rs.getObject("hardware_id", Integer.class), rs.getString("hardware_nombre"),
                rs.getObject("sistema_id", Integer.class), rs.getString("sistema_nombre"),
                rs.getObject("modulo_id", Integer.class), rs.getString("modulo_nombre"),
                rs.getObject("frecuencia_id", Integer.class), rs.getString("frecuencia_nombre"),
                rs.getObject("relevancia", Double.class)));
    }

    private void agregarFiltro(StringBuilder sql, MapSqlParameterSource params, String nombre,
            String columna, Integer valor) {
        if (valor != null) {
            sql.append(" AND ").append(columna).append(" = :").append(nombre);
            params.addValue(nombre, valor);
        }
    }

    private List<String> columna(String sql, Integer conocimientoId) {
        return jdbc.query(sql, (rs, row) -> rs.getString(1), conocimientoId);
    }

    private List<String> columnas(String sql, int cantidad, Integer conocimientoId) {
        return jdbc.query(sql, (rs, row) -> valores(rs, cantidad), conocimientoId).stream()
                .flatMap(List::stream).toList();
    }

    private List<String> valores(java.sql.ResultSet rs, int cantidad) throws java.sql.SQLException {
        List<String> valores = new ArrayList<>(cantidad);
        for (int i = 1; i <= cantidad; i++) valores.add(rs.getString(i));
        return valores;
    }
}
