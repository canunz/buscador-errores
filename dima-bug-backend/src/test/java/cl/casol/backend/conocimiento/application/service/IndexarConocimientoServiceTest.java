package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.DocumentoBusquedaConocimientoRepository;
import cl.casol.backend.conocimiento.domain.DocumentoBusquedaConocimiento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class IndexarConocimientoServiceTest {

    @Test
    void eliminarRetiraLaProyeccionReconstruible() {
        DocumentoBusquedaConocimientoRepository repository = mock(DocumentoBusquedaConocimientoRepository.class);
        new IndexarConocimientoService(repository).eliminar(9);
        verify(repository).eliminar(9);
    }

    @org.junit.jupiter.api.TestFactory
    Stream<DynamicTest> cadaOrigenQuedaDisponibleParaBusquedaTextual() {
        return Stream.of(
                new Caso("titulo y descripcion", documento(List.of("titulo descripcion"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), "titulo"),
                new Caso("sintoma", documento(List.of(), List.of("sintoma red"), List.of(), List.of(), List.of(), List.of(), List.of()), "sintoma red"),
                new Caso("causa", documento(List.of(), List.of(), List.of("causa firewall"), List.of(), List.of(), List.of(), List.of()), "causa firewall"),
                new Caso("prueba y resultado esperado", documento(List.of(), List.of(), List.of(), List.of("ping", "respuesta esperada"), List.of(), List.of(), List.of()), "respuesta esperada"),
                new Caso("solucion", documento(List.of(), List.of(), List.of(), List.of(), List.of("reiniciar servicio"), List.of(), List.of()), "reiniciar servicio"),
                new Caso("responsable y departamento", documento(List.of(), List.of(), List.of(), List.of(), List.of(), List.of("Luciano", "Soporte"), List.of()), "Luciano"),
                new Caso("material", documento(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of("Manual VPN")), "Manual VPN"),
                new Caso("clasificacion", documento(List.of("POS", "Linux", "Ventas", "Caja", "Diaria"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), "Ventas")
        ).map(caso -> DynamicTest.dynamicTest("indexa " + caso.nombre(), () -> {
            DocumentoBusquedaConocimientoRepository repository = mock(DocumentoBusquedaConocimientoRepository.class);
            when(repository.obtener(1)).thenReturn(Optional.of(caso.documento()));
            new IndexarConocimientoService(repository).indexar(1);
            verify(repository).guardar(eq(1), contains(caso.termino()));
        }));
    }

    @Test
    void construyeUnDocumentoConTodosLosOrigenesIndexablesEnSuOrden() {
        DocumentoBusquedaConocimientoRepository repository = mock(DocumentoBusquedaConocimientoRepository.class);
        when(repository.obtener(10)).thenReturn(Optional.of(new DocumentoBusquedaConocimiento(
                List.of("Titulo", "Descripcion", "Comentario", "Hardware", "Linux", "Sistema", "Detalle sistema", "Modulo", "Frecuencia"),
                List.of("Sintoma primero", "Sintoma segundo"),
                List.of("Causa primera"),
                List.of("Prueba activa", "Resultado esperado"),
                List.of("Solucion"),
                List.of("Luciano", "Tecnico", "Soporte"),
                List.of("Manual firewall"))));

        new IndexarConocimientoService(repository).indexar(10);

        verify(repository).guardar(10, String.join("\n", "Titulo", "Descripcion", "Comentario", "Hardware",
                "Linux", "Sistema", "Detalle sistema", "Modulo", "Frecuencia", "Sintoma primero",
                "Sintoma segundo", "Causa primera", "Prueba activa", "Resultado esperado", "Solucion",
                "Luciano", "Tecnico", "Soporte", "Manual firewall"));
        verify(repository, never()).obtener(argThat(id -> id != 10));
    }

    @Test
    void omiteNulosVaciosYEspacios() {
        DocumentoBusquedaConocimientoRepository repository = mock(DocumentoBusquedaConocimientoRepository.class);
        when(repository.obtener(3)).thenReturn(Optional.of(new DocumentoBusquedaConocimiento(
                java.util.Arrays.asList("  Titulo  ", null, " "), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of())));

        new IndexarConocimientoService(repository).indexar(3);

        verify(repository).guardar(3, "Titulo");
    }

    @Test
    void noCreaIndiceParaConocimientoInexistente() {
        DocumentoBusquedaConocimientoRepository repository = mock(DocumentoBusquedaConocimientoRepository.class);
        when(repository.obtener(99)).thenReturn(Optional.empty());

        new IndexarConocimientoService(repository).indexar(99);

        verify(repository, never()).guardar(anyInt(), anyString());
    }

    private DocumentoBusquedaConocimiento documento(List<String> base, List<String> sintomas, List<String> causas,
            List<String> pruebas, List<String> soluciones, List<String> asignaciones, List<String> materiales) {
        return new DocumentoBusquedaConocimiento(base, sintomas, causas, pruebas, soluciones, asignaciones, materiales);
    }

    private record Caso(String nombre, DocumentoBusquedaConocimiento documento, String termino) { }
}
