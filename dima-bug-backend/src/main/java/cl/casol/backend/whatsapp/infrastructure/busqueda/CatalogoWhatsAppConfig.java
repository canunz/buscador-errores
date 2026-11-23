package cl.casol.backend.whatsapp.infrastructure.busqueda;

import cl.casol.backend.conocimiento.application.service.AutoservicioPublicoService;
import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService;
import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.infrastructure.persistence.PerfilesWhatsApp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import java.time.Duration;

@Configuration(proxyBeanMethods=false)
@Profile(PerfilesWhatsApp.DURABLE)
public class CatalogoWhatsAppConfig {
    private static final String MODO="dimabug.whatsapp.catalogo.modo";
    @Bean
    static org.springframework.beans.factory.config.BeanFactoryPostProcessor validarModo(org.springframework.core.env.Environment entorno){
        return fabrica -> {
            if(!java.util.Set.of("local","http-principal").contains(entorno.getProperty(MODO,"local")))
                throw new IllegalArgumentException("Modo de catálogo WhatsApp inválido");
        };
    }
    @Bean @ConditionalOnProperty(name=MODO,havingValue="http-principal")
    CatalogoPublicoHttpClient catalogoPublicoHttpClient(
            @Value("${dimabug.whatsapp.catalogo.url-base:http://127.0.0.1:8081}")String url,
            @Value("${dimabug.whatsapp.catalogo.conexion-ms:1000}")long conexion,
            @Value("${dimabug.whatsapp.catalogo.respuesta-ms:3000}")long respuesta,
            @Value("${server.port:8082}")int puertoPropio){
        var cliente=new CatalogoPublicoHttpClient(url,Duration.ofMillis(conexion),Duration.ofMillis(respuesta));
        if(cliente.puerto()==puertoPropio)throw new IllegalArgumentException("El catálogo no puede consultar el propio backend WhatsApp");
        return cliente;
    }
    @Bean @ConditionalOnProperty(name=MODO,havingValue="http-principal")
    BuscarSolucionesCanalPort busquedaPrincipal(CatalogoPublicoHttpClient cliente){return new BusquedaPublicaHttpAdapter(cliente);}
    @Bean @ConditionalOnProperty(name=MODO,havingValue="http-principal")
    PrepararRespuestaPublicaPort preparacionPrincipal(CatalogoPublicoHttpClient cliente){return new PrepararRespuestaPublicaHttpAdapter(cliente);}
    @Bean @ConditionalOnProperty(name=MODO,havingValue="local",matchIfMissing=true)
    BuscarSolucionesCanalPort busquedaLocal(ConsultarChatbotService servicio){return new BusquedaPublicaCanalAdapter(servicio);}
    @Bean @ConditionalOnProperty(name=MODO,havingValue="local",matchIfMissing=true)
    PrepararRespuestaPublicaPort preparacionLocal(AutoservicioPublicoService servicio){return new PrepararRespuestaPublicaAdapter(servicio);}
}
