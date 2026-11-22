package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.ChatbotResponse;
import cl.casol.backend.conocimiento.infrastructure.web.dto.ConsultaChatbotRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST de chatbot. Atiende las rutas que empiezan con /api/chatbot.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {
    private final ConsultarChatbotService service;
    public ChatbotController(ConsultarChatbotService service) { this.service = service; }

    // POST /api/chatbot/consultas: consultar.
    @PostMapping("/consultas")
    public ChatbotResponse consultar(@Valid @RequestBody ConsultaChatbotRequest request) {
        return ChatbotResponse.from(service.consultar(request.consulta()));
    }
}
