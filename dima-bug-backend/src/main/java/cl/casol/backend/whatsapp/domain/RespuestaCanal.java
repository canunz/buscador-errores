package cl.casol.backend.whatsapp.domain;

import java.util.List;

public record RespuestaCanal(Estado estado, String mensaje, String referencia,
                             List<Solucion> conocimientos, List<Candidato> candidatos,
                             List<String> advertencias, String destinatario) {
    /** Los cuatro últimos estados pertenecen al flujo de autoatención (saludo, verificación y ticket). */
    public enum Estado { RESULTADOS, SIN_RESULTADOS, CONFIRMACION, CANCELADO, INVALIDO,
        NO_COMPATIBLE, DUPLICADO, EXPIRADO, ERROR_OCR, ERROR_BUSCADOR, TIEMPO_AGOTADO, SATURADO,
        SALUDO, SOLUCIONADO, TICKET_CREADO, ERROR_TICKET }
    public record Solucion(Integer id, String titulo, String instrucciones) { }
    public record Candidato(String texto, List<String> avisos) {
        public Candidato { avisos = List.copyOf(avisos); }
    }
    public RespuestaCanal {
        conocimientos = List.copyOf(conocimientos);
        candidatos = List.copyOf(candidatos);
        advertencias = List.copyOf(advertencias);
    }
    /** Compatibilidad: respuestas sin destinatario (simulación y planes ya cifrados). */
    public RespuestaCanal(Estado estado, String mensaje, String referencia, List<Solucion> conocimientos,
                          List<Candidato> candidatos, List<String> advertencias) {
        this(estado, mensaje, referencia, conocimientos, candidatos, advertencias, null);
    }
    /** Alias QA del participante (nunca el teléfono); el adaptador de envío lo resuelve. */
    public RespuestaCanal conDestinatario(String alias) {
        return new RespuestaCanal(estado, mensaje, referencia, conocimientos, candidatos, advertencias, alias);
    }
    public static RespuestaCanal simple(Estado estado, String mensaje) {
        return new RespuestaCanal(estado, mensaje, null, List.of(), List.of(), List.of());
    }
    @Override public String toString() { return "RespuestaCanal[estado=" + estado + ", contenido omitido]"; }
}
