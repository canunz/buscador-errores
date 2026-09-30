package cl.casol.backend.procedimiento.application.service;

import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.procedimiento.application.port.out.ProcedimientoRepository;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MantenerProcedimientoServiceTest {
    @Mock ProcedimientoRepository procedimientos; @Mock UsuarioRepository usuarios;
    MantenerProcedimientoService service; Usuario tecnico;
    @BeforeEach void setUp(){MockitoAnnotations.openMocks(this);service=new MantenerProcedimientoService(procedimientos,usuarios);tecnico=usuario(7,"TECNICO");}

    @Test void crearSiempreBorradorYUsaUsuarioAutenticado(){when(usuarios.buscarPorEmail("t@x.cl")).thenReturn(Optional.of(tecnico));when(procedimientos.guardar(any())).thenAnswer(i->i.getArgument(0));Procedimiento p=service.crear("N","D","t@x.cl");assertEquals(EstadoProcedimiento.BORRADOR,p.estado());assertSame(tecnico,p.creadoPor());assertNull(p.modificadoPor());}
    @Test void modificarPreservaEstadoCreadorYActualizaModificador(){Procedimiento p=procedimiento(EstadoProcedimiento.PUBLICADO);when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(p));when(usuarios.buscarPorEmail("t@x.cl")).thenReturn(Optional.of(tecnico));when(procedimientos.guardar(any())).thenAnswer(i->i.getArgument(0));Procedimiento r=service.modificar(1,"Nuevo","D2","t@x.cl");assertEquals(EstadoProcedimiento.PUBLICADO,r.estado());assertSame(p.creadoPor(),r.creadoPor());assertSame(tecnico,r.modificadoPor());assertNotNull(r.fechaModificacion());}
    @Test void publicarBorrador(){Procedimiento p=procedimiento(EstadoProcedimiento.BORRADOR);when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(p));when(usuarios.buscarPorEmail("t@x.cl")).thenReturn(Optional.of(tecnico));when(procedimientos.guardar(any())).thenAnswer(i->i.getArgument(0));assertEquals(EstadoProcedimiento.PUBLICADO,service.publicar(1,"t@x.cl").estado());}
    @Test void noRepublicaPublicado(){when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(procedimiento(EstadoProcedimiento.PUBLICADO)));assertThrows(EstadoProcedimientoInvalidoException.class,()->service.publicar(1,"t@x.cl"));verify(procedimientos,never()).guardar(any());}
    @Test void buscarInexistente(){assertThrows(ProcedimientoNoEncontradoException.class,()->service.buscar(404));}
    @Test void listarDelegaPoliticaRepositorio(){when(procedimientos.buscarTodos()).thenReturn(List.of(procedimiento(EstadoProcedimiento.BORRADOR),procedimiento(EstadoProcedimiento.PUBLICADO)));assertEquals(2,service.listar().size());}
    private Procedimiento procedimiento(EstadoProcedimiento e){return new Procedimiento(1,"N","D",e,usuario(1,"ADMINISTRADOR"),LocalDateTime.now(),null,null);}
    private Usuario usuario(int id,String rol){return new Usuario(id,new Rol(id,rol,null,true,null,null),"Usuario","t@x.cl","h",true,null,null);}
}
