package cl.casol.backend;
import cl.casol.backend.shared.infrastructure.security.LimiteAutoservicioFilter;
import org.junit.jupiter.api.Test;import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class LimiteAutoservicioFilterTest {
 @Test void limitaChatbotSinConfiarEnForwardedFor()throws Exception{
  var f=new LimiteAutoservicioFilter();
  for(int i=0;i<11;i++){var r=new MockHttpServletRequest("POST","/api/publico/chatbot/consultas");r.setRemoteAddr("192.0.2.1");r.addHeader("X-Forwarded-For","192.0.2."+i);var s=new MockHttpServletResponse();f.doFilter(r,s,new MockFilterChain());assertEquals(i<10?200:429,s.getStatus());if(i==10)assertEquals("60",s.getHeader("Retry-After"));}
 }
 @Test void limitaLecturasYNoAfectaRutasPrivadas()throws Exception{
  var f=new LimiteAutoservicioFilter();
  for(int i=0;i<61;i++){var s=new MockHttpServletResponse();f.doFilter(new MockHttpServletRequest("GET","/api/publico/soluciones"),s,new MockFilterChain());assertEquals(i<60?200:429,s.getStatus());}
  var s=new MockHttpServletResponse();f.doFilter(new MockHttpServletRequest("GET","/api/admin/soluciones"),s,new MockFilterChain());assertEquals(200,s.getStatus());
 }
 @Test void rechazaPayloadGrandeAntesDeConsultar()throws Exception{var f=new LimiteAutoservicioFilter();var r=new MockHttpServletRequest("POST","/api/publico/chatbot/consultas");r.setContent(new byte[9000]);var s=new MockHttpServletResponse();f.doFilter(r,s,new MockFilterChain());assertEquals(413,s.getStatus());}
 @Test void elLimiteDeBytesTambienSeAplicaSinContentLength()throws Exception {
  var f=new LimiteAutoservicioFilter();var r=new MockHttpServletRequest("POST","/api/publico/chatbot/consultas"){@Override public long getContentLengthLong(){return -1;}};r.setContent(new byte[9000]);var s=new MockHttpServletResponse();f.doFilter(r,s,new MockFilterChain());assertEquals(413,s.getStatus());
 }

}
