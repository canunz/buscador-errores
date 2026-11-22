package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: prueba conflicto.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class PruebaConflictoException extends RuntimeException { public PruebaConflictoException(String mensaje){super(mensaje);} }
