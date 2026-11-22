package cl.casol.backend.whatsapp.infrastructure.persistence;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

/** Fallo temprano: esta entrega está autorizada para QA local, no para Meta/producción. */
@Configuration(proxyBeanMethods=false)
@Profile("qa-whatsapp-durable | qa-whatsapp-meta")
public class WhatsAppDurablePerfilGuard {
    @Bean static BeanFactoryPostProcessor validarPerfilDurable(Environment env){
        return factory->{
            boolean meta=env.acceptsProfiles(Profiles.of("qa-whatsapp-meta"));
            if(!env.acceptsProfiles(Profiles.of("!staging & !prod & !production")) ||
                    (meta?env.acceptsProfiles(Profiles.of("qa-whatsapp-http-local | qa-whatsapp-durable | qa-whatsapp-simulado")):
                          !env.acceptsProfiles(Profiles.of("qa-whatsapp-http-local"))))
                throw new IllegalStateException("WhatsApp durable requiere exclusivamente el webhook de QA local");
            boolean conexionReal=env.getProperty("dimabug.whatsapp.meta-qa.envio-real",Boolean.class,false)||
                    env.getProperty("dimabug.whatsapp.meta-qa.conexion-meta",Boolean.class,false);
            // Conexión/envío real solo con la autoatención activa, credencial Graph y actor de tickets explícitos.
            boolean autorizada=env.getProperty("dimabug.whatsapp.autoatencion.habilitada",Boolean.class,false)&&
                    !env.getProperty("dimabug.whatsapp.meta-qa.graph-token","").isBlank()&&
                    !env.getProperty("dimabug.whatsapp.autoatencion.ticket-actor-email","").isBlank();
            if(meta&&(!"127.0.0.1".equals(env.getProperty("server.address"))||
                    !"none".equals(env.getProperty("server.forward-headers-strategy"))||
                    (conexionReal&&!autorizada)))
                throw new IllegalStateException("Meta QA solo admite conexión o envío real con la autoatención autorizada");
            String url=env.getProperty("spring.datasource.url","");
            if(url.startsWith("jdbc:mysql://")){
                if(!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/wa_durable_[a-f0-9]+(?:\\?.*)?")||
                        !"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto")))
                    throw new IllegalStateException("WhatsApp durable requiere MySQL exclusivo de QA y ddl-auto=validate");
            }else if(!url.startsWith("jdbc:h2:mem:wa_"))throw new IllegalStateException("Base WhatsApp de QA no autorizada");
        };
    }
}
