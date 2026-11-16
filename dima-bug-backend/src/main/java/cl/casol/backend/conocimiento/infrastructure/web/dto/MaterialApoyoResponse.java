package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import cl.casol.backend.conocimiento.domain.TipoMaterial;

public record MaterialApoyoResponse(Integer id, String nombre, TipoMaterial tipo, String url) {
    public static MaterialApoyoResponse fromPaso(MaterialApoyo material, Integer procedimientoId, Integer pasoId) {
        String url = material.url();
        if (url != null && url.startsWith(cl.casol.backend.shared.application.archivo.FormatoArchivo.PREFIJO))
            url = "/api/procedimientos/" + procedimientoId + "/pasos/" + pasoId
                    + "/materiales/" + material.id() + "/archivo";
        return new MaterialApoyoResponse(material.id(), material.nombre(), material.tipo(), url);
    }
    public static MaterialApoyoResponse from(MaterialApoyo material) {
        String url = material.url();
        if (url != null && url.startsWith(cl.casol.backend.shared.application.archivo.FormatoArchivo.PREFIJO)) {
            url = material.conocimientoId() == null ? null
                    : "/api/conocimientos/" + material.conocimientoId() + "/materiales/" + material.id() + "/archivo";
        }
        return new MaterialApoyoResponse(material.id(), material.nombre(), material.tipo(), url);
    }
}
