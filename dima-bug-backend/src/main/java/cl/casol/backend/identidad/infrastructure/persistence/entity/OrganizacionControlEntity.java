package cl.casol.backend.identidad.infrastructure.persistence.entity;
import jakarta.persistence.*;
/**
 * Entidad JPA de organización control. Representa una fila de la tabla u_organizacion_control.
 * Se usa solo en la capa de persistencia; el dominio trabaja con su propio modelo.
 */
@Entity @Table(name="u_organizacion_control")
public class OrganizacionControlEntity {
    @Id @Column(name="control_id") private Integer id;
    protected OrganizacionControlEntity() { }
}
