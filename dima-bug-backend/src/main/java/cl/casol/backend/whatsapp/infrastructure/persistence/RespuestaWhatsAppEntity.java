package cl.casol.backend.whatsapp.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.time.Instant;

/** Registrada exclusivamente por whatsapp.orm.xml en el perfil durable. */
@Table(name="wa_respuesta")
public class RespuestaWhatsAppEntity {
    @Id @Column(name="id", nullable=false, length=36) @JdbcTypeCode(Types.CHAR)
    public String id;
    @Column(name="evento_id", nullable=false)
    public Long eventoId;
    @Column(name="conversacion_id", nullable=false)
    public Long conversacionId;
    @Column(name="estado", nullable=false, length=24)
    public String estado;
    @Column(name="revision_objetivo")
    public Long revisionObjetivo;
    @Column(name="plan_cifrado", columnDefinition="MEDIUMBLOB") @Lob
    public byte[] planCifrado;
    @Column(name="preparado_cifrado", columnDefinition="MEDIUMBLOB") @Lob
    public byte[] preparadoCifrado;
    @Column(name="intentos_preparacion", nullable=false)
    public Integer intentosPreparacion;
    @Column(name="intentos_envio", nullable=false)
    public Integer intentosEnvio;
    @Column(name="fase_reintento", length=16)
    public String faseReintento;
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
    @Column(name="proveedor_clave_hmac", length=32)
    @JdbcTypeCode(Types.BINARY)
    public byte[] proveedorClaveHmac;
    @Column(name="estado_entrega", length=24)
    public String estadoEntrega;
    @Column(name="ultimo_error_codigo", length=40)
    public String ultimoErrorCodigo;
    @Column(name="creado_en", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant creadoEn;
    @Column(name="actualizado_en", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant actualizadoEn;
    @Column(name="vence_en", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant venceEn;
    @Column(name="retener_hasta", nullable=false)
    @JdbcTypeCode(Types.TIMESTAMP)
    public Instant retenerHasta;
}
