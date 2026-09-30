package cl.casol.backend.conocimiento.infrastructure.persistence.entity;

import cl.casol.backend.identidad.infrastructure.persistence.entity.DepartamentoEntity;
import cl.casol.backend.identidad.infrastructure.persistence.entity.ResponsableEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "co_solucion_asignacion")
public class SolucionAsignacionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asignacion_id") private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solucion_id", nullable = false)
    private SolucionEntity solucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id")
    private ResponsableEntity responsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departamento_id")
    private DepartamentoEntity departamento;

    @Column(name = "asignacion_principal", nullable = false)
    private boolean principal;

    protected SolucionAsignacionEntity() { }
    public SolucionAsignacionEntity(Integer id, SolucionEntity solucion, ResponsableEntity responsable,
            DepartamentoEntity departamento, boolean principal) {
        this.id = id; this.solucion = solucion; this.responsable = responsable;
        this.departamento = departamento; this.principal = principal;
    }
    public Integer getId() { return id; }
    public SolucionEntity getSolucion() { return solucion; }
    public ResponsableEntity getResponsable() { return responsable; }
    public DepartamentoEntity getDepartamento() { return departamento; }
    public boolean isPrincipal() { return principal; }
}
