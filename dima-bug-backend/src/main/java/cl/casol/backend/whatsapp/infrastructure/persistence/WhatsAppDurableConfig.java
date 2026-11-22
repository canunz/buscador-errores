package cl.casol.backend.whatsapp.infrastructure.persistence;

import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.application.service.ProcesarReservaWhatsAppService;
import cl.casol.backend.whatsapp.infrastructure.busqueda.*;
import cl.casol.backend.whatsapp.infrastructure.tickets.TicketWhatsAppAdapter;
import cl.casol.backend.tickets.application.port.in.CrearTicketUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@Profile(PerfilesWhatsApp.DURABLE)
public class WhatsAppDurableConfig {
    @Bean CifradoWhatsApp cifradoWhatsApp(@Value("${dimabug.whatsapp.durable.clave-cifrado}")String cifrado,
            @Value("${dimabug.whatsapp.durable.clave-hmac}")String hmac){return new CifradoWhatsApp(cifrado,hmac);}
    @Bean ProcesarReservaWhatsAppService procesarReservaWhatsApp(AlmacenWhatsAppPort almacen,
            ConsultarChatbotService buscador,@Qualifier("ocrWebhookHttpLocal")ReconocerImagenPort ocr,
            @Value("${dimabug.whatsapp.autoatencion.habilitada:false}")boolean autoatencion,
            @Value("${dimabug.whatsapp.autoatencion.ticket-actor-email:}")String actor,
            ObjectProvider<CrearTicketUseCase> tickets){
        var busqueda=new BusquedaPublicaCanalAdapter(buscador);
        if(!autoatencion)return new ProcesarReservaWhatsAppService(almacen,busqueda,ocr);
        // Autoatención: Hola → ayuda; foto → OCR → solución → ¿se solucionó?; NO o sin solución → ticket DimaBug.
        return new ProcesarReservaWhatsAppService(almacen,busqueda,ocr,new TicketWhatsAppAdapter(tickets.getObject(),actor));
    }
    @Bean PrepararRespuestaPublicaPort prepararRespuestaWhatsApp(AutoservicioPublicoService servicio){return new PrepararRespuestaPublicaAdapter(servicio);}
    @Bean(name="whatsAppScheduler") ThreadPoolTaskScheduler whatsAppScheduler(){
        var s=new ThreadPoolTaskScheduler();s.setPoolSize(2);s.setThreadNamePrefix("wa-reloj-");s.setWaitForTasksToCompleteOnShutdown(false);return s;
    }
}
