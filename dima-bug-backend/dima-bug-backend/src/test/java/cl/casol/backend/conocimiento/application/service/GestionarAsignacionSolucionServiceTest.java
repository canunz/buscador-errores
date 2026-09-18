package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionarAsignacionSolucionServiceTest {
    ConocimientoRepository conocimientos; SolucionRepository soluciones; SolucionAsignacionRepository asignaciones;
    DepartamentoRepository departamentos; ResponsableRepository responsables; GestionarAsignacionSolucionService service;
    Departamento depto=new Departamento(1,"Soporte TI",true);
    Responsable responsable=new Responsable(5,1,"Juan Pérez","Técnico",null,true);
    @BeforeEach void setUp() { conocimientos=mock(ConocimientoRepository.class); soluciones=mock(SolucionRepository.class);
        asignaciones=mock(SolucionAsignacionRepository.class); departamentos=mock(DepartamentoRepository.class);
        responsables=mock(ResponsableRepository.class); service=new GestionarAsignacionSolucionService(
                conocimientos,soluciones,asignaciones,departamentos,responsables); }
    void padres() { when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(mock(Conocimiento.class)));
        when(soluciones.buscarPorId(8)).thenReturn(Optional.of(new Solucion(8,4,"S",TipoSolucion.DERIVACION,1))); }
    @Test void listaOrdenadaPrincipalLuegoId() { padres(); when(asignaciones.buscarPorSolucionOrdenadas(8)).thenReturn(List.of(
            new SolucionAsignacion(2,8,responsable,depto,true),new SolucionAsignacion(3,8,null,depto,false)));
        assertTrue(service.listar(4,8).getFirst().principal()); }
    @Test void listaVacia() { padres(); when(asignaciones.buscarPorSolucionOrdenadas(8)).thenReturn(List.of());
        assertTrue(service.listar(4,8).isEmpty()); }
    @Test void solucionInexistente() { when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(mock(Conocimiento.class)));
        when(soluciones.buscarPorId(8)).thenReturn(Optional.empty());
        assertThrows(SolucionNoEncontradaException.class,()->service.listar(4,8)); }
    @Test void solucionAjena() { when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(mock(Conocimiento.class)));
        when(soluciones.buscarPorId(8)).thenReturn(Optional.of(new Solucion(8,9,"S",TipoSolucion.PASOS,1)));
        assertThrows(SolucionNoEncontradaException.class,()->service.listar(4,8)); }
    @Test void creaPorDepartamentoConPrincipalPorDefecto() { padres(); when(departamentos.buscarActivoPorId(1)).thenReturn(Optional.of(depto));
        when(asignaciones.guardar(any())).thenAnswer(i->i.getArgument(0)); SolucionAsignacion a=service.crear(4,8,1,null,null);
        assertEquals(depto,a.departamento()); assertNull(a.responsable()); assertTrue(a.principal()); }
    @Test void creaPorResponsable() { padres(); when(responsables.buscarActivoPorId(5)).thenReturn(Optional.of(responsable));
        when(asignaciones.guardar(any())).thenAnswer(i->i.getArgument(0)); assertEquals(responsable,service.crear(4,8,null,5,false).responsable()); }
    @Test void creaAmbosCoherentes() { padres(); when(departamentos.buscarActivoPorId(1)).thenReturn(Optional.of(depto));
        when(responsables.buscarActivoPorId(5)).thenReturn(Optional.of(responsable)); when(asignaciones.guardar(any())).thenAnswer(i->i.getArgument(0));
        SolucionAsignacion a=service.crear(4,8,1,5,true); assertEquals(1,a.departamento().id()); assertEquals(5,a.responsable().id()); }
    @Test void rechazaAmbosNulos() { padres(); assertThrows(AsignacionSolucionInvalidaException.class,()->service.crear(4,8,null,null,true)); }
    @Test void departamentoInexistenteOInactivo() { padres(); when(departamentos.buscarActivoPorId(1)).thenReturn(Optional.empty());
        assertThrows(DepartamentoNoEncontradoException.class,()->service.crear(4,8,1,null,true)); }
    @Test void responsableInexistenteOInactivo() { padres(); when(responsables.buscarActivoPorId(5)).thenReturn(Optional.empty());
        assertThrows(ResponsableNoEncontradoException.class,()->service.crear(4,8,null,5,true)); }
    @Test void responsableNoPerteneceAlDepartamento() { padres(); Responsable ajeno=new Responsable(5,2,"Juan",null,null,true);
        when(departamentos.buscarActivoPorId(1)).thenReturn(Optional.of(depto)); when(responsables.buscarActivoPorId(5)).thenReturn(Optional.of(ajeno));
        assertThrows(AsignacionSolucionInvalidaException.class,()->service.crear(4,8,1,5,true)); verify(asignaciones,never()).guardar(any()); }
    @Test void modificaAsignacionPerteneciente() { padres(); when(asignaciones.buscarPorId(10)).thenReturn(Optional.of(
            new SolucionAsignacion(10,8,null,depto,true))); when(responsables.buscarActivoPorId(5)).thenReturn(Optional.of(responsable));
        when(asignaciones.guardar(any())).thenAnswer(i->i.getArgument(0)); SolucionAsignacion a=service.modificar(4,8,10,null,5,false);
        assertEquals(10,a.id()); assertFalse(a.principal()); }
    @Test void asignacionInexistente() { padres(); when(asignaciones.buscarPorId(10)).thenReturn(Optional.empty());
        assertThrows(AsignacionSolucionNoEncontradaException.class,()->service.modificar(4,8,10,1,null,true)); }
    @Test void asignacionDeOtraSolucion() { padres(); when(asignaciones.buscarPorId(10)).thenReturn(Optional.of(
            new SolucionAsignacion(10,99,null,depto,true)));
        assertThrows(AsignacionSolucionNoEncontradaException.class,()->service.modificar(4,8,10,1,null,true)); }
}
