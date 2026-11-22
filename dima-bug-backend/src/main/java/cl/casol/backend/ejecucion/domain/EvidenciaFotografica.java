package cl.casol.backend.ejecucion.domain;

import java.time.Instant;

/**
 * Modelo de dominio de evidencia fotográfica. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record EvidenciaFotografica(Long id,String solicitudId,Categoria categoria,String sha256,byte[] datos,Instant recibida) {
    public enum Categoria { MUEBLE, RED, ELECTRICIDAD, FOTO_GENERAL }
}
