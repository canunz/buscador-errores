package cl.casol.backend.ejecucion.application.service;

import cl.casol.backend.ejecucion.application.port.out.*;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.ProcedimientoNoEncontradoException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestionarEjecucionServiceTest {
 @Mock EjecucionRepository ejecuciones; @Mock EjecucionPasoRepository ejecucionPasos;
 @Mock ProcedimientoRepository procedimientos; @Mock PasoRepository pasos; @Mock UsuarioRepository usuarios;
 @InjectMocks GestionarEjecucionService service;
 Usuario tecnico, admin, otro; Procedimiento publicado; LocalDateTime inicio;
 @BeforeEach void setup(){tecnico=user(7,"TECNICO");admin=user(1,"ADMINISTRADOR");otro=user(8,"TECNICO");inicio=LocalDateTime.now();
  publicado=new Procedimiento(10,"P",null,EstadoProcedimiento.PUBLICADO,tecnico,inicio,null,null);}

 @Test void tecnicoIniciaPublicadoConDatosDelAutenticadoYChecklist(){inicioValido(tecnico,List.of(paso(1,1),paso(2,2)));
  Ejecucion r=service.iniciar(10,"u7@x.cl"); assertEquals(EstadoEjecucion.EN_CURSO,r.estado());assertNull(r.fechaFin());assertNotNull(r.fechaInicio());assertEquals(7,r.usuario().getId());
  ArgumentCaptor<List<EjecucionPaso>> c=ArgumentCaptor.forClass(List.class);verify(ejecucionPasos).guardarTodos(c.capture());assertEquals(2,c.getValue().size());assertTrue(c.getValue().stream().noneMatch(EjecucionPaso::cumplido));}
 @Test void administradorIniciaPublicado(){inicioValido(admin,List.of(paso(1,1)));assertEquals(1,service.iniciar(10,"u1@x.cl").usuario().getId());}
 @Test void borradorNoInicia(){when(procedimientos.buscarPorId(10)).thenReturn(Optional.of(new Procedimiento(10,"P",null,EstadoProcedimiento.BORRADOR,tecnico,inicio,null,null)));assertThrows(ReglaEjecucionException.class,()->service.iniciar(10,"x"));verifyNoInteractions(ejecuciones);}
 @Test void procedimientoInexistente(){when(procedimientos.buscarPorId(10)).thenReturn(Optional.empty());assertThrows(ProcedimientoNoEncontradoException.class,()->service.iniciar(10,"x"));}
 @Test void procedimientoSinPasosNoCreaEjecucion(){when(procedimientos.buscarPorId(10)).thenReturn(Optional.of(publicado));when(pasos.buscarPorProcedimientoOrdenados(10)).thenReturn(List.of());assertThrows(ReglaEjecucionException.class,()->service.iniciar(10,"x"));verifyNoInteractions(ejecuciones);}
 @Test void iniciarEsTransaccional() throws Exception {assertNotNull(GestionarEjecucionService.class.getMethod("iniciar",Integer.class,String.class).getAnnotation(Transactional.class));}
 @Test void listaChecklistOrdenadoConDefinicion(){auth(tecnico);when(ejecuciones.buscarPorId(20)).thenReturn(Optional.of(run(20,tecnico,EstadoEjecucion.EN_CURSO)));when(ejecucionPasos.buscarPorEjecucion(20)).thenReturn(List.of(ep(102,2,false),ep(101,1,false)));when(pasos.buscarPorId(1)).thenReturn(Optional.of(paso(1,1)));when(pasos.buscarPorId(2)).thenReturn(Optional.of(new Paso(2,10,2,"Segunda",true)));var r=service.listarPasos(20,"u7@x.cl");assertEquals(1,r.get(0).orden());assertEquals("Paso 1",r.get(0).instruccion());assertTrue(r.get(1).esCritico());}
 @Test void marcarRegistraFechaYObservacion(){prepararActualizacion(tecnico,EstadoEjecucion.EN_CURSO,ep(101,1,false));when(pasos.buscarPorId(1)).thenReturn(Optional.of(paso(1,1)));when(ejecucionPasos.guardar(any())).thenAnswer(i->i.getArgument(0));var r=service.actualizarPaso(20,101,true,"OK","u7@x.cl").ejecucionPaso();assertTrue(r.cumplido());assertNotNull(r.fecha());assertEquals("OK",r.observacion());}
 @Test void desmarcarLimpiaFecha(){EjecucionPaso p=new EjecucionPaso(101,20,1,true,null,inicio);prepararActualizacion(tecnico,EstadoEjecucion.EN_CURSO,p);when(pasos.buscarPorId(1)).thenReturn(Optional.of(paso(1,1)));when(ejecucionPasos.guardar(any())).thenAnswer(i->i.getArgument(0));assertNull(service.actualizarPaso(20,101,false,null,"u7@x.cl").ejecucionPaso().fecha());}
 @Test void pasoDeOtraEjecucionNoSeModifica(){prepararActualizacion(tecnico,EstadoEjecucion.EN_CURSO,new EjecucionPaso(101,99,1,false,null,null));assertThrows(EjecucionPasoNoEncontradoException.class,()->service.actualizarPaso(20,101,true,null,"u7@x.cl"));verify(ejecucionPasos,never()).guardar(any());}
 @Test void noModificaCompletada(){prepararActualizacion(tecnico,EstadoEjecucion.COMPLETADA,ep(101,1,false));assertThrows(ReglaEjecucionException.class,()->service.actualizarPaso(20,101,true,null,"u7@x.cl"));}
 @Test void noModificaCancelada(){prepararActualizacion(tecnico,EstadoEjecucion.CANCELADA,ep(101,1,false));assertThrows(ReglaEjecucionException.class,()->service.actualizarPaso(20,101,true,null,"u7@x.cl"));}
 @Test void completaSiTodosCumplidos(){prepararCierre(tecnico,EstadoEjecucion.EN_CURSO,List.of(new EjecucionPaso(1,20,1,true,null,inicio)));when(ejecuciones.guardar(any())).thenAnswer(i->i.getArgument(0));Ejecucion r=service.completar(20,"u7@x.cl");assertEquals(EstadoEjecucion.COMPLETADA,r.estado());assertNotNull(r.fechaFin());}
 @Test void noCompletaConPendiente(){prepararCierre(tecnico,EstadoEjecucion.EN_CURSO,List.of(ep(1,1,false)));assertThrows(ReglaEjecucionException.class,()->service.completar(20,"u7@x.cl"));}
 @Test void cancelaSinExigirPasosYGuardaObservacion(){auth(tecnico);when(ejecuciones.buscarPorId(20)).thenReturn(Optional.of(run(20,tecnico,EstadoEjecucion.EN_CURSO)));when(ejecuciones.guardar(any())).thenAnswer(i->i.getArgument(0));Ejecucion r=service.cancelar(20,"motivo","u7@x.cl");assertEquals(EstadoEjecucion.CANCELADA,r.estado());assertNotNull(r.fechaFin());assertEquals("motivo",r.observaciones());verifyNoInteractions(ejecucionPasos);}
 @Test void noCompletaCancelada(){prepararCierre(tecnico,EstadoEjecucion.CANCELADA,List.of());assertThrows(ReglaEjecucionException.class,()->service.completar(20,"u7@x.cl"));}
 @Test void noCancelaCompletada(){prepararCierre(tecnico,EstadoEjecucion.COMPLETADA,List.of());assertThrows(ReglaEjecucionException.class,()->service.cancelar(20,null,"u7@x.cl"));}
 @Test void tecnicoNoConsultaNiModificaAjena(){auth(tecnico);when(ejecuciones.buscarPorId(20)).thenReturn(Optional.of(run(20,otro,EstadoEjecucion.EN_CURSO)));assertThrows(AccessDeniedException.class,()->service.buscar(20,"u7@x.cl"));assertThrows(AccessDeniedException.class,()->service.cancelar(20,null,"u7@x.cl"));}
 @Test void administradorGestionaAjena(){auth(admin);when(ejecuciones.buscarPorId(20)).thenReturn(Optional.of(run(20,otro,EstadoEjecucion.EN_CURSO)));when(ejecuciones.guardar(any())).thenAnswer(i->i.getArgument(0));assertEquals(EstadoEjecucion.CANCELADA,service.cancelar(20,null,"u1@x.cl").estado());}
 @Test void listadoTecnicoFiltraPorUsuario(){auth(tecnico);when(ejecuciones.buscarPorUsuario(7)).thenReturn(List.of());service.listar("u7@x.cl");verify(ejecuciones).buscarPorUsuario(7);verify(ejecuciones,never()).buscarTodas();}
 @Test void listadoAdminIncluyeTodas(){auth(admin);when(ejecuciones.buscarTodas()).thenReturn(List.of());service.listar("u1@x.cl");verify(ejecuciones).buscarTodas();}
 @Test void checklistExistenteNoSeRegenera(){auth(tecnico);when(ejecuciones.buscarPorId(20)).thenReturn(Optional.of(run(20,tecnico,EstadoEjecucion.EN_CURSO)));when(ejecucionPasos.buscarPorEjecucion(20)).thenReturn(List.of(ep(1,1,false)));when(pasos.buscarPorId(1)).thenReturn(Optional.of(paso(1,1)));assertEquals(1,service.listarPasos(20,"u7@x.cl").size());verify(pasos,never()).buscarPorProcedimientoOrdenados(anyInt());}

 void inicioValido(Usuario u,List<Paso> ps){when(procedimientos.buscarPorId(10)).thenReturn(Optional.of(publicado));when(pasos.buscarPorProcedimientoOrdenados(10)).thenReturn(ps);auth(u);when(ejecuciones.guardar(any())).thenAnswer(i->{Ejecucion e=i.getArgument(0);return new Ejecucion(20,e.procedimientoId(),e.usuario(),e.fechaInicio(),e.fechaFin(),e.estado(),e.observaciones());});when(ejecucionPasos.guardarTodos(any())).thenAnswer(i->i.getArgument(0));}
 void auth(Usuario u){when(usuarios.buscarPorEmail("u"+u.getId()+"@x.cl")).thenReturn(Optional.of(u));}
 void prepararActualizacion(Usuario u,EstadoEjecucion st,EjecucionPaso ep){auth(u);when(ejecuciones.buscarPorId(20)).thenReturn(Optional.of(run(20,u,st)));if(st==EstadoEjecucion.EN_CURSO)when(ejecucionPasos.buscarPorId(101)).thenReturn(Optional.of(ep));}
 void prepararCierre(Usuario u,EstadoEjecucion st,List<EjecucionPaso> ps){auth(u);when(ejecuciones.buscarPorId(20)).thenReturn(Optional.of(run(20,u,st)));if(st==EstadoEjecucion.EN_CURSO)when(ejecucionPasos.buscarPorEjecucion(20)).thenReturn(ps);}
 Usuario user(int id,String rol){return new Usuario(id,new Rol(1,rol,null,true,null,null),"U"+id,"u"+id+"@x.cl","h",true,null,null);}
 Paso paso(int id,int orden){return new Paso(id,10,orden,"Paso "+id,false);} EjecucionPaso ep(int id,int paso,boolean ok){return new EjecucionPaso(id,20,paso,ok,null,null);}
 Ejecucion run(int id,Usuario u,EstadoEjecucion st){return new Ejecucion(id,10,u,inicio,st==EstadoEjecucion.EN_CURSO?null:inicio,st,null);}
}
