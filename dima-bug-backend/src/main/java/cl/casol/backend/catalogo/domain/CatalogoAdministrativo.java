package cl.casol.backend.catalogo.domain;
import java.util.List;
/**
 * Modelo de dominio de catálogo administrativo. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record CatalogoAdministrativo(Integer id, String nombre, boolean activo,
        String sistemaOperativo, String descripcion, Integer sistemaId, Integer orden,
        List<Integer> sistemaIds) { }
