package cl.casol.backend.tickets.application.port.out;

import java.util.List;
import java.util.Optional;

/** Sugerir, validar y crear conocimiento. Nunca se toca el repositorio de conocimientos directamente. */
public interface ConocimientoPort {
    List<ConocimientoSugerido> sugerir(String texto, int limite);

    /** Conocimiento existente y no eliminado. */
    Optional<ConocimientoReferencia> buscar(Integer conocimientoId);

    /** Crea el conocimiento en BORRADOR y devuelve su id. */
    Integer crearBorrador(BorradorConocimiento datos, String emailAutor);
}