package cl.casol.backend.tickets.domain;
import org.junit.jupiter.api.Test;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class DatosSolicitanteTest {
 @Test void telefonoChilenoYAnexosHabituales() {
  for(String s:List.of("+56 9 1234 5678","(+56) 65 222 3344","anexo 1234","1234","ext. 1234","600 123 4567","+56 2 2345 6789 anexo 1234"))
   assertTrue(DatosSolicitante.contactoValido(s),s);
  for(String s:List.of("","9","contacto@example.invalid","llamar","<script>","1".repeat(19)))
   assertFalse(DatosSolicitante.contactoValido(s),s);
 }
 @Test void ipsYAnyDeskLiteralesSinResolverHostnames() {
  for(String s:List.of("192.168.1.10","0.0.0.0","255.255.255.255","2001:db8::1","::1","::","123 456 789","1234567890"))
   assertTrue(DatosSolicitante.equipoValido(s),s);
  for(String s:List.of("999.168.0.1","192.168.1","01.2.3.4","2001:::1",":1::","::1:","1:2:3","1:2:3:4:5:6:7:8:9","ejemplo.cl","https://192.168.1.1","usuario@ad","fe80::1%eth0"))
   assertFalse(DatosSolicitante.equipoValido(s),s);
 }
 @Test void opcionalesNoObligatoriosYLongitudesControladas() {
  var d=new DatosSolicitante("  Persona QA  ",1,"Sucursal",2,"Área","anexo 1234","  ",null);
  assertEquals("Persona QA",d.nombreCompleto());assertNull(d.equipo());assertNull(d.codigoUsuario());
  assertThrows(TicketInvalidoException.class,()->new DatosSolicitante("",1,"S",2,"D","1234",null,null));
  assertThrows(TicketInvalidoException.class,()->new DatosSolicitante("Persona",1,"S",2,"D","",null,null));
  assertThrows(TicketInvalidoException.class,()->new DatosSolicitante("Persona",1,"S",2,"D","1234","999.1.1.1",null));
  assertThrows(TicketInvalidoException.class,()->new DatosSolicitante("Persona",1,"S",2,"D","1234",null,"x".repeat(81)));
 }
}
