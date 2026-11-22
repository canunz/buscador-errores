package cl.casol.backend.ejecucion.domain;

/**
 * Valores posibles de estado ejecución paso.
 */
public enum EstadoEjecucionPaso {
    PENDIENTE, EN_PROCESO, ESPERANDO_RESPUESTA, ESPERANDO_EVIDENCIA, EVIDENCIA_RECIBIDA,
    EN_VALIDACION, RECHAZADO, COMPLETADO, ERROR, ESPERANDO_CONFIRMACION
}
