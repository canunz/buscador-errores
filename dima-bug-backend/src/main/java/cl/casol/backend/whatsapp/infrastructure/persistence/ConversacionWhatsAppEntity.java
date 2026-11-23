package cl.casol.backend.whatsapp.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.time.Instant;

/** Registrada exclusivamente por whatsapp.orm.xml en el perfil durable. */
@Table(name="wa_conversacion")
public class ConversacionWhatsAppEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id", nullable=false)
    public Long id;
    @Column(name="clave_hmac", nullable=false, length=32)
    @JdbcTypeCode(Types.BINARY)
    public byte[] claveHmac;
    @Column(name="estado", nullable=false, length=24)
    public String estado;
    @Column(name="revision_estado", nullable=false)
    public Long revisionEstado;
    @Column(name="secuencia_recibida", nullable=false)
    public Long secuenciaRecibida;
    @Column(name="referencia", length=36) @JdbcTypeCode(Types.CHAR)
    public String referencia;
    @Column(name="estado_cifrado", columnDefinition="MEDIUMBLOB") @Lob
    public byte[] estadoCifrado;
    @Column(name="destino_cifrado", columnDefinition="BLOB") @Lob
    public byte[] destinoCifrado;
    @Column(name="propuesta_expira")
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant propuestaExpira;
    @Column(name="ultima_imagen_evento")
    public Long ultimaImagenEvento;
    @Column(name="ultima_imagen_emitida")
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant ultimaImagenEmitida;
    @Column(name="reserva_token", length=36) @JdbcTypeCode(Types.CHAR)
    public String reservaToken;
    @Column(name="reserva_hasta")
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant reservaHasta;
    @Column(name="cercado", nullable=false)
    public Long cercado;
    @Column(name="ventana_ingreso")
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant ventanaIngreso;
    @Column(name="ingresados", nullable=false)
    public Integer ingresados;
    @Column(name="creado_en", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant creadoEn;
    @Column(name="actualizado_en", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant actualizadoEn;
    @Column(name="retener_hasta", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant retenerHasta;
}
