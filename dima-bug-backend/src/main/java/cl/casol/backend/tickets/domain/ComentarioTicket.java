package cl.casol.backend.tickets.domain;

import java.time.LocalDateTime;

/**
 * Modelo de dominio de comentario ticket. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record ComentarioTicket(Long id, Integer autorId, String texto, boolean interno, LocalDateTime fecha) {
    public static final int MAX_TEXTO = 1000;

    /** Las notas internas no se las mostramos al usuario que pidió ayuda. */
    public boolean visiblePara(ActorTicket actor) {
        return !interno || actor.esStaff();
    }
}