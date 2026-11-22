package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Sucursal;
import java.util.*;

/**
 * Repositorio Spring Data de sucursal.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SucursalRepository {
    List<Sucursal> listar();
    // Busca por id.
    Optional<Sucursal> buscar(Integer id);
    // Busca por código.
    Optional<Sucursal> buscarPorCodigo(String codigo);
    // Bloquea por id.
    void bloquear(Integer id);
    // Guarda el sucursal.
    Sucursal guardar(Sucursal sucursal);
}
