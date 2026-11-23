package cl.casol.backend.whatsapp.infrastructure.busqueda;
import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.conocimiento.application.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CatalogoWhatsAppConfigTest {
 AnnotationConfigApplicationContext contexto(Map<String,Object> props){
  var c=new AnnotationConfigApplicationContext();c.getEnvironment().setActiveProfiles("qa-whatsapp-meta");
  c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",props));
  c.registerBean(ConsultarChatbotService.class,()->mock(ConsultarChatbotService.class));
  c.registerBean(AutoservicioPublicoService.class,()->mock(AutoservicioPublicoService.class));c.register(CatalogoWhatsAppConfig.class);return c;
 }
 @Test void predeterminadoLocal(){try(var c=contexto(Map.of())){c.refresh();assertInstanceOf(BusquedaPublicaCanalAdapter.class,c.getBean(BuscarSolucionesCanalPort.class));assertInstanceOf(PrepararRespuestaPublicaAdapter.class,c.getBean(PrepararRespuestaPublicaPort.class));}}
 @Test void httpExplicitoNoUsaQA(){try(var c=contexto(Map.of("dimabug.whatsapp.catalogo.modo","http-principal"))){c.refresh();assertInstanceOf(BusquedaPublicaHttpAdapter.class,c.getBean(BuscarSolucionesCanalPort.class));assertInstanceOf(PrepararRespuestaPublicaHttpAdapter.class,c.getBean(PrepararRespuestaPublicaPort.class));verifyNoInteractions(c.getBean(ConsultarChatbotService.class),c.getBean(AutoservicioPublicoService.class));}}
 @Test void rechazaAutoconsulta(){try(var c=contexto(Map.of("dimabug.whatsapp.catalogo.modo","http-principal","server.port","8081"))){assertThrows(RuntimeException.class,c::refresh);}}
 @Test void rechazaModoDesconocido(){try(var c=contexto(Map.of("dimabug.whatsapp.catalogo.modo","otro"))){assertThrows(RuntimeException.class,c::refresh);}}
 @Test void localNoInterpretaIdsPrincipales(){
  var s=mock(AutoservicioPublicoService.class);var r=new cl.casol.backend.whatsapp.domain.RespuestaCanal(cl.casol.backend.whatsapp.domain.RespuestaCanal.Estado.RESULTADOS,"Resultado",null,java.util.List.of(new cl.casol.backend.whatsapp.domain.RespuestaCanal.Solucion(1,"x","x","principal-publico-v1@http://127.0.0.1:8081")),java.util.List.of(),java.util.List.of());
  assertTrue(new PrepararRespuestaPublicaAdapter(s).preparar(r).conocimientos().isEmpty());verifyNoInteractions(s);
 }
}
