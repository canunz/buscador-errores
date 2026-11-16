package cl.casol.backend.seguimiento.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="se_resultado")
public class ResultadoSolucionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="resultado_id") private Integer id;
    @Column(name="solucion_id",nullable=false) private Integer solucionId;
    @Column(name="usuario_id",nullable=false) private Integer usuarioId;
    @Column(name="resultado_funciono",nullable=false) private boolean funciono;
    @Column(name="resultado_comentario",length=300) private String comentario;
    @Column(name="resultado_fecha",nullable=false) private LocalDateTime fecha;
    protected ResultadoSolucionEntity() { }
    public ResultadoSolucionEntity(Integer id,Integer solucionId,Integer usuarioId,boolean funciono,
            String comentario,LocalDateTime fecha){this.id=id;this.solucionId=solucionId;this.usuarioId=usuarioId;
        this.funciono=funciono;this.comentario=comentario;this.fecha=fecha;}
    public Integer getId(){return id;} public Integer getSolucionId(){return solucionId;}
    public Integer getUsuarioId(){return usuarioId;} public boolean isFunciono(){return funciono;}
    public String getComentario(){return comentario;} public LocalDateTime getFecha(){return fecha;}
}
