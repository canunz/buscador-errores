package cl.casol.backend.tickets.application.service;
import cl.casol.backend.tickets.application.port.in.*;
import cl.casol.backend.tickets.application.port.out.*;
import cl.casol.backend.tickets.domain.*;
import cl.casol.backend.tickets.domain.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class CrearSolicitudServiceTest {
 final EscenarioTickets e=new EscenarioTickets();
 final CrearTicketUseCase crear=mock(CrearTicketUseCase.class);
 final CatalogoSolicitantePort catalogos=mock(CatalogoSolicitantePort.class);
 final CrearSolicitudService.DatosEntrada entrada=new CrearSolicitudService.DatosEntrada("Persona QA",1,2,"+56 9 1234 5678",null,null);
 final DatosSolicitante snapshot=new DatosSolicitante("Persona QA",1,"Sucursal QA",2,"Área QA","+56 9 1234 5678",null,null);
 @BeforeEach void catalogosActivos(){
  when(catalogos.sucursalActiva(1)).thenReturn(Optional.of(new CatalogoSolicitantePort.Opcion(1,"Sucursal QA")));
  when(catalogos.departamentoActivo(2)).thenReturn(Optional.of(new CatalogoSolicitantePort.Opcion(2,"Área QA")));
 }
 final CrearSolicitudService service=new CrearSolicitudService(crear,e.contexto,e.adjuntos,e.almacen,catalogos,5,5242880);
 byte[] foto(String formato) throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),formato,out);return out.toByteArray();}
 void transaccion(){TransactionSynchronizationManager.initSynchronization();}
 void terminar(int status){for(var s:TransactionSynchronizationManager.getSynchronizations())s.afterCompletion(status);TransactionSynchronizationManager.clearSynchronization();}
 @AfterEach void limpiar(){if(TransactionSynchronizationManager.isSynchronizationActive())terminar(TransactionSynchronization.STATUS_ROLLED_BACK);}
 @Test void reconoceFotosRealesYRechazaFirmasFalsasPdfGifYExceso() throws Exception {
  service.validar(foto("png"));service.validar(foto("jpg"));
  try(var in=getClass().getResourceAsStream("/tickets/foto.webp")){service.validar(in.readAllBytes());}
  for(byte[] b:List.of(new byte[0],new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff},"%PDF-abc".getBytes(),"GIF89a".getBytes(),new byte[5242881]))
   assertThrows(TicketInvalidoException.class,()->service.validar(b));
 }
 @Test void cantidadInvalidaNoCreaTicketNiArchivos(){
  assertThrows(TicketInvalidoException.class,()->service.crear("T","D",Collections.nCopies(6,new CrearSolicitudService.Foto("x",new byte[1])),entrada,EscenarioTickets.EMAIL_USUARIO));
  verifyNoInteractions(crear,e.almacen);
 }
 @Test void creaConPrioridadMediaYConservaArchivoSoloTrasCommit() throws Exception {
  transaccion();var ticket=e.ticket(EstadoTicket.ABIERTO,null);
  when(crear.crear(any(),any())).thenReturn(new TicketVista(ticket,List.of(),Map.of(),null,null,List.of()));
  when(e.almacen.guardar(any())).thenReturn("ref");
  service.crear("T","D",List.of(new CrearSolicitudService.Foto("../../foto.png",foto("png"))),entrada,EscenarioTickets.EMAIL_USUARIO);
  verify(crear).crear(eq(new CrearTicketUseCase.Comando("T","D",PrioridadTicket.MEDIA,null,null,null,snapshot)),eq(EscenarioTickets.EMAIL_USUARIO));
  verify(e.adjuntos).guardar(argThat(a->a.nombre().equals("foto.png")&&a.comentarioId()==null));
  terminar(TransactionSynchronization.STATUS_COMMITTED);verify(e.almacen,never()).eliminar(any());
 }
 @Test void falloPersistenciaYLuegoRollbackEliminaArchivo() throws Exception {
  transaccion();var ticket=e.ticket(EstadoTicket.ABIERTO,null);
  when(crear.crear(any(),any())).thenReturn(new TicketVista(ticket,List.of(),Map.of(),null,null,List.of()));
  when(e.almacen.guardar(any())).thenReturn("ref");when(e.adjuntos.guardar(any())).thenThrow(new IllegalStateException());
  byte[] b=foto("png");assertThrows(IllegalStateException.class,()->service.crear("T","D",List.of(new CrearSolicitudService.Foto("f.png",b)),entrada,EscenarioTickets.EMAIL_USUARIO));
  terminar(TransactionSynchronization.STATUS_ROLLED_BACK);verify(e.almacen).eliminar("ref");
 }
 @Test void commitFallidoTambienCompensa() throws Exception {
  transaccion();var ticket=e.ticket(EstadoTicket.ABIERTO,null);
  when(crear.crear(any(),any())).thenReturn(new TicketVista(ticket,List.of(),Map.of(),null,null,List.of()));when(e.almacen.guardar(any())).thenReturn("ref");
  service.crear("T","D",List.of(new CrearSolicitudService.Foto("f.jpg",foto("jpg"))),entrada,EscenarioTickets.EMAIL_USUARIO);
  terminar(TransactionSynchronization.STATUS_ROLLED_BACK);verify(e.almacen).eliminar("ref");
 }
 @Test void rollbackRealH2RevierteFilasYCompensaArchivo() throws Exception {
  var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:portal_rollback;DB_CLOSE_DELAY=-1","sa","");
  var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE IF NOT EXISTS solicitud_simulada(id INT PRIMARY KEY)");
  jdbc.execute("DELETE FROM solicitud_simulada");
  var tx=new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
  var ticket=e.ticket(EstadoTicket.ABIERTO,null);
  when(crear.crear(any(),any())).thenAnswer(i->{jdbc.update("INSERT INTO solicitud_simulada VALUES(1)");return new TicketVista(ticket,List.of(),Map.of(),null,null,List.of());});
  when(e.almacen.guardar(any())).thenReturn("ref");
  when(e.adjuntos.guardar(any())).thenThrow(new IllegalStateException("fallo simulado"));
  byte[] bytes=foto("png");
  assertThrows(IllegalStateException.class,()->tx.execute(status->service.crear("T","D",List.of(new CrearSolicitudService.Foto("f.png",bytes)),entrada,EscenarioTickets.EMAIL_USUARIO)));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_simulada",Integer.class));
  verify(e.almacen).eliminar("ref");
 }
 @Test void resultadoDesconocidoNoBorraArchivosQuePudieronConfirmarse() throws Exception {
  transaccion();var ticket=e.ticket(EstadoTicket.ABIERTO,null);
  when(crear.crear(any(),any())).thenReturn(new TicketVista(ticket,List.of(),Map.of(),null,null,List.of()));when(e.almacen.guardar(any())).thenReturn("ref");
  service.crear("T","D",List.of(new CrearSolicitudService.Foto("f.jpg",foto("jpg"))),entrada,EscenarioTickets.EMAIL_USUARIO);
  terminar(TransactionSynchronization.STATUS_UNKNOWN);verify(e.almacen,never()).eliminar(any());
 }
 @Test void proyeccionBasicaNoExponeAutoresReferenciasNiFotosDeOtros() {
  var ticket=e.ticket(EstadoTicket.ABIERTO,null);
  var own=new AdjuntoTicket(1L,100L,null,"propia.png","image/png",1,"privado",1,null);
  var other=new AdjuntoTicket(2L,100L,null,"otra.png","image/png",1,"privado",3,null);
  var internal=new AdjuntoTicket(3L,100L,5L,"interna.png","image/png",1,"privado",1,null);
  var v=new TicketVista(ticket,List.of(),Map.of(),null,null,List.of(),List.of(own,other,internal)).paraBasico();
  assertEquals(1,v.adjuntos().size());assertNull(v.adjuntos().getFirst().autorId());assertNull(v.adjuntos().getFirst().referencia());
 }
 @Test void prioridadInyectadaEsIgnoradaPorElServicioReal() {
  when(e.usuarios.buscarPorEmail(EscenarioTickets.EMAIL_USUARIO)).thenReturn(Optional.of(new UsuarioTicket(1,"QA",EscenarioTickets.EMAIL_USUARIO,RolTicket.USUARIO,true,true)));
  var real=new CrearTicketService(e.tickets,e.catalogo,e.contexto);
  var resultado=real.crear(new CrearTicketUseCase.Comando("T","D",PrioridadTicket.CRITICA,null,null,null,snapshot),EscenarioTickets.EMAIL_USUARIO);
  assertEquals(PrioridadTicket.MEDIA,resultado.ticket().getPrioridad());
 }
 @Test void selectorInexistenteOInactivoNoCreaNiEscribeFotos() {
  var entradaInvalida=new CrearSolicitudService.DatosEntrada("Persona",999,2,"1234",null,null);
  assertThrows(TicketInvalidoException.class,()->service.crear("T","D",List.of(),entradaInvalida,EscenarioTickets.EMAIL_USUARIO));
  verifyNoInteractions(crear,e.almacen);
 }
 @Test void endpointLegadoNoPermiteOmitirDatosDeAutorregistrado() {
  when(e.usuarios.buscarPorEmail(EscenarioTickets.EMAIL_USUARIO)).thenReturn(Optional.of(new UsuarioTicket(1,"QA",EscenarioTickets.EMAIL_USUARIO,RolTicket.USUARIO,true,true)));
  var real=new CrearTicketService(e.tickets,e.catalogo,e.contexto);
  assertThrows(TicketInvalidoException.class,()->real.crear(new CrearTicketUseCase.Comando("T","D",PrioridadTicket.CRITICA,null,null,null),EscenarioTickets.EMAIL_USUARIO));
  verify(e.tickets,never()).guardar(any());
 }
 @Test void basicoSoloDescargaFotosPropiasSinComentarios() {
  when(e.usuarios.buscarPorEmail(EscenarioTickets.EMAIL_USUARIO)).thenReturn(Optional.of(new UsuarioTicket(1,"QA",EscenarioTickets.EMAIL_USUARIO,RolTicket.USUARIO,true,true)));
  e.ticket(EstadoTicket.ABIERTO,null);var descargar=new AdjuntarArchivoTicketService(e.contexto,e.adjuntos,e.almacen);
  when(e.adjuntos.buscar(100L,1L)).thenReturn(Optional.of(new AdjuntoTicket(1L,100L,null,"foto.png","image/png",10,"ref",2,null)));
  assertThrows(AdjuntoNoEncontradoException.class,()->descargar.descargar(100L,1L,EscenarioTickets.EMAIL_USUARIO));verifyNoInteractions(e.almacen);
  assertThrows(TicketNoEncontradoException.class,()->descargar.descargar(100L,1L,EscenarioTickets.EMAIL_OTRO));
 }
}
