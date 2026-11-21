package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.PaginaTickets;

import java.util.List;
import java.util.function.Function;

/**
 * Datos que se devuelven al frontend con página.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
    // Crea el objeto a partir de los datos recibidos.
    public static <D, T> PaginaResponse<T> from(PaginaTickets<D> pagina, Function<D, T> mapper) {
        return new PaginaResponse<>(pagina.contenido().stream().map(mapper).toList(), pagina.pagina(),
                pagina.tamano(), pagina.totalElementos(), pagina.totalPaginas());
    }
}