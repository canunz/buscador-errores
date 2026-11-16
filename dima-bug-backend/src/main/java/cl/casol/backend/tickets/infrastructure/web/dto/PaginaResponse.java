package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.PaginaTickets;

import java.util.List;
import java.util.function.Function;

public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
    public static <D, T> PaginaResponse<T> from(PaginaTickets<D> pagina, Function<D, T> mapper) {
        return new PaginaResponse<>(pagina.contenido().stream().map(mapper).toList(), pagina.pagina(),
                pagina.tamano(), pagina.totalElementos(), pagina.totalPaginas());
    }
}