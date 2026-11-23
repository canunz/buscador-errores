package cl.casol.backend.whatsapp.domain;

/** Contrato neutral; no contiene objetos de Meta, Node ni infraestructura. */
public record MensajeCanal(String cuenta, String canal, String usuario, String id,
                           Tipo tipo, String contenido) {
    public enum Tipo { TEXTO, IMAGEN, NO_COMPATIBLE }
    @Override public String toString() { return "MensajeCanal[contenido omitido]"; }
}
