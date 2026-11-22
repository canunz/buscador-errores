package cl.casol.backend.whatsapp.infrastructure.simulado;

import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService;
import cl.casol.backend.whatsapp.application.service.ProcesarMensajeService;
import cl.casol.backend.whatsapp.infrastructure.busqueda.BusquedaPublicaCanalAdapter;
import cl.casol.backend.whatsapp.infrastructure.ocr.OcrNodeProcesoAdapter;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@Profile("qa-whatsapp-simulado")
public class WhatsAppQaConfig {
    @Bean WebhookSimuladoAdapter webhookSimulado(ConsultarChatbotService buscador,
            @Value("${dimabug.whatsapp.qa.raiz}") String raiz) {
        return new WebhookSimuladoAdapter(new ProcesarMensajeService(new BusquedaPublicaCanalAdapter(buscador),
                new OcrNodeProcesoAdapter(Path.of(raiz)),Clock.systemUTC()));
    }
}
