package cl.casol.backend.ejecucion.infrastructure.persistence.entity;
import cl.casol.backend.ejecucion.domain.EvidenciaFotografica;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="se_evidencia",uniqueConstraints=@UniqueConstraint(name="uk_evidencia_categoria",columnNames={"solicitud_id","categoria"}))
public class EvidenciaFotograficaEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="solicitud_id",nullable=false,length=36) private String solicitudId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EvidenciaFotografica.Categoria categoria;
    @Column(nullable=false,length=64) private String sha256;
    @Lob @Column(nullable=false,columnDefinition="MEDIUMBLOB") private byte[] datos;
    @Column(nullable=false) private Instant recibida;
    protected EvidenciaFotograficaEntity() { }
    public EvidenciaFotograficaEntity(EvidenciaFotografica e) { id=e.id();solicitudId=e.solicitudId();categoria=e.categoria();sha256=e.sha256();datos=e.datos();recibida=e.recibida(); }
    public EvidenciaFotografica toDomain() { return new EvidenciaFotografica(id,solicitudId,categoria,sha256,datos,recibida); }
}
