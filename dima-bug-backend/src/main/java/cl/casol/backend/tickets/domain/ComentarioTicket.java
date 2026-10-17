package cl.casol.backend.tickets.domain;

import java.time.LocalDateTime;

public record ComentarioTicket(Long id, Integer autorId, String texto, boolean interno, LocalDateTime fecha) {
    public static final int MAX_TEXTO = 1000;

    public boolean visiblePara(ActorTicket actor) {
        return !interno || actor.esStaff();
    }
}