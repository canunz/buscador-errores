package cl.casol.backend.ejecucion.infrastructure.persistence.entity;

import cl.casol.backend.ejecucion.domain.ContratoPaso;
import cl.casol.backend.procedimiento.domain.ModoEjecucionPaso;
import jakarta.persistence.*;

@Embeddable
public class ContratoPasoEmbebido {
    @Column(name="contrato_version") private Integer version;
    @Column(name="contrato_orden") private Integer orden;
    @Column(name="contrato_instruccion", columnDefinition="TEXT") private String instruccion;
    @Column(name="contrato_critico") private Boolean critico;
    @Enumerated(EnumType.STRING) @Column(name="contrato_modo", length=30) private ModoEjecucionPaso modo;
    @Column(name="contrato_configuracion_json",columnDefinition="TEXT") private String configuracionJson;
    protected ContratoPasoEmbebido() { }
    public ContratoPasoEmbebido(ContratoPaso c) {
        version=c.version(); orden=c.orden(); instruccion=c.instruccion(); critico=c.esCritico(); modo=c.modo();configuracionJson=cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.escribir(c.configuracion());
    }
    public ContratoPaso toDomain() {
        return version == null ? null : new ContratoPaso(version, orden, instruccion, critico, modo,cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.leer(configuracionJson));
    }
}
