package cl.casol.backend.conocimiento.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "co_prueba")
public class PruebaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prueba_id")
    private Integer id;

    @Column(name = "prueba_descripcion", nullable = false, unique = true, length = 500)
    private String descripcion;

    @Column(name = "prueba_resultado_esperado", length = 500)
    private String resultadoEsperado;

    @Column(name = "prueba_estado", nullable = false)
    private boolean activa;

    protected PruebaEntity() { }

    public Integer getId() { return id; }
    public String getDescripcion() { return descripcion; }
    public String getResultadoEsperado() { return resultadoEsperado; }
    public boolean isActiva() { return activa; }
}
