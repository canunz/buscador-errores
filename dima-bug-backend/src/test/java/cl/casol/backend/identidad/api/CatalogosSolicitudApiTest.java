package cl.casol.backend.identidad.api;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CatalogosSolicitudApiTest {
 @Test void soloActivosYProyeccionDeDosCamposSinContactos() {
  var sucursales=mock(SucursalRepository.class);var departamentos=mock(DepartamentoRepository.class);
  var activa=mock(Sucursal.class);when(activa.id()).thenReturn(1);when(activa.nombre()).thenReturn("Sucursal QA");when(activa.activa()).thenReturn(true);
  var inactiva=mock(Sucursal.class);when(inactiva.activa()).thenReturn(false);
  when(sucursales.listar()).thenReturn(List.of(activa,inactiva));when(sucursales.buscar(1)).thenReturn(Optional.of(activa));when(sucursales.buscar(2)).thenReturn(Optional.of(inactiva));
  when(departamentos.buscarActivosOrdenadosPorNombre()).thenReturn(List.of(new Departamento(3,"Área QA",true),new Departamento(4,"Inactivo",false)));
  when(departamentos.buscarActivoPorId(4)).thenReturn(Optional.empty());
  var api=new CatalogosSolicitudApi(sucursales,departamentos);
  assertEquals(List.of(new CatalogosSolicitudApi.Opcion(1,"Sucursal QA")),api.sucursales());
  assertEquals(List.of(new CatalogosSolicitudApi.Opcion(3,"Área QA")),api.departamentos());
  assertTrue(api.sucursalActiva(2).isEmpty());assertTrue(api.departamentoActivo(4).isEmpty());
  assertEquals(Set.of("id","nombre"),Arrays.stream(CatalogosSolicitudApi.Opcion.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
  verify(activa,never()).responsableEmail();verify(activa,never()).responsableNombre();
 }
}
