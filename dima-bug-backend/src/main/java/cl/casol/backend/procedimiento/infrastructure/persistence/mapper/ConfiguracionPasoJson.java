package cl.casol.backend.procedimiento.infrastructure.persistence.mapper;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.shared.domain.DestinatarioPaso;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
public final class ConfiguracionPasoJson {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private ConfiguracionPasoJson(){}
    public static String escribir(Object valor){return valor==null?null:JSON.writeValueAsString(valor);}
    public static ConfiguracionPaso leer(String valor){return valor==null?null:JSON.readValue(valor,ConfiguracionPaso.class);}
    public static List<DestinatarioPaso> destinatarios(String valor){return valor==null?List.of():List.of(JSON.readValue(valor,DestinatarioPaso[].class));}
}
