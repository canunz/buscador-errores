package cl.casol.backend.conocimiento.domain;
import cl.casol.backend.conocimiento.domain.exception.SolucionDuplicadaException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
/** Compara contenido; conserva literalmente el original al guardarlo. */
public final class IdentidadSolucion {
    private IdentidadSolucion() { }
    private static String normalizar(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto, Normalizer.Form.NFC)
                .replaceAll("[\\s\\p{Z}]+", " ").strip().toLowerCase(Locale.ROOT);
    }
    public static void exigirNueva(List<Solucion> actuales, String titulo, String descripcion, TipoSolucion tipo) {
        if (actuales.stream().anyMatch(s -> Objects.equals(s.tipo(), tipo)
                && normalizar(s.titulo()).equals(normalizar(titulo))
                && normalizar(s.descripcion()).equals(normalizar(descripcion)))) throw new SolucionDuplicadaException();
    }
}
