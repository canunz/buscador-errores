package cl.casol.backend.whatsapp;

import cl.casol.backend.whatsapp.infrastructure.meta.*;
import cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class ObservabilidadWebhookMetaQaTest {
    @Test void motivosConservanRechazosYAlias(){
        var p=new PoliticaMetaQa("cuenta","numero","tel:10000001=a,bsuid:US.prueba=a,bsuid:US.otro=b");
        assertEquals(PoliticaMetaQa.NoAutorizado.Validacion.WABA,assertThrows(PoliticaMetaQa.NoAutorizado.class,()->p.cuenta("otra")).validacion());
        assertEquals(PoliticaMetaQa.NoAutorizado.Validacion.PHONE_NUMBER_ID,assertThrows(PoliticaMetaQa.NoAutorizado.class,()->p.numero("otro")).validacion());
        assertEquals(PoliticaMetaQa.NoAutorizado.Validacion.PARTICIPANTE,assertThrows(PoliticaMetaQa.NoAutorizado.class,()->p.participante("10000002",null)).validacion());
        assertEquals(PoliticaMetaQa.NoAutorizado.Validacion.ALIAS,assertThrows(PoliticaMetaQa.NoAutorizado.class,()->p.participante("10000001","US.otro")).validacion());
        assertEquals("a",p.participante("10000001","US.prueba"));
    }
    @Test void correlacionYLogSinDatosSensibles(CapturedOutput output)throws Exception{
        var entrada=mock(EntradaMetaQaAdapter.class);
        when(entrada.recibir(any(),anyString())).thenThrow(new PoliticaMetaQa.NoAutorizado(PoliticaMetaQa.NoAutorizado.Validacion.PARTICIPANTE,"STATUSES"));
        var controller=new WebhookMetaQaController(new ValidacionWebhookLocal("token-privado-sintetico","secreto-privado-sintetico",EntradaMetaQaAdapter.MAX_BYTES),entrada,new ObservabilidadMetaQa());
        class Registro extends InterceptorRegistry {
            HandlerInterceptor interceptor;
            @Override public InterceptorRegistration addInterceptor(HandlerInterceptor i){interceptor=i;return super.addInterceptor(i);}
        }
        var registro=new Registro();new WebhookMetaQaObservabilidad().addInterceptors(registro);
        var mvc=MockMvcBuilders.standaloneSetup(controller).addInterceptors(registro.interceptor).build();
        var get=mvc.perform(get("/webhooks/whatsapp").param("hub.mode","subscribe").param("hub.verify_token","token-privado-sintetico").param("hub.challenge","reto-privado"))
            .andExpect(status().isOk()).andReturn();
        var post=mvc.perform(post("/webhooks/whatsapp").contentType("application/json").header("X-Hub-Signature-256","firma-privada").content("payload-privado"))
            .andExpect(status().isForbidden()).andReturn();
        String id=post.getResponse().getHeader("X-QA-Correlation-ID");
        assertNotNull(id);assertDoesNotThrow(()->java.util.UUID.fromString(id));
        assertNotEquals(get.getResponse().getHeader("X-QA-Correlation-ID"),id);
        String logs=output.getOut();assertTrue(logs.contains("metodo=GET status=200"));
        assertTrue(logs.contains("metodo=POST status=403 correlacion="+id));
        assertTrue(logs.contains("validacion=PARTICIPANTE elemento=STATUSES"));
        for(String privado:new String[]{"token-privado-sintetico","secreto-privado-sintetico","reto-privado","firma-privada","payload-privado"})
            assertFalse(logs.contains(privado));
    }
}
