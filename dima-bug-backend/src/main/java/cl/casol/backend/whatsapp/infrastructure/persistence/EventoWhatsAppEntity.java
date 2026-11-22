package cl.casol.backend.whatsapp.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.time.Instant;

/** Registrada exclusivamente por whatsapp.orm.xml en el perfil durable. */
@Table(name="wa_evento")
public class EventoWhatsAppEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id", nullable=false)
    public Long id;
    @Column(name="clave_hmac", nullable=false, length=32)
    @JdbcTypeCode(Types.BINARY)
    public byte[] claveHmac;
    @Column(name="contenido_hmac", nullable=false, length=32)
    @JdbcTypeCode(Types.BINARY)
    public byte[] contenidoHmac;
    @Column(name="conversacion_id")
    public Long conversacionId;
    @Column(name="secuencia")
    public Long secuencia;
    @Column(name="tipo", nullable=false, length=24)
    public String tipo;
    @Column(name="estado", nullable=false, length=24)
    public String estado;
    @Column(name="esquema", nullable=false)
    public Integer esquema;
    @Column(name="contenido_cifrado", columnDefinition="MEDIUMBLOB") @Lob
    public byte[] contenidoCifrado;
    @Column(name="emitido_en")
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant emitidoEn;
    @Column(name="recibido_en", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant recibidoEn;
    @Column(name="actualizado_en", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant actualizadoEn;
    @Column(name="terminado_en")
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant terminadoEn;
    @Column(name="vence_en", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant venceEn;
    @Column(name="retener_hasta", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant retenerHasta;
    @Column(name="intentos", nullable=false)
    public Integer intentos;
    @Column(name="proximo_intento", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant proximoIntento;
    @Column(name="reserva_token", length=36) @JdbcTypeCode(Types.CHAR)
    public String reservaToken;
    @Column(name="reserva_hasta")
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant reservaHasta;
    @Column(name="cercado", nullable=false)
    public Long cercado;
    @Column(name="ultimo_error_codigo", length=40)
    public String ultimoErrorCodigo;
    @Column(name="resultado_codigo", length=40)
    public String resultadoCodigo;
}
