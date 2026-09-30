package cl.casol.backend.procedimiento.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pr_paso", uniqueConstraints = @UniqueConstraint(columnNames = {"procedimiento_id", "paso_orden"}))
public class PasoEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "paso_id") private Integer id;
    @Column(name = "procedimiento_id", nullable = false) private Integer procedimientoId;
    @Column(name = "paso_orden", nullable = false) private Integer orden;
    @Column(name = "paso_instruccion", nullable = false, columnDefinition = "TEXT") private String instruccion;
    @Column(name = "paso_es_critico", nullable = false) private boolean esCritico;
    protected PasoEntity() { }
    public PasoEntity(Integer id, Integer procedimientoId, Integer orden, String instruccion, boolean esCritico) {
        this.id=id; this.procedimientoId=procedimientoId; this.orden=orden;
        this.instruccion=instruccion; this.esCritico=esCritico;
    }
    public Integer getId(){return id;} public Integer getProcedimientoId(){return procedimientoId;}
    public Integer getOrden(){return orden;} public String getInstruccion(){return instruccion;}
    public boolean isEsCritico(){return esCritico;}
}
