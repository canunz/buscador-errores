package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.ChatbotResponse;
import cl.casol.backend.conocimiento.infrastructure.web.dto.ConsultaChatbotRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {
    private final ConsultarChatbotService service;
    public ChatbotController(ConsultarChatbotService service) { this.service = service; }

    @PostMapping("/consultas")
    public ChatbotResponse consultar(@Valid @RequestBody ConsultaChatbotRequest request) {
        return ChatbotResponse.from(service.consultar(request.consulta()));
    }
}
