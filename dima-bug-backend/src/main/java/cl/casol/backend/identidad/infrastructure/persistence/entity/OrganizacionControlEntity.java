package cl.casol.backend.identidad.infrastructure.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="u_organizacion_control")
public class OrganizacionControlEntity {
    @Id @Column(name="control_id") private Integer id;
    protected OrganizacionControlEntity() { }
}
