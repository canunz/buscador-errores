package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Sucursal;
import java.util.*;

public interface SucursalRepository {
    List<Sucursal> listar();
    Optional<Sucursal> buscar(Integer id);
    Optional<Sucursal> buscarPorCodigo(String codigo);
    void bloquear(Integer id);
    Sucursal guardar(Sucursal sucursal);
}
