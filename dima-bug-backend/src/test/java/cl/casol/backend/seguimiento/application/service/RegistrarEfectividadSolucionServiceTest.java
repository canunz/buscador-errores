package cl.casol.backend.seguimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.seguimiento.application.port.out.ResultadoSolucionRepository;
import cl.casol.backend.seguimiento.domain.*;
import cl.casol.backend.seguimiento.domain.exception.EstadoConocimientoNoEvaluableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrarEfectividadSolucionServiceTest {
 @Mock ConocimientoRepository conocimientos; @Mock SolucionRepository soluciones;
 @Mock UsuarioRepository usuarios; @Mock ResultadoSolucionRepository resultados;
 @InjectMocks RegistrarEfectividadSolucionService service;
 Usuario tecnico,admin; Solucion solucion;
 @BeforeEach void setup(){tecnico=user(7,"TECNICO");admin=user(1,"ADMINISTRADOR");solucion=new Solucion(5,10,"S",TipoSolucion.PASOS,1);}
 @Test void tecnicoRegistraTrueConSolucionUsuarioYFecha(){valido(EstadoConocimiento.PUBLICADO,tecnico);guardarConId();var r=service.registrar(10,5,true,"ok","u7@x.cl").resultado();assertEquals(5,r.solucionId());assertEquals(7,r.usuarioId());assertTrue(r.funciono());assertEquals("ok",r.comentario());assertNotNull(r.fecha());}
 @Test void tecnicoRegistraFalse(){valido(EstadoConocimiento.PUBLICADO,tecnico);guardarConId();assertFalse(service.registrar(10,5,false,"fallo","u7@x.cl").resultado().funciono());}
 @Test void administradorRegistra(){valido(EstadoConocimiento.PUBLICADO,admin);guardarConId();assertEquals(1,service.registrar(10,5,true,null,"u1@x.cl").usuario().getId());}
 @Test void comentarioOpcional(){valido(EstadoConocimiento.PUBLICADO,tecnico);guardarConId();assertNull(service.registrar(10,5,true,null,"u7@x.cl").resultado().comentario());}
 @Test void tipoDerivacionTambienAceptado(){solucion=new Solucion(5,10,"D",TipoSolucion.DERIVACION,1);valido(EstadoConocimiento.PUBLICADO,tecnico);guardarConId();assertTrue(service.registrar(10,5,true,null,"u7@x.cl").resultado().funciono());}
 @Test void solucionInexistenteNoGuarda(){when(conocimientos.buscarPorIdIncluidoEliminado(10)).thenReturn(Optional.of(conocimiento(EstadoConocimiento.PUBLICADO)));when(soluciones.buscarPorId(5)).thenReturn(Optional.empty());assertThrows(SolucionNoEncontradaException.class,()->service.registrar(10,5,true,null,"x"));verifyNoInteractions(resultados);}
 @Test void conocimientoInexistenteNoGuarda(){when(conocimientos.buscarPorIdIncluidoEliminado(10)).thenReturn(Optional.empty());assertThrows(ConocimientoNoEncontradoException.class,()->service.registrar(10,5,true,null,"x"));verifyNoInteractions(soluciones,resultados);}
 @Test void solucionAjenaSeTrataComoNoEncontrada(){when(conocimientos.buscarPorIdIncluidoEliminado(10)).thenReturn(Optional.of(conocimiento(EstadoConocimiento.PUBLICADO)));when(soluciones.buscarPorId(5)).thenReturn(Optional.of(new Solucion(5,99,"X",TipoSolucion.PASOS,1)));assertThrows(SolucionNoEncontradaException.class,()->service.registrar(10,5,true,null,"x"));verifyNoInteractions(resultados);}
 @Test void borradorNoAceptaResultado(){validoSinUsuario(EstadoConocimiento.BORRADOR);assertThrows(EstadoConocimientoNoEvaluableException.class,()->service.registrar(10,5,true,null,"x"));verifyNoInteractions(resultados);}
 @Test void eliminadoNoAceptaResultado(){validoSinUsuario(EstadoConocimiento.ELIMINADO);assertThrows(EstadoConocimientoNoEvaluableException.class,()->service.registrar(10,5,true,null,"x"));verifyNoInteractions(resultados);}
 @Test void cadaPostInsertaNuevoRegistro(){valido(EstadoConocimiento.PUBLICADO,tecnico);AtomicInteger ids=new AtomicInteger();when(resultados.guardar(any())).thenAnswer(i->{ResultadoSolucion r=i.getArgument(0);return new ResultadoSolucion(ids.incrementAndGet(),r.solucionId(),r.usuarioId(),r.funciono(),r.comentario(),r.fecha());});var a=service.registrar(10,5,true,null,"u7@x.cl").resultado();var b=service.registrar(10,5,false,null,"u7@x.cl").resultado();assertNotEquals(a.id(),b.id());assertTrue(a.funciono());assertFalse(b.funciono());verify(resultados,times(2)).guardar(any());}
 @Test void registrarEsTransaccional() throws Exception {assertNotNull(RegistrarEfectividadSolucionService.class.getMethod("registrar",Integer.class,Integer.class,boolean.class,String.class,String.class).getAnnotation(Transactional.class));}
 @Test void historialVacio(){pertenencia();when(resultados.buscarPorSolucionOrdenados(5)).thenReturn(List.of());assertTrue(service.listar(10,5).isEmpty());}
 @Test void historialConTrueFalseYUsuarios(){pertenencia();when(resultados.buscarPorSolucionOrdenados(5)).thenReturn(List.of(resultado(2,false,8),resultado(1,true,7)));when(usuarios.buscarPorId(8)).thenReturn(Optional.of(user(8,"TECNICO")));when(usuarios.buscarPorId(7)).thenReturn(Optional.of(tecnico));var r=service.listar(10,5);assertFalse(r.get(0).resultado().funciono());assertTrue(r.get(1).resultado().funciono());assertEquals(8,r.get(0).usuario().getId());}
 @Test void historialValidaPertenencia(){when(conocimientos.buscarPorIdIncluidoEliminado(10)).thenReturn(Optional.of(conocimiento(EstadoConocimiento.PUBLICADO)));when(soluciones.buscarPorId(5)).thenReturn(Optional.of(new Solucion(5,11,"X",TipoSolucion.PASOS,1)));assertThrows(SolucionNoEncontradaException.class,()->service.listar(10,5));verifyNoInteractions(resultados);}
 @Test void efectividadDelegaAgregacionSoloParaSolucion(){pertenencia();var e=new EfectividadSolucion(5,4,3,1,75.0);when(resultados.calcularEfectividad(5)).thenReturn(e);assertEquals(e,service.obtenerEfectividad(10,5));verify(resultados).calcularEfectividad(5);}
 @Test void consultaEfectividadValidaConocimiento(){when(conocimientos.buscarPorIdIncluidoEliminado(10)).thenReturn(Optional.empty());assertThrows(ConocimientoNoEncontradoException.class,()->service.obtenerEfectividad(10,5));verifyNoInteractions(resultados);}
 void valido(EstadoConocimiento e,Usuario u){validoSinUsuario(e);when(usuarios.buscarPorEmail("u"+u.getId()+"@x.cl")).thenReturn(Optional.of(u));}
 void validoSinUsuario(EstadoConocimiento e){when(conocimientos.buscarPorIdIncluidoEliminado(10)).thenReturn(Optional.of(conocimiento(e)));when(soluciones.buscarPorId(5)).thenReturn(Optional.of(solucion));}
 void pertenencia(){validoSinUsuario(EstadoConocimiento.PUBLICADO);} void guardarConId(){when(resultados.guardar(any())).thenAnswer(i->{ResultadoSolucion r=i.getArgument(0);return new ResultadoSolucion(1,r.solucionId(),r.usuarioId(),r.funciono(),r.comentario(),r.fecha());});}
 Conocimiento conocimiento(EstadoConocimiento e){return new Conocimiento(10,"C","D",e,null,null,null,null,null,tecnico,LocalDateTime.now(),null,null);}
 Usuario user(int id,String rol){return new Usuario(id,new Rol(1,rol,null,true,null,null),"U"+id,"u"+id+"@x.cl","h",true,null,null);}
 ResultadoSolucion resultado(int id,boolean ok,int user){return new ResultadoSolucion(id,5,user,ok,null,LocalDateTime.now().minusMinutes(id));}
 static class AtomicInteger {private int value;int incrementAndGet(){return ++value;}}
}
