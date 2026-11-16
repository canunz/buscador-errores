package cl.casol.backend.ejecucion.application.service;

import cl.casol.backend.ejecucion.application.port.out.*;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ContratoEjecucionTest {
    @Mock EjecucionRepository ejecuciones;
    @Mock EjecucionPasoRepository checklist;
    @Mock ProcedimientoRepository procedimientos;
    @Mock PasoRepository pasos;
    @Mock UsuarioRepository usuarios;
    @InjectMocks GestionarEjecucionService servicio;
    final LocalDateTime ahora = LocalDateTime.of(2026,11,7,12,0);
    final Usuario tecnico = new Usuario(7,new Rol(1,"TECNICO",null,true,null,null),"QA",
            "qa@example.invalid","NO_LOGIN",true,null,null);

    void auth() { when(usuarios.buscarPorEmail(tecnico.getEmail())).thenReturn(Optional.of(tecnico)); }
    void ejecucion() {
        auth();
        when(ejecuciones.buscarPorId(20)).thenReturn(Optional.of(new Ejecucion(20,10,tecnico,ahora,null,EstadoEjecucion.EN_CURSO,null)));
    }
    ContratoPaso contrato(ModoEjecucionPaso modo) { return new ContratoPaso(1,2,"Verificar electricidad",true,modo); }
    EjecucionPaso paso(ModoEjecucionPaso modo,EstadoEjecucionPaso estado,long revision) {
        return new EjecucionPaso(100,20,1,estado==EstadoEjecucionPaso.COMPLETADO,"Antes",
                estado==EstadoEjecucionPaso.COMPLETADO?ahora:null,contrato(modo),estado,revision);
    }
    void actual(EjecucionPaso p) { ejecucion(); when(checklist.buscarPorId(100)).thenReturn(Optional.of(p)); }

    @Test void inicioCapturaInstruccionesOrdenYCriticidadEnUnaTransaccion() {
        auth();
        when(procedimientos.buscarPorId(10)).thenReturn(Optional.of(new Procedimiento(10,"Preparar",null,EstadoProcedimiento.PUBLICADO,tecnico,ahora,null,null)));
        when(pasos.buscarPorProcedimientoOrdenados(10)).thenReturn(List.of(new Paso(1,10,2,"Verificar electricidad",true)));
        when(ejecuciones.guardar(any())).thenAnswer(i->{var e=(Ejecucion)i.getArgument(0);return new Ejecucion(20,10,e.usuario(),e.fechaInicio(),null,e.estado(),null);});
        servicio.iniciar(10,tecnico.getEmail());
        var captor=ArgumentCaptor.forClass(List.class);
        verify(checklist).guardarTodos(captor.capture());
        var p=(EjecucionPaso)captor.getValue().getFirst();
        assertEquals(contrato(ModoEjecucionPaso.MANUAL),p.contrato());
        assertFalse(p.cumplido()); assertEquals(EstadoEjecucionPaso.PENDIENTE,p.estado());
    }
    @Test void snapshotNoLeeNiDependeDeLaDefinicionActual() {
        ejecucion(); when(checklist.buscarPorEjecucion(20)).thenReturn(List.of(paso(ModoEjecucionPaso.MANUAL,EstadoEjecucionPaso.PENDIENTE,0)));
        var detalle=servicio.listarPasos(20,tecnico.getEmail()).getFirst();
        assertEquals("Verificar electricidad",detalle.instruccion()); assertTrue(detalle.esCritico()); assertEquals(2,detalle.orden());
        verifyNoInteractions(pasos);
    }
    @Test void notaParcialNoRevierteUnaValidacionNiSuEstado() {
        var anterior=paso(ModoEjecucionPaso.VALIDACION_EXTERNA,EstadoEjecucionPaso.EN_VALIDACION,4);
        actual(anterior); when(checklist.guardar(any())).thenAnswer(i->i.getArgument(0));
        var r=servicio.actualizarPaso(20,100,null,"Revisando",4L,tecnico.getEmail()).ejecucionPaso();
        assertEquals(EstadoEjecucionPaso.EN_VALIDACION,r.estado()); assertFalse(r.cumplido()); assertEquals(5,r.version());
        assertEquals(anterior.contrato(),r.contrato());
    }
    @Test void notaParcialNoDesmarcaPasoCompletado() {
        var anterior=paso(ModoEjecucionPaso.MANUAL,EstadoEjecucionPaso.COMPLETADO,1);
        actual(anterior); when(checklist.guardar(any())).thenAnswer(i->i.getArgument(0));
        var r=servicio.actualizarPaso(20,100,null,"Nota",1L,tecnico.getEmail()).ejecucionPaso();
        assertTrue(r.cumplido()); assertEquals(ahora,r.fecha()); assertEquals(EstadoEjecucionPaso.COMPLETADO,r.estado());
    }
    @Test void versionObsoletaNoEscribe() {
        actual(paso(ModoEjecucionPaso.MANUAL,EstadoEjecucionPaso.PENDIENTE,3));
        assertThrows(ConflictoEjecucionException.class,()->servicio.actualizarPaso(20,100,true,"OK",2L,tecnico.getEmail()));
        verify(checklist,never()).guardar(any());
    }
    @Test void patchLegadoNoPuedeCompletarValidacionExterna() {
        actual(paso(ModoEjecucionPaso.VALIDACION_EXTERNA,EstadoEjecucionPaso.ESPERANDO_RESPUESTA,2));
        assertThrows(ConflictoEjecucionException.class,()->servicio.actualizarPaso(20,100,true,null,tecnico.getEmail()));
        verify(checklist,never()).guardar(any());
    }
    @Test void dominioTampocoPermiteCompletarAutomaticoConUnaMarca() {
        var p=paso(ModoEjecucionPaso.AUTOMATICO,EstadoEjecucionPaso.EN_PROCESO,0);
        assertThrows(ConflictoEjecucionException.class,()->p.actualizar(true,null,ahora));
    }
    @Test void rechazoNoSePuedeBorrarMediantePatchBooleano() {
        actual(paso(ModoEjecucionPaso.VALIDACION_EXTERNA,EstadoEjecucionPaso.RECHAZADO,5));
        assertThrows(ConflictoEjecucionException.class,()->servicio.actualizarPaso(20,100,false,null,5L,tecnico.getEmail()));
    }
    @Test void proyeccionBooleanEstadoNoAdmiteInconsistencias() {
        assertThrows(IllegalArgumentException.class,()->new EjecucionPaso(1,2,3,true,null,ahora,contrato(ModoEjecucionPaso.MANUAL),EstadoEjecucionPaso.EN_VALIDACION,0));
    }
    @Test void lecturaLegadaNoFabricaContrato() {
        var p=new EjecucionPaso(1,2,3,true,null,ahora);
        assertNull(p.contrato()); assertEquals(EstadoEjecucionPaso.COMPLETADO,p.estado());
    }
    @Test void bloqueoSeAdquiereAntesDeLeerLaCabecera() {
        actual(paso(ModoEjecucionPaso.MANUAL,EstadoEjecucionPaso.PENDIENTE,0));
        when(checklist.guardar(any())).thenAnswer(i->i.getArgument(0));
        servicio.actualizarPaso(20,100,true,"OK",0L,tecnico.getEmail());
        var orden=inOrder(ejecuciones,checklist);
        orden.verify(ejecuciones).bloquear(20); orden.verify(ejecuciones).buscarPorId(20); orden.verify(checklist).buscarPorId(100);
    }
}
