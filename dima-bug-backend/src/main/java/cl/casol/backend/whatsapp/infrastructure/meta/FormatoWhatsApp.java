package cl.casol.backend.whatsapp.infrastructure.meta;

import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import static cl.casol.backend.whatsapp.domain.RespuestaCanal.Estado.*;

/** Convierte la respuesta pública ya preparada en el texto que verá el usuario en WhatsApp. */
public final class FormatoWhatsApp {
    public static final int MAX = 4000; // Límite de Meta: 4096 caracteres por mensaje de texto.
    private FormatoWhatsApp() { }

    public static String texto(RespuestaCanal r) {
        var sb = new StringBuilder(limpiar(r.mensaje()));
        if (r.estado() == RESULTADOS && !r.conocimientos().isEmpty()) {
            int n = 0;
            for (var s : r.conocimientos()) {
                if (n == 2) break;
                sb.append("\n\n✅ *").append(n == 0 ? "Solución sugerida" : "Otra opción").append(":* ")
                  .append(limpiar(s.titulo()));
                String pasos = limpiar(s.instrucciones());
                if (!pasos.isEmpty()) sb.append("\n").append(pasos);
                n++;
            }
            sb.append("\n\n¿Se solucionó tu problema? Responde *SÍ* o *NO*.");
        } else if (r.estado() == SIN_RESULTADOS && sb.isEmpty()) {
            sb.append("No encontré una solución de autoatención. Contacta a Soporte TI.");
        }
        String t = sb.toString().strip();
        return t.length() <= MAX ? t : t.substring(0, MAX - 1) + "…";
    }

    /** Las instrucciones pueden venir con HTML del editor: se dejan como texto plano. */
    static String limpiar(String t) {
        if (t == null) return "";
        String s = t.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</div>", "\n").replaceAll("(?i)<li[^>]*>", "• ")
                .replaceAll("<[^>]+>", "").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n").strip();
    }
}
