package cl.casol.backend.whatsapp.domain;

import java.util.List;

/** Instantánea neutral de la propuesta; el adaptador decide cómo protegerla. */
public record EstadoConversacion(String referencia, String consulta,
        List<RespuestaCanal.Candidato> candidatos, long venceEpochMilli) {
    public EstadoConversacion { candidatos=List.copyOf(candidatos); }
    @Override public String toString() { return "EstadoConversacion[contenido omitido]"; }
}
