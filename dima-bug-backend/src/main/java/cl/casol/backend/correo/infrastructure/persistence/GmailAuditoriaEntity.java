package cl.casol.backend.correo.infrastructure.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="gm_auditoria_oauth") @org.hibernate.annotations.Immutable
public class GmailAuditoriaEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="actor_id") Integer actor;
    @Column(nullable=false,length=40) String tipo;
    @Column(nullable=false) Instant fecha;
    protected GmailAuditoriaEntity(){}
}
