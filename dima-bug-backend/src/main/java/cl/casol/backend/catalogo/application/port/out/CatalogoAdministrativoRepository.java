package cl.casol.backend.catalogo.application.port.out;
import cl.casol.backend.catalogo.domain.*;
import java.util.*;
/**
 * Repositorio Spring Data de catálogo administrativo.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface CatalogoAdministrativoRepository {
    // Lista el tipo.
    List<CatalogoAdministrativo> listar(TipoCatalogo tipo);
    // Busca el tipo.
    Optional<CatalogoAdministrativo> buscar(TipoCatalogo tipo, Integer id);
    // Guarda el tipo.
    CatalogoAdministrativo guardar(TipoCatalogo tipo, CatalogoAdministrativo registro);
    // Bloquea el tipo.
    void bloquear(TipoCatalogo tipo);
    // Indica si módulo referenciado.
    boolean moduloReferenciado(Integer id);
    // Vuelve a indexar referencias.
    void reindexarReferencias(TipoCatalogo tipo, Integer id);
}
