package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.CodigoAcceso;

import java.util.Optional;

/**
 * Repositorio Spring Data de código acceso.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface CodigoAccesoRepository {

    /** Último código generado para el usuario (usado o no), para controlar los reenvíos. */
    Optional<CodigoAcceso> buscarUltimo(Integer usuarioId);

    /** Último código aún no usado del usuario. */
    Optional<CodigoAcceso> buscarPendiente(Integer usuarioId);

    /** Deja sin efecto los códigos anteriores al generar uno nuevo. */
    void invalidarPendientes(Integer usuarioId);

    // Guarda el código.
    CodigoAcceso guardar(CodigoAcceso codigo);

    /** Serializa emisión, reenvío y consumo incluso cuando todavía no hay códigos. */
    void bloquearUsuario(Integer usuarioId);

    // Cuenta desde.
    long contarDesde(Integer usuarioId, java.time.LocalDateTime desde);

    // Indica si está vigente.
    boolean vigente(Long codigoId, java.time.LocalDateTime ahora);
}
