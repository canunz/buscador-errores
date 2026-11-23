package cl.casol.backend.whatsapp.infrastructure.meta;

import jakarta.servlet.http.*;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import java.time.Instant;
import java.util.UUID;

/** Solo metadatos generados localmente; nunca registra URL, cabeceras ni cuerpos. */
@Configuration(proxyBeanMethods=false)
@Profile("qa-whatsapp-meta & !staging & !prod & !production")
public class WebhookMetaQaObservabilidad implements WebMvcConfigurer {
    public static final String VALIDACION=WebhookMetaQaObservabilidad.class.getName()+".validacion";
    public static final String ELEMENTO=WebhookMetaQaObservabilidad.class.getName()+".elemento";
    private static final String CORRELACION=WebhookMetaQaObservabilidad.class.getName()+".correlacion";
    @Override public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(new HandlerInterceptor(){
            @Override public boolean preHandle(HttpServletRequest r,HttpServletResponse s,Object h){
                String id=UUID.randomUUID().toString();
                r.setAttribute(CORRELACION,id);s.setHeader("X-QA-Correlation-ID",id);return true;
            }
            @Override public void afterCompletion(HttpServletRequest r,HttpServletResponse s,Object h,Exception e){
                String method=switch(r.getMethod()){case "GET"->"GET";case "POST"->"POST";default->"OTRO";};
                LoggerFactory.getLogger(WebhookMetaQaObservabilidad.class).info(
                    "wa_qa_request fecha={} metodo={} status={} correlacion={} validacion={} elemento={}",
                    Instant.now(),method,s.getStatus(),r.getAttribute(CORRELACION),
                    r.getAttribute(VALIDACION)==null?"NINGUNA":r.getAttribute(VALIDACION),
                    r.getAttribute(ELEMENTO)==null?"NINGUNO":r.getAttribute(ELEMENTO));
            }
        }).addPathPatterns("/webhooks/whatsapp");
    }
}
