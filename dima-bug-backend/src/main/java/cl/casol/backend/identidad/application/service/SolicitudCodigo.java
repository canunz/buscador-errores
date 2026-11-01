package cl.casol.backend.identidad.application.service;

/** Datos que el frontend necesita para mostrar el contador y habilitar el reenvío. */
public record SolicitudCodigo(int segundosVigencia, int segundosReenvio) {
}
