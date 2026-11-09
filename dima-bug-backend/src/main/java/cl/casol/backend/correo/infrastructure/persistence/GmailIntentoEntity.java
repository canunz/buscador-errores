package cl.casol.backend.correo.infrastructure.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="gm_estado_oauth")
public class GmailIntentoEntity {
    @Id @Column(name="estado_hash",length=64) String estado;
    @Column(name="navegador_hash",nullable=false,length=64) String navegador;
    @Column(name="pkce_cifrado",nullable=false,columnDefinition="TEXT") String verificador;
    @Column(name="actor_id",nullable=false) Integer actor;
    @Column(name="expira_en",nullable=false) Instant expira;
    @Column(name="version_cuenta",nullable=false) long versionCuenta;
    @Column(nullable=false) boolean consumido;
    protected GmailIntentoEntity(){}
}
