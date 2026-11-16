package cl.casol.backend.procedimiento.application.service;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class PrepararVersionEvidenciaServiceTest {
    @Mock ProcedimientoRepository procedimientos;@Mock PasoRepository pasos;@Mock UsuarioRepository usuarios;
    @InjectMocks PrepararVersionEvidenciaService service;
    Usuario actor(String rol){return new Usuario(1,new Rol(1,rol,null,true,null,null),"QA","qa@example.invalid","NO_LOGIN",true,null,null);}
    void preparar(String nombre){var a=actor("ADMINISTRADOR");when(usuarios.buscarPorEmail(a.getEmail())).thenReturn(Optional.of(a));when(procedimientos.buscarPorId(10)).thenReturn(Optional.of(new Procedimiento(10,nombre,"D",EstadoProcedimiento.PUBLICADO,a,LocalDateTime.now(),null,null)));when(pasos.buscarPorProcedimientoOrdenados(10)).thenReturn(List.of(new Paso(1,10,1,"Instalaciones",false),new Paso(2,10,2,"Actividad física",true)));when(procedimientos.guardar(any())).thenAnswer(i->{Procedimiento p=i.getArgument(0);return new Procedimiento(20,p.nombre(),p.descripcion(),p.estado(),p.creadoPor(),p.fechaCreacion(),null,null);});}
    @Test void creaBorradorYConfiguraPrimerPasoSinEditarElPublicado(){preparar("Preparar POS");var nuevo=service.crear(10,"qa@example.invalid");assertEquals(EstadoProcedimiento.BORRADOR,nuevo.estado());assertEquals(20,nuevo.id());var captor=ArgumentCaptor.forClass(Paso.class);verify(pasos,times(2)).guardar(captor.capture());assertEquals(ModoEjecucionPaso.VALIDACION_EXTERNA,captor.getAllValues().getFirst().modoEjecucion());assertTrue(captor.getAllValues().getFirst().esCritico());assertEquals(ModoEjecucionPaso.MANUAL,captor.getAllValues().get(1).modoEjecucion());assertTrue(captor.getAllValues().stream().allMatch(p->p.id()==null && p.procedimientoId()==20));}
    @Test void tecnicoNoCreaVersionPrivilegiada(){when(usuarios.buscarPorEmail("qa@example.invalid")).thenReturn(Optional.of(actor("TECNICO")));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.crear(10,"qa@example.invalid"));verifyNoInteractions(procedimientos,pasos);}
    @Test void nombreDeVersionRespetaElLimiteDelModelo(){preparar("P".repeat(150));assertTrue(service.crear(10,"qa@example.invalid").nombre().length()<=150);}
}
