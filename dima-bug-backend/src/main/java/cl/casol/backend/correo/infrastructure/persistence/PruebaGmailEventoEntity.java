package cl.casol.backend.correo.infrastructure.persistence;
import jakarta.persistence.*;import java.time.Instant;
@Entity @Table(name="gm_prueba_evento") @org.hibernate.annotations.Immutable
public class PruebaGmailEventoEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="prueba_id",nullable=false,length=36) String prueba;
    @Column(name="actor_id",nullable=false) Integer actor;
    @Column(nullable=false,length=120) String destinatario;
    @Column(nullable=false,length=20) String estado;
    @Column(nullable=false) Instant fecha;
    @Column(name="mensaje_id",length=100) String mensajeId;
    @Column(length=40) String resultado;
    protected PruebaGmailEventoEntity(){}
}
