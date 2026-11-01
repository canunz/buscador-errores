package cl.casol.backend.identidad.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "u_codigo_acceso")
public class CodigoAccesoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo_id")
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "codigo_hash", nullable = false, length = 100)
    private String codigoHash;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "usado", nullable = false)
    private boolean usado;

    protected CodigoAccesoEntity() {
    }

    public CodigoAccesoEntity(Long id, Integer usuarioId, String codigoHash, LocalDateTime creadoEn,
                              LocalDateTime expiraEn, int intentos, boolean usado) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.codigoHash = codigoHash;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
        this.intentos = intentos;
        this.usado = usado;
    }

    public Long getId() { return id; }
    public Integer getUsuarioId() { return usuarioId; }
    public String getCodigoHash() { return codigoHash; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public int getIntentos() { return intentos; }
    public boolean isUsado() { return usado; }
}
