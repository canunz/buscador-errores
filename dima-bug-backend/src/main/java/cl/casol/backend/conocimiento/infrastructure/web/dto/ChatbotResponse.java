package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService.SolucionChatbot;
import java.util.List;

public record ChatbotResponse(String mensaje, List<ChatbotSolucionResponse> soluciones) {
    public static final String MENSAJE_SOPORTE =
            "No encontré una solución de autoservicio para tu consulta. Contacta a Soporte para recibir ayuda.";

    public static ChatbotResponse from(List<SolucionChatbot> soluciones) {
        return new ChatbotResponse(soluciones.isEmpty() ? MENSAJE_SOPORTE : "Encontré estas soluciones de autoservicio:",
                soluciones.stream().map(s -> new ChatbotSolucionResponse(s.conocimiento(), s.titulo(), s.solucion())).toList());
    }

    public record ChatbotSolucionResponse(String conocimiento, String titulo, String solucion) { }
}
