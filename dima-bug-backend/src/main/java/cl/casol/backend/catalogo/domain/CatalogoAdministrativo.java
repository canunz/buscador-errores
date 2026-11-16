package cl.casol.backend.catalogo.domain;
import java.util.List;
public record CatalogoAdministrativo(Integer id, String nombre, boolean activo,
        String sistemaOperativo, String descripcion, Integer sistemaId, Integer orden,
        List<Integer> sistemaIds) { }
