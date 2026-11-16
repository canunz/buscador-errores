package cl.casol.backend.catalogo.application.port.out;
import cl.casol.backend.catalogo.domain.*;
import java.util.*;
public interface CatalogoAdministrativoRepository {
    List<CatalogoAdministrativo> listar(TipoCatalogo tipo);
    Optional<CatalogoAdministrativo> buscar(TipoCatalogo tipo, Integer id);
    CatalogoAdministrativo guardar(TipoCatalogo tipo, CatalogoAdministrativo registro);
    void bloquear(TipoCatalogo tipo);
    boolean moduloReferenciado(Integer id);
    void reindexarReferencias(TipoCatalogo tipo, Integer id);
}
