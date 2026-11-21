package cl.casol.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.security.AlcanceBasicoFilter;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.domain.*;
import cl.casol.backend.tickets.infrastructure.web.dto.*;
import java.time.LocalDateTime;
import java.util.*;

class RegistroUniversalSeguridadTest {
 @Test void limitePorConexionNoSeEludeConCabeceraDeIpInventada() throws Exception {
  var filtro=new cl.casol.backend.shared.infrastructure.security.LimiteAutenticacionFilter(1);
  var llamadas=new java.util.concurrent.atomic.AtomicInteger();
  for(int i=0;i<2;i++) {
   var req=new org.springframework.mock.web.MockHttpServletRequest("POST","/api/auth/codigo");
   req.setServletPath("/api/auth/codigo");req.setRemoteAddr("127.0.0.1");req.addHeader("X-Forwarded-For","192.0.2."+i);req.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var res=new org.springframework.mock.web.MockHttpServletResponse();
   filtro.doFilter(req,res,(a,b)->llamadas.incrementAndGet());
   assertEquals(i==0?200:429,res.getStatus());
  }
  assertEquals(1,llamadas.get());
 }
 @Test void estadosInconsistentesNuncaAutorizanJWT() {
  Rol staff=new Rol(1,"ADMINISTRADOR","",true,null,null);
  for(EstadoRegistro estado:List.of(EstadoRegistro.AUTO_PENDIENTE,EstadoRegistro.AUTO_CANCELADO,EstadoRegistro.AUTO_CONFIRMADO))
   assertFalse(new Usuario(1,staff,"Prueba","prueba@example.invalid","hash",true,null,null,estado).puedeIngresar());
 }
 @Test void allowlistRechazaTodoMetodoORutaNoPrevisto() {
  for(String p:List.of("/api/tickets/sugerencias","/api/tickets/1/adjuntos","/api/conocimientos","/api/departamentos","/api/ejecuciones","/api/usuarios","/api/tickets/1/estado","/api/admin/gmail"))
   for(String m:List.of("GET","POST","PUT","PATCH","DELETE"))assertFalse(AlcanceBasicoFilter.permitido(m,p));
  assertTrue(AlcanceBasicoFilter.permitido("GET","/api/tickets/1"));
  assertTrue(AlcanceBasicoFilter.permitido("POST","/api/tickets"));
 }
 @Test void proyeccionEliminaSolucionAsignadoClasificacionEtiquetasHistorialYConocimiento() {
  Ticket t=new Ticket(1L,"TK-TEST","Propio","Descripción",EstadoTicket.RESUELTO,PrioridadTicket.MEDIA,1,2,3,4,5,6,
    "solución interna",LocalDateTime.now(),LocalDateTime.now(),null,null,Set.of(7L),List.of(),List.of());
  TicketVista v=new TicketVista(t,List.of(),Map.of(),null,null,List.of()).paraBasico();
  var d=TicketDetalleResponse.from(v);var r=TicketResumenResponse.from(v);
  assertNull(d.asignado());assertNull(d.hardware());assertNull(d.sistema());assertNull(d.modulo());assertNull(d.conocimiento());assertNull(d.solucion());
  assertTrue(d.historial().isEmpty());assertTrue(d.comentarios().isEmpty());assertTrue(d.adjuntos().isEmpty());assertTrue(d.etiquetas().isEmpty());
  assertNull(r.asignado());assertTrue(r.etiquetas().isEmpty());assertEquals("Propio",d.titulo());
 }
}
