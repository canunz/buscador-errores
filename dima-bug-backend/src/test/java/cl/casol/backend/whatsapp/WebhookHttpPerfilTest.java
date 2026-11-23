package cl.casol.backend.whatsapp;
import cl.casol.backend.whatsapp.infrastructure.http.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.application.service.VisibilidadContenidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(WebhookWhatsAppController.class)
@ActiveProfiles("qa-sin-webhook")
@Import({WebhookHttpLocalConfig.class,SecurityConfig.class,JwtAuthenticationFilter.class})
class WebhookHttpPerfilTest {
 @Autowired MockMvc mvc;
 @Autowired org.springframework.context.ApplicationContext contexto;
 @MockitoBean TokenServicePort tokens;
 @MockitoBean UsuarioRepository usuarios;
 @MockitoBean UsuarioAutenticadoCache cache;
 @MockitoBean VisibilidadContenidoService visibilidad;
 @Test void desactivadoPorDefectoSinSecretos()throws Exception{
   assertTrue(contexto.getBeansOfType(WebhookWhatsAppController.class).isEmpty());
   assertTrue(contexto.getBeansOfType(ValidacionWebhookLocal.class).isEmpty());
   mvc.perform(get("/webhooks/whatsapp")).andExpect(status().isUnauthorized());
   mvc.perform(post("/webhooks/whatsapp")).andExpect(status().isUnauthorized());
 }
 @Test void noSeActivaConPerfilesProductivos(){
   for(String perfil:new String[]{"staging","prod","production"})try(var c=new AnnotationConfigApplicationContext()){
     c.getEnvironment().setActiveProfiles("qa-whatsapp-http-local",perfil);
     c.register(WebhookHttpLocalConfig.class,WebhookWhatsAppController.class);c.refresh();
     assertTrue(c.getBeansOfType(WebhookWhatsAppController.class).isEmpty());
   }
 }
}
