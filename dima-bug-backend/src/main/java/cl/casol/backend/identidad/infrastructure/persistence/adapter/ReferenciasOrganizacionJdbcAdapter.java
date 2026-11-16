package cl.casol.backend.identidad.infrastructure.persistence.adapter;
import cl.casol.backend.identidad.application.port.out.ReferenciasOrganizacionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
public class ReferenciasOrganizacionJdbcAdapter implements ReferenciasOrganizacionRepository {
    private final JdbcTemplate jdbc;
    public ReferenciasOrganizacionJdbcAdapter(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Integer> conocimientos(Integer id) {
        return jdbc.query("""
            select distinct so.conocimiento_id from co_solucion_asignacion a
            join co_solucion so on so.solucion_id=a.solucion_id
            join co_conocimiento c on c.conocimiento_id=so.conocimiento_id
            left join u_responsable r on r.responsable_id=a.responsable_id
            where (a.departamento_id=? or r.departamento_id=?) and c.conocimiento_estado <> 'ELIMINADO'
            order by so.conocimiento_id
            """,(rs,row)->rs.getInt(1),id,id);
    }
}
