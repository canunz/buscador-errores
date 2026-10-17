package cl.casol.backend.tickets.domain;

import java.util.List;

public record PaginaTickets<T>(List<T> contenido, int pagina, int tamano, long totalElementos) {

    public int totalPaginas() {
        return tamano <= 0 ? 0 : (int) Math.ceil((double) totalElementos / tamano);
    }
}