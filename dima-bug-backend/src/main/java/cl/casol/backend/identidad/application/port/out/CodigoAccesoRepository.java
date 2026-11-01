package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.CodigoAcceso;

import java.util.Optional;

public interface CodigoAccesoRepository {

    /** Último código generado para el usuario (usado o no), para controlar los reenvíos. */
    Optional<CodigoAcceso> buscarUltimo(Integer usuarioId);

    /** Último código aún no usado del usuario. */
    Optional<CodigoAcceso> buscarPendiente(Integer usuarioId);

    /** Deja sin efecto los códigos anteriores al generar uno nuevo. */
    void invalidarPendientes(Integer usuarioId);

    CodigoAcceso guardar(CodigoAcceso codigo);
}
