package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import cl.casol.backend.conocimiento.domain.TipoMaterial;

public record MaterialApoyoResponse(Integer id, String nombre, TipoMaterial tipo, String url) {
    public static MaterialApoyoResponse from(MaterialApoyo material) {
        return new MaterialApoyoResponse(material.id(), material.nombre(), material.tipo(), material.url());
    }
}
