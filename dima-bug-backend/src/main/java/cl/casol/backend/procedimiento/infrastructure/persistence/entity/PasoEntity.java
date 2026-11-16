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
    @Enumerated(EnumType.STRING) @Column(name="paso_modo_ejecucion",nullable=false,length=30)
    private cl.casol.backend.procedimiento.domain.ModoEjecucionPaso modoEjecucion=cl.casol.backend.procedimiento.domain.ModoEjecucionPaso.MANUAL;
    @Column(name="paso_mensaje_evidencia",length=2000) private String mensajeEvidencia;
    @Column(name="paso_configuracion_json",columnDefinition="TEXT") private String configuracionJson;
    @Column(name="paso_config_historial",columnDefinition="TEXT") private String historial;
    protected PasoEntity() { }
    public PasoEntity(Integer id, Integer procedimientoId, Integer orden, String instruccion, boolean esCritico) {
        this.id=id; this.procedimientoId=procedimientoId; this.orden=orden;
        this.instruccion=instruccion; this.esCritico=esCritico;
    }
    public Integer getId(){return id;} public Integer getProcedimientoId(){return procedimientoId;}
    public Integer getOrden(){return orden;} public String getInstruccion(){return instruccion;}
    public boolean isEsCritico(){return esCritico;}
    public PasoEntity(Integer id,Integer procedimientoId,Integer orden,String instruccion,boolean esCritico,
            cl.casol.backend.procedimiento.domain.ModoEjecucionPaso modo,String mensaje) {
        this(id,procedimientoId,orden,instruccion,esCritico);modoEjecucion=modo;mensajeEvidencia=mensaje;
    }
    public cl.casol.backend.procedimiento.domain.ModoEjecucionPaso getModoEjecucion(){return modoEjecucion;}
    public String getMensajeEvidencia(){return mensajeEvidencia;}
    public void configurar(cl.casol.backend.procedimiento.domain.ConfiguracionPaso c){configuracionJson=cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.escribir(c);}
    public cl.casol.backend.procedimiento.domain.ConfiguracionPaso getConfiguracion(){return cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.leer(configuracionJson);}
    public String getHistorial(){return historial;}
    public void setHistorial(String h){historial=h;}
}
