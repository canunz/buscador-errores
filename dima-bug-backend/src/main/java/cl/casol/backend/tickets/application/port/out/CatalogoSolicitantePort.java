package cl.casol.backend.tickets.application.port.out;
import java.util.List;
import java.util.Optional;
/**
 * Puerto de salida de catálogo solicitante.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface CatalogoSolicitantePort {
    // Devuelve opcion.
    record Opcion(Integer id, String nombre) {}
    List<Opcion> sucursales();
    List<Opcion> departamentos();
    // Devuelve sucursal activa.
    Optional<Opcion> sucursalActiva(Integer id);
    // Devuelve departamento activo.
    Optional<Opcion> departamentoActivo(Integer id);
}
