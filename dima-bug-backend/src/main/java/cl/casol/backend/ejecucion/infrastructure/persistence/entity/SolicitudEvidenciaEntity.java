package cl.casol.backend.ejecucion.infrastructure.persistence.entity;

import cl.casol.backend.ejecucion.domain.SolicitudEvidencia;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="se_solicitud_evidencia",uniqueConstraints={
    @UniqueConstraint(name="uk_solicitud_ronda",columnNames={"ejecucion_paso_id","ronda"}),
    @UniqueConstraint(name="uk_solicitud_inicio",columnNames={"actor_id","clave_inicio"})})
public class SolicitudEvidenciaEntity {
    @Id @Column(length=36) private String id;
    @Column(name="ejecucion_id",nullable=false) private Integer ejecucionId;
    @Column(name="ejecucion_paso_id",nullable=false) private Integer ejecucionPasoId;
    @Column(nullable=false) private int ronda;
    @Column(name="sucursal_id") private Integer sucursalId;
    @Column(name="sucursal_codigo",length=30) private String sucursalCodigo;
    @Column(name="sucursal_nombre",length=100) private String sucursalNombre;
    @Column(name="responsable_nombre",length=100) private String responsableNombre;
    @Column(name="responsable_email",nullable=false,length=120) private String responsableEmail;
    @Column(name="actor_id",nullable=false) private Integer actorId;
    @Column(name="procedimiento_id",nullable=false) private Integer procedimientoId;
    @Column(name="clave_inicio",length=36) private String claveInicio;
    @Column(length=2000,nullable=false) private String mensaje;
    @Column(name="recordatorio_1_horas",nullable=false) private int hora1;
    @Column(name="recordatorio_2_horas",nullable=false) private int hora2;
    @Column(name="escalamiento_horas",nullable=false) private int hora3;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private SolicitudEvidencia.Estado estado;
    @Column(name="fecha_envio") private Instant fechaEnvio;
    @Column(name="mensaje_enviado_id",length=100) private String mensajeEnviadoId;
    @Column(name="regla_aplicada",nullable=false) private int reglaAplicada;
    @Column(name="proximo_seguimiento") private Instant proximoSeguimiento;
    @Column(name="mensaje_recibido_id",length=36) private String mensajeRecibidoId;
    @Column(name="huella_recibida",length=64) private String huellaRecibida;
    @Version @Column(nullable=false) private Long version;
    @Column(name="destinatarios_json",columnDefinition="TEXT") private String destinatariosJson;
    @Column(name="configuracion_json",columnDefinition="TEXT") private String configuracionJson;
    protected SolicitudEvidenciaEntity() { }
    public SolicitudEvidenciaEntity(SolicitudEvidencia s) {
        id=s.id();ejecucionId=s.ejecucionId();ejecucionPasoId=s.ejecucionPasoId();ronda=s.ronda();var c=s.contexto();
        sucursalId=c.sucursalId();sucursalCodigo=c.sucursalCodigo();sucursalNombre=c.sucursalNombre();responsableNombre=c.responsableNombre();
        responsableEmail=c.responsableEmail();actorId=c.actorId();procedimientoId=c.procedimientoId();claveInicio=c.claveInicio();
        mensaje=s.politica().mensaje();hora1=s.politica().primerRecordatorioHoras();hora2=s.politica().segundoRecordatorioHoras();hora3=s.politica().escalamientoHoras();
        estado=s.estado();fechaEnvio=s.fechaEnvio();mensajeEnviadoId=s.mensajeEnviadoId();reglaAplicada=s.reglaAplicada();
        proximoSeguimiento=s.proximoSeguimiento();mensajeRecibidoId=s.mensajeRecibidoId();huellaRecibida=s.huellaRecibida();version=s.version();
        destinatariosJson=cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.escribir(c.destinatarios());
        configuracionJson=cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.escribir(s.politica().configuracion());
    }
    public SolicitudEvidencia toDomain() {
        return new SolicitudEvidencia(id,ejecucionId,ejecucionPasoId,ronda,
            new SolicitudEvidencia.Contexto(sucursalId,sucursalCodigo,sucursalNombre,responsableNombre,responsableEmail,actorId,procedimientoId,claveInicio,destinatariosJson==null?java.util.List.of(new cl.casol.backend.shared.domain.DestinatarioPaso(responsableEmail,cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.PRINCIPAL)):cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.destinatarios(destinatariosJson)),
            new SolicitudEvidencia.Politica(mensaje,hora1,hora2,hora3,cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.leer(configuracionJson)),estado,fechaEnvio,mensajeEnviadoId,reglaAplicada,proximoSeguimiento,mensajeRecibidoId,huellaRecibida,version);
    }
}
