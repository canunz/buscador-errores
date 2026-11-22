package cl.casol.backend.tickets.domain;

import java.util.List;

/**
 * Modelo de dominio de página tickets. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record PaginaTickets<T>(List<T> contenido, int pagina, int tamano, long totalElementos) {

    // Devuelve total paginas.
    public int totalPaginas() {
        return tamano <= 0 ? 0 : (int) Math.ceil((double) totalElementos / tamano);
    }
}