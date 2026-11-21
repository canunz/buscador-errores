package cl.casol.backend.shared.application.archivo;

/**
 * Archivo subido (módulo de uso común).
 */
public record ArchivoSubido(String mime, byte[] contenido) { }
