package cl.casol.backend.correo.infrastructure.persistence;
import jakarta.persistence.*;import java.time.Instant;
import cl.casol.backend.correo.application.port.out.PruebaGmailStore.Estado;
@Entity @Table(name="gm_prueba_correo",indexes=@Index(name="ix_gm_prueba_destino_fecha",columnList="destinatario,creada"))
public class PruebaGmailEntity {
    @Id @Column(length=36) String id;
    @Version Long version;
    @Column(name="actor_id",nullable=false) Integer actor;
    @Column(nullable=false,length=120) String remitente;
    @Column(nullable=false,length=120) String destinatario;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) Estado estado;
    @Column(nullable=false) Instant creada;
    Instant intento;Instant finalizada;
    @Column(name="mensaje_id",length=100) String mensajeId;
    @Column(length=40) String resultado;
    protected PruebaGmailEntity(){}
}
