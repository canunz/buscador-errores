package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService.SolucionChatbot;
import java.util.List;

/**
 * Datos que se devuelven al frontend con chatbot.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ChatbotResponse(String mensaje, List<ChatbotSolucionResponse> soluciones) {
    public static final String MENSAJE_SOPORTE =
            "No encontré una solución de autoservicio para tu consulta. Contacta a Soporte para recibir ayuda.";

    // Crea el objeto a partir de los datos recibidos.
    public static ChatbotResponse from(List<SolucionChatbot> soluciones) {
        return new ChatbotResponse(soluciones.isEmpty() ? MENSAJE_SOPORTE : "Encontré estas soluciones de autoservicio:",
                soluciones.stream().map(s -> new ChatbotSolucionResponse(s.id(), s.conocimiento(), s.titulo(), s.solucion())).toList());
    }

    // Devuelve chatbot solución response.
    public record ChatbotSolucionResponse(Integer id, String conocimiento, String titulo, String solucion) { }
}
