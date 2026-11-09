package cl.casol.backend.ejecucion.infrastructure.persistence.entity;
import cl.casol.backend.ejecucion.domain.EventoAutomatizacion;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="se_evento_ejecucion") @org.hibernate.annotations.Immutable
public class EventoAutomatizacionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="ejecucion_id",nullable=false) private Integer ejecucionId;
    @Column(name="solicitud_id",length=36) private String solicitudId;
    @Column(nullable=false,length=60) private String tipo;
    @Column(name="actor_id") private Integer actorId;
    @Column(nullable=false,length=1000) private String detalle;
    @Column(nullable=false) private Instant fecha;
    protected EventoAutomatizacionEntity() { }
    public EventoAutomatizacionEntity(EventoAutomatizacion e) {id=e.id();ejecucionId=e.ejecucionId();solicitudId=e.solicitudId();tipo=e.tipo();actorId=e.actorId();detalle=e.detalle();fecha=e.fecha();}
    public EventoAutomatizacion toDomain() {return new EventoAutomatizacion(id,ejecucionId,solicitudId,tipo,actorId,detalle,fecha);}
}
