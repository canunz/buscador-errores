package cl.casol.backend.conocimiento.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ConocimientoPruebaId implements Serializable {
    @Column(name = "conocimiento_id", nullable = false)
    private Integer conocimientoId;

    @Column(name = "prueba_id", nullable = false)
    private Integer pruebaId;

    protected ConocimientoPruebaId() { }

    public ConocimientoPruebaId(Integer conocimientoId, Integer pruebaId) {
        this.conocimientoId = conocimientoId;
        this.pruebaId = pruebaId;
    }

    public Integer getConocimientoId() { return conocimientoId; }
    public Integer getPruebaId() { return pruebaId; }

    @Override public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ConocimientoPruebaId that)) return false;
        return Objects.equals(conocimientoId, that.conocimientoId) && Objects.equals(pruebaId, that.pruebaId);
    }

    @Override public int hashCode() {
        return Objects.hash(conocimientoId, pruebaId);
    }
}
