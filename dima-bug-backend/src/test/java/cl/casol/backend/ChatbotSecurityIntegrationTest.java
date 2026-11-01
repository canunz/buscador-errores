package cl.casol.backend;

import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService;
import cl.casol.backend.conocimiento.infrastructure.web.ChatbotController;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.application.service.VisibilidadContenidoService;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatbotController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ChatbotSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConsultarChatbotService service;
    @MockitoBean TokenServicePort tokens;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAutenticadoCache cache;
    @MockitoBean VisibilidadContenidoService visibilidad;

    private void auth(String rol) {
        when(tokens.esValido("valido")).thenReturn(true);
        when(tokens.obtenerEmail("valido")).thenReturn("qa@example.invalid");
        when(usuarios.buscarPorEmail("qa@example.invalid")).thenReturn(Optional.of(new Usuario(1,
                new Rol(1, rol, null, true, null, null), "QA", "qa@example.invalid", "h", true, null, null)));
    }

    @ParameterizedTest @ValueSource(strings = {"ADMINISTRADOR", "TECNICO", "SOPORTE", "USUARIO"})
    void rolesAutorizadosRecibenSoloContratoOriginal(String rol) throws Exception {
        auth(rol);
        when(service.consultar("impresora")).thenReturn(List.of(new ConsultarChatbotService.SolucionChatbot("Impresión", "Reinicio", "Paso seguro")));
        mvc.perform(post("/api/chatbot/consultas").header("Authorization", "Bearer valido")
                .contentType(MediaType.APPLICATION_JSON).content("{\"consulta\":\"impresora\"}"))
                .andExpect(status().isOk()).andExpect(content().json("""
                        {"mensaje":"Encontré estas soluciones de autoservicio:","soluciones":[
                        {"conocimiento":"Impresión","titulo":"Reinicio","solucion":"Paso seguro"}]}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void sinTokenOConTokenInvalidoDevuelve401() throws Exception {
        for (String token : List.of("", "Bearer invalido")) mvc.perform(post("/api/chatbot/consultas")
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"consulta\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void rolAjenoDevuelve403() throws Exception {
        auth("OTRO");
        mvc.perform(post("/api/chatbot/consultas").header("Authorization", "Bearer valido")
                .contentType(MediaType.APPLICATION_JSON).content("{\"consulta\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void entradasInvalidasNoLleganAlServicio() throws Exception {
        auth("USUARIO");
        for (String cuerpo : List.of("{}", "{\"consulta\":null}", "{\"consulta\":\"  \"}", "{\"consulta\":\"" + "x".repeat(301) + "\"}")) {
            mvc.perform(post("/api/chatbot/consultas").header("Authorization", "Bearer valido")
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void acepta300CaracteresYDerivaSinRevelarContenido() throws Exception {
        auth("USUARIO");
        when(service.consultar("x".repeat(300))).thenReturn(List.of());
        mvc.perform(post("/api/chatbot/consultas").header("Authorization", "Bearer valido")
                .contentType(MediaType.APPLICATION_JSON).content("{\"consulta\":\"" + "x".repeat(300) + "\"}"))
                .andExpect(status().isOk()).andExpect(content().json("""
                        {"mensaje":"No encontré una solución de autoservicio para tu consulta. Contacta a Soporte para recibir ayuda.","soluciones":[]}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }
}
