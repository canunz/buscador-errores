package cl.casol.backend;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.shared.application.service.VisibilidadContenidoService;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import cl.casol.backend.tickets.application.service.CrearSolicitudService;
import cl.casol.backend.tickets.application.port.in.*;
import cl.casol.backend.tickets.domain.*;
import cl.casol.backend.tickets.infrastructure.web.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({SolicitudController.class,AdjuntoTicketController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class,TicketExceptionHandler.class})
class SolicitudPortalHttpTest {
 @Autowired MockMvc mvc;
 @MockitoBean CrearSolicitudService service;
 @MockitoBean AdjuntarArchivoTicketUseCase adjuntos;
 @MockitoBean TokenServicePort tokens;
 @MockitoBean UsuarioRepository usuarios;
 @MockitoBean UsuarioAutenticadoCache usuarioAutenticadoCache;
 @MockitoBean VisibilidadContenidoService visibilidadContenidoService;
 void auth(){
  when(tokens.esValido("x")).thenReturn(true);when(tokens.obtenerEmail("x")).thenReturn("qa@example.invalid");
  when(usuarioAutenticadoCache.obtener(anyString())).thenReturn(Optional.empty());
  when(usuarios.buscarPorEmail("qa@example.invalid")).thenReturn(Optional.of(new Usuario(7,new Rol(1,"USUARIO",null,true,null,null),"QA","qa@example.invalid","unused",true,null,null,EstadoRegistro.AUTO_CONFIRMADO)));
  when(service.maxFotos()).thenReturn(5);when(service.maxBytes()).thenReturn(5242880L);
 }
 @Test void anonimoNoSubeNiDescarga() throws Exception {
  mvc.perform(multipart("/api/tickets/solicitudes").param("titulo","T").param("descripcion","D").param("nombreCompleto","Persona QA").param("sucursalId","1").param("departamentoId","2").param("contacto","1234")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/tickets/1/adjuntos/1")).andExpect(status().isUnauthorized());verifyNoInteractions(service,adjuntos);
 }
 @Test void usuarioCreaSolicitudConFotosYPrioridadMedia() throws Exception {
  auth();var t=Ticket.crear("TK-TEST","T","D",PrioridadTicket.MEDIA,7,null,null,null,LocalDateTime.now());
  when(service.crear(any(),any(),any(),any(),any())).thenReturn(new TicketVista(t,List.of(),Map.of(),null,null,List.of()));
  mvc.perform(multipart("/api/tickets/solicitudes").file(new MockMultipartFile("fotos","foto.png","image/png",new byte[]{1})).param("titulo","T").param("descripcion","D").param("nombreCompleto","Persona QA").param("sucursalId","1").param("departamentoId","2").param("contacto","1234").param("prioridad","CRITICA").param("solicitanteId","99").header("Authorization","Bearer x"))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.prioridad").value("MEDIA"));
  verify(service).crear(eq("T"),eq("D"),argThat(f->f.size()==1),argThat(d->d.sucursalId()==1 && d.nombreCompleto().equals("Persona QA")),eq("qa@example.invalid"));
  // El autorregistro confirmado ya es un usuario normal, por eso aquí no se prueban rutas internas.
 }
 @Test void limitesYDescargaPrivadaConAutorizacionDeTicket() throws Exception {
  auth();mvc.perform(get("/api/tickets/solicitudes/limites").header("Authorization","Bearer x")).andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(5));
  when(adjuntos.descargar(1L,2L,"qa@example.invalid")).thenReturn(new AdjuntarArchivoTicketUseCase.ArchivoAdjunto("foto.png","image/png",new byte[]{1,2}));
  mvc.perform(get("/api/tickets/1/adjuntos/2").header("Authorization","Bearer x")).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"));
  when(adjuntos.descargar(1L,3L,"qa@example.invalid")).thenThrow(new cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException(1L));
  mvc.perform(get("/api/tickets/1/adjuntos/3").header("Authorization","Bearer x")).andExpect(status().isNotFound());
 }
 @Test void demasiadasFotosOTituloVacioNoLleganAlServicio() throws Exception {
  auth();mvc.perform(multipart("/api/tickets/solicitudes").param("titulo","").param("descripcion","D").param("nombreCompleto","Persona QA").param("sucursalId","1").param("departamentoId","2").param("contacto","1234").header("Authorization","Bearer x")).andExpect(status().isBadRequest());
  var req=multipart("/api/tickets/solicitudes").param("titulo","T").param("descripcion","D").param("nombreCompleto","Persona QA").param("sucursalId","1").param("departamentoId","2").param("contacto","1234").header("Authorization","Bearer x");
  for(int i=0;i<6;i++)req.file(new MockMultipartFile("fotos","a.png","image/png",new byte[]{1}));
  mvc.perform(req).andExpect(status().isBadRequest());verify(service,never()).crear(any(),any(),any(),any(),any());
 }
 @Test void camposObligatoriosAusentesSon400AunqueLaPeticionSeManipule() throws Exception {
  auth();
  for(String missing:List.of("nombreCompleto","sucursalId","departamentoId","contacto")) {
   var req=multipart("/api/tickets/solicitudes").param("titulo","T").param("descripcion","D").header("Authorization","Bearer x");
   for(var field:Map.of("nombreCompleto","Persona QA","sucursalId","1","departamentoId","2","contacto","1234").entrySet())
    if(!field.getKey().equals(missing)) req.param(field.getKey(),field.getValue());
   mvc.perform(req).andExpect(status().isBadRequest());
  }
  verify(service,never()).crear(any(),any(),any(),any(),any());
 }
 @Test void catalogosSoloExponenIdYNombreYDirectoriosSiguenBloqueados() throws Exception {
  auth();
  when(service.catalogos(any())).thenReturn(new CrearSolicitudService.Catalogos(
   List.of(new cl.casol.backend.tickets.application.port.out.CatalogoSolicitantePort.Opcion(1,"Sucursal QA")),
   List.of(new cl.casol.backend.tickets.application.port.out.CatalogoSolicitantePort.Opcion(2,"Área QA"))));
  mvc.perform(get("/api/tickets/solicitudes/catalogos").header("Authorization","Bearer x"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.sucursales[0].nombre").value("Sucursal QA"))
   .andExpect(jsonPath("$.sucursales[0].email").doesNotExist()).andExpect(jsonPath("$.sucursales[0].responsableNombre").doesNotExist());
  for(String path:List.of("/api/sucursales","/api/departamentos","/api/usuarios"))
   mvc.perform(get(path).header("Authorization","Bearer x")).andExpect(status().isForbidden());
  mvc.perform(get("/api/tickets/solicitudes/catalogos")).andExpect(status().isUnauthorized());
 }

}
