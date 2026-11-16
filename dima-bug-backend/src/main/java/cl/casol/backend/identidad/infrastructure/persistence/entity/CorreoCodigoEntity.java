package cl.casol.backend.identidad.infrastructure.persistence.entity;

import cl.casol.backend.identidad.application.port.out.CorreoCodigoStore.Estado;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "u_correo_codigo", indexes = @Index(name = "idx_correo_codigo_pendiente", columnList = "estado,proximo_intento"))
public class CorreoCodigoEntity {
    @Id @Column(length = 36) public String id;
    @Column(name = "codigo_id", nullable = false, unique = true) public Long codigoId;
    @Lob @Column(name = "contenido_cifrado", columnDefinition = "LONGTEXT") public String contenidoCifrado;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) public Estado estado;
    @Column(nullable = false) public int intentos;
    @Column(nullable = false) public LocalDateTime expira;
    @Column(name = "proximo_intento", nullable = false) public LocalDateTime proximoIntento;
    @Column(name = "actualizado_en", nullable = false) public LocalDateTime actualizadoEn;
    @Column(length = 80) public String referencia;
    public CorreoCodigoEntity() {}
}
