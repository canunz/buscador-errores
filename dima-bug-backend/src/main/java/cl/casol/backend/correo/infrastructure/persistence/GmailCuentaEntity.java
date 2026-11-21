package cl.casol.backend.correo.infrastructure.persistence;
import jakarta.persistence.*;
import java.time.Instant;
/**
 * Entidad JPA de gmail cuenta. Representa una fila de la tabla gm_cuenta_oauth.
 * Se usa solo en la capa de persistencia; el dominio trabaja con su propio modelo.
 */
@Entity @Table(name="gm_cuenta_oauth")
public class GmailCuentaEntity {
    @Id Integer id;
    @Version Long version;
    @Column(nullable=false,length=20) String estado="DESCONECTADA";
    @Column(name="access_token_cifrado",columnDefinition="TEXT") String acceso;
    @Column(name="refresh_token_cifrado",columnDefinition="TEXT") String refresco;
    @Column(name="revocacion_cifrada",columnDefinition="TEXT") String revocacion;
    @Column(name="expira_en") Instant expira;
    @Column(name="autorizado_por") Integer actor;
    protected GmailCuentaEntity(){}
}
