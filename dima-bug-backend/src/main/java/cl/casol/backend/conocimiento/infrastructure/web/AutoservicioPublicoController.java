package cl.casol.backend.conocimiento.infrastructure.web;
import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import cl.casol.backend.shared.infrastructure.web.ArchivoHttp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @Validated @RequestMapping("/api/publico")
public class AutoservicioPublicoController {
 private final AutoservicioPublicoService service;private final ConsultarChatbotService chatbot;
 public AutoservicioPublicoController(AutoservicioPublicoService s,ConsultarChatbotService c){service=s;chatbot=c;}
 public record SolucionPublica(Integer id,String titulo,String instrucciones,TipoSolucion tipo){static SolucionPublica from(Solucion s){return new SolucionPublica(s.id(),s.titulo()==null?"Solución de autoservicio":s.titulo(),s.descripcion(),s.tipo());}}
 @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
 public org.springframework.http.ResponseEntity<Map<String,String>> invalida(Exception e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message","Consulta no válida"));}
 @GetMapping("/soluciones") public List<SolucionPublica> listar(@RequestParam(defaultValue="") @Size(max=150) String q){return service.buscar(q).stream().map(SolucionPublica::from).toList();}
 @GetMapping("/soluciones/{id}") public SolucionPublica detalle(@PathVariable Integer id){return SolucionPublica.from(service.publica(id));}
 @GetMapping("/soluciones/{id}/materiales") public List<MaterialSolucionResponse> materiales(@PathVariable Integer id){return service.materiales(id,true).stream().map(m->MaterialSolucionResponse.from(m,true)).toList();}
 @GetMapping("/soluciones/{id}/materiales/{material}/archivo") public org.springframework.http.ResponseEntity<byte[]> archivo(@PathVariable Integer id,@PathVariable Integer material){return ArchivoHttp.respuesta(service.descargar(id,material,true));}
 @PostMapping("/chatbot/consultas") public ChatbotResponse consultar(@Valid @RequestBody ConsultaChatbotRequest r){return ChatbotResponse.from(chatbot.consultar(r.consulta()).stream().map(s->new ConsultarChatbotService.SolucionChatbot("Autoservicio",s.titulo(),s.solucion())).toList());}
}
