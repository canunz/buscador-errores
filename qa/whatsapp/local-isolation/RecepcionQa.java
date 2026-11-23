package cl.casol.qa;

import cl.casol.backend.whatsapp.infrastructure.meta.*;
import cl.casol.backend.whatsapp.infrastructure.persistence.*;
import cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal;
import cl.casol.backend.whatsapp.application.port.out.AlmacenWhatsAppPort;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Composition root de pruebas: solo recepción durable, sin component scan. */
@Configuration(proxyBeanMethods=false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses=ConversacionWhatsAppEntity.class)
@EnableJpaRepositories(basePackageClasses=ConversacionWhatsAppRepository.class)
@Import({WebhookMetaQaObservabilidad.class, AlmacenWhatsAppJpaAdapter.class, WebhookMetaQaController.class, WhatsAppDurablePerfilGuard.class})
public class RecepcionQa {
    public static void main(String[] args) { SpringApplication.run(RecepcionQa.class,args); }
    @Bean ObservabilidadMetaQa observabilidad(){return new ObservabilidadMetaQa();}
    @Bean ControlesMetaQa controles(Environment e, ObservabilidadMetaQa o){
        for(String nombre:new String[]{"procesamiento","medios","envio","envio-real","conexion-meta"})
            if(e.getProperty("dimabug.whatsapp.meta-qa."+nombre,Boolean.class,false))
                throw new IllegalStateException("Artefacto QA exclusivo de recepción");
        return new ControlesMetaQa(new ControlesMetaQa.Estado(e.getProperty("dimabug.whatsapp.meta-qa.recepcion",Boolean.class,false),false,false,false),o);
    }
    @Bean PoliticaMetaQa politica(Environment e){return new PoliticaMetaQa(e.getRequiredProperty("dimabug.whatsapp.meta-qa.waba"),e.getRequiredProperty("dimabug.whatsapp.meta-qa.numero"),e.getRequiredProperty("dimabug.whatsapp.meta-qa.participantes"));}
    @Bean ValidacionWebhookLocal firma(Environment e){return new ValidacionWebhookLocal(e.getRequiredProperty("dimabug.whatsapp.meta-qa.verify-token"),e.getRequiredProperty("dimabug.whatsapp.meta-qa.app-secret"),EntradaMetaQaAdapter.MAX_BYTES);}
    @Bean CifradoWhatsApp cifrado(Environment e){return new CifradoWhatsApp(e.getRequiredProperty("dimabug.whatsapp.durable.clave-cifrado"),e.getRequiredProperty("dimabug.whatsapp.durable.clave-hmac"));}
    @Bean EntradaMetaQaAdapter entrada(AlmacenWhatsAppPort a,ValidacionWebhookLocal f,PoliticaMetaQa p,ControlesMetaQa c,ObservabilidadMetaQa o){return new EntradaMetaQaAdapter(a,f,p,c,o);}
    @Bean SecurityFilterChain seguridad(HttpSecurity http)throws Exception{
        return http.csrf(c->c.disable()).requestCache(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c->c.requestMatchers("/webhooks/whatsapp").permitAll().anyRequest().denyAll()).build();
    }
}
