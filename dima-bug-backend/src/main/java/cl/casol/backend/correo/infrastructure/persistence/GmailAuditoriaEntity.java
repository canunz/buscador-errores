package cl.casol.backend.correo.infrastructure.persistence;
import jakarta.persistence.*;
import java.time.Instant;
/**
 * Entidad JPA de gmail auditoría. Representa una fila de la tabla gm_auditoria_oauth.
 * Se usa solo en la capa de persistencia; el dominio trabaja con su propio modelo.
 */
@Entity @Table(name="gm_auditoria_oauth") @org.hibernate.annotations.Immutable
public class GmailAuditoriaEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="actor_id") Integer actor;
    @Column(nullable=false,length=40) String tipo;
    @Column(nullable=false) Instant fecha;
    protected GmailAuditoriaEntity(){}
}
