package cl.casol.backend.shared.application.archivo;

/**
 * Archivo descarga (módulo de uso común).
 */
public record ArchivoDescarga(String nombre, String mime, byte[] contenido) { }
