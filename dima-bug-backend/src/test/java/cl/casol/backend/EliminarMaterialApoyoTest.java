package cl.casol.backend;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.application.service.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import cl.casol.backend.shared.application.archivo.*;
import cl.casol.backend.shared.application.port.out.AlmacenamientoArchivoPort;
import cl.casol.backend.shared.infrastructure.storage.AlmacenamientoArchivoFilesystemAdapter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EliminarMaterialApoyoTest {
    static final String REF = "file:materiales/9d50c602-4bf2-438a-82c3-e197c9153250";
    final ConocimientoRepository conocimientos = mock(ConocimientoRepository.class);
    final ProcedimientoRepository procedimientos = mock(ProcedimientoRepository.class);
    final PasoRepository pasos = mock(PasoRepository.class);
    final MaterialApoyoRepository materiales = mock(MaterialApoyoRepository.class);
    final IndexarConocimientoService indexador = mock(IndexarConocimientoService.class);
    final AlmacenamientoArchivoPort almacenamiento = mock(AlmacenamientoArchivoPort.class);
    final Manager manager = new Manager();
    final ArchivoMaterialService archivos = new ArchivoMaterialService(almacenamiento,manager);
    final MaterialConocimientoArchivoService conocimiento = new MaterialConocimientoArchivoService(conocimientos,materiales,
            mock(MantenerMaterialApoyoService.class),archivos,indexador);
    final MaterialPasoArchivoService paso = new MaterialPasoArchivoService(procedimientos,pasos,materiales,
            mock(MantenerMaterialPasoService.class),archivos);
    @TempDir Path directorio;

    @BeforeEach void preparar() {
        when(conocimientos.buscarPorId(1)).thenReturn(Optional.of(mock(Conocimiento.class)));
        when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(mock(Procedimiento.class)));
        when(pasos.buscarPorId(2)).thenReturn(Optional.of(new Paso(2,1,1,"X",false)));
    }
    @ParameterizedTest @ValueSource(strings={"conocimiento-enlace","conocimiento-local","paso-enlace","paso-local"})
    void eliminaRegistroArchivoEIndiceSegunDestino(String caso) {
        boolean esConocimiento = caso.startsWith("conocimiento");
        boolean local = caso.endsWith("local");
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(esConocimiento,local ? REF : "https://ejemplo.cl/a")));
        doAnswer(i -> {manager.eventos.add("registro"); return null;}).when(materiales).eliminar(3);
        doAnswer(i -> {manager.eventos.add("indice"); return null;}).when(indexador).indexar(1);
        doAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            manager.eventos.add("archivo"); return null;
        }).when(almacenamiento).eliminar(REF);
        if(esConocimiento) conocimiento.eliminar(1,3); else paso.eliminar(1,2,3);
        var esperado = new ArrayList<>(List.of("registro"));
        if(esConocimiento) esperado.add("indice");
        esperado.add("commit"); if(local) esperado.add("archivo");
        assertEquals(esperado,manager.eventos);
        verify(materiales).eliminar(3);
        if(!local) verifyNoInteractions(almacenamiento);
        if(!esConocimiento) verifyNoInteractions(indexador);
    }
    @Test void conocimientoInexistente() {
        when(conocimientos.buscarPorId(1)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class,()->conocimiento.eliminar(1,3)); sinBorrados();
    }
    @Test void procedimientoInexistente() {
        when(procedimientos.buscarPorId(1)).thenReturn(Optional.empty());
        assertThrows(ProcedimientoNoEncontradoException.class,()->paso.eliminar(1,2,3)); sinBorrados();
        verifyNoInteractions(pasos);
    }
    @Test void pasoInexistente() {
        when(pasos.buscarPorId(2)).thenReturn(Optional.empty());
        assertThrows(PasoNoEncontradoException.class,()->paso.eliminar(1,2,3)); sinBorrados();
    }
    @Test void pasoDeOtroProcedimiento() {
        when(pasos.buscarPorId(2)).thenReturn(Optional.of(new Paso(2,9,1,"X",false)));
        assertThrows(PasoNoEncontradoException.class,()->paso.eliminar(1,2,3)); sinBorrados();
    }
    @Test void materialInexistenteEnAmbosDestinos() {
        assertThrows(MaterialApoyoNoEncontradoException.class,()->conocimiento.eliminar(1,3));
        assertThrows(MaterialApoyoNoEncontradoException.class,()->paso.eliminar(1,2,3)); sinBorrados();
    }
    @Test void materialDeOtroConocimiento() {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,9,null,"M",TipoMaterial.PDF,REF)));
        assertThrows(MaterialApoyoNoEncontradoException.class,()->conocimiento.eliminar(1,3)); sinBorrados();
    }
    @Test void materialDeOtroPaso() {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,null,9,"M",TipoMaterial.PDF,REF)));
        assertThrows(MaterialApoyoNoEncontradoException.class,()->paso.eliminar(1,2,3)); sinBorrados();
    }
    @Test void conocimientoNoEliminaMaterialDePaso() {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(false,REF)));
        assertThrows(MaterialApoyoNoEncontradoException.class,()->conocimiento.eliminar(1,3)); sinBorrados();
    }
    @Test void pasoNoEliminaMaterialDeConocimiento() {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(true,REF)));
        assertThrows(MaterialApoyoNoEncontradoException.class,()->paso.eliminar(1,2,3)); sinBorrados();
    }
    @Test void rechazaRegistroConAmbosDestinos() {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,1,2,"M",TipoMaterial.PDF,REF)));
        assertThrows(MaterialApoyoNoEncontradoException.class,()->conocimiento.eliminar(1,3));
        assertThrows(MaterialApoyoNoEncontradoException.class,()->paso.eliminar(1,2,3)); sinBorrados();
    }
    @ParameterizedTest @ValueSource(strings={"file:materiales/invalido","file:materiales/../secreto",
            "file:materiales/..\\secreto","file:materiales/%2e%2e/secreto","file:materiales/",
            "file:/servidor/secreto", "file:materiales/9d50c602-4bf2-438a-82c3-e197c9153250/../x"})
    void referenciaInvalidaSeRechazaAntesDeBorrar(String referencia) {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(true,referencia)));
        assertThrows(ArchivoException.class,()->conocimiento.eliminar(1,3));
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(false,referencia)));
        assertThrows(ArchivoException.class,()->paso.eliminar(1,2,3)); sinBorrados();
    }
    @Test void falloBorradoBdConservaArchivo() {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(true,REF)));
        doThrow(new IllegalStateException("BD")).when(materiales).eliminar(3);
        assertThrows(IllegalStateException.class,()->conocimiento.eliminar(1,3));
        verifyNoInteractions(almacenamiento,indexador); assertEquals(List.of("rollback"),manager.eventos);
    }
    @Test void falloReindexacionRevierteBdYConservaArchivo() {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(true,REF)));
        doThrow(new IllegalStateException("indice")).when(indexador).indexar(1);
        assertThrows(IllegalStateException.class,()->conocimiento.eliminar(1,3));
        verifyNoInteractions(almacenamiento); assertEquals(List.of("rollback"),manager.eventos);
    }
    @Test void falloCommitConservaArchivo() {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(true,REF))); manager.fallarCommit = true;
        assertThrows(TransactionSystemException.class,()->conocimiento.eliminar(1,3)); verifyNoInteractions(almacenamiento);
    }
    @Test void falloFilesystemNoInformaExito() {
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(true,REF)));
        doThrow(new ArchivoException(ArchivoException.Motivo.ALMACENAMIENTO,"disco")).when(almacenamiento).eliminar(REF);
        ArchivoException ex = assertThrows(ArchivoException.class,()->conocimiento.eliminar(1,3));
        assertEquals(ArchivoException.Motivo.ALMACENAMIENTO,ex.motivo());
        assertTrue(ex.getMessage().contains("registro fue eliminado")); assertFalse(ex.getMessage().contains(REF));
        assertEquals(List.of("commit"),manager.eventos); verify(materiales).eliminar(3);
    }
    @Test void eliminaArchivoRealDespuesDeConfirmarRegistro() {
        var disco = new AlmacenamientoArchivoFilesystemAdapter(directorio.toString());
        String ref = disco.guardar("%PDF-1.7".getBytes());
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(material(true,ref)));
        var coordinador = new ArchivoMaterialService(disco,manager);
        var caso = new MaterialConocimientoArchivoService(conocimientos,materiales,
                mock(MantenerMaterialApoyoService.class),coordinador,indexador);
        caso.eliminar(1,3);
        assertFalse(Files.exists(directorio.resolve(FormatoArchivo.clave(ref)))); verify(materiales).eliminar(3);
    }
    private MaterialApoyo material(boolean conocimiento,String url) {
        return new MaterialApoyo(3,conocimiento ? 1 : null,conocimiento ? null : 2,"M",TipoMaterial.PDF,url);
    }
    private void sinBorrados() {verify(materiales,never()).eliminar(any()); verifyNoInteractions(almacenamiento,indexador);}
    static class Manager extends AbstractPlatformTransactionManager {
        final List<String> eventos = new ArrayList<>(); boolean fallarCommit;
        @Override protected Object doGetTransaction() {return new Object();}
        @Override protected void doBegin(Object t,TransactionDefinition d) {
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,d.getPropagationBehavior());
        }
        @Override protected void doCommit(DefaultTransactionStatus s) {
            if(fallarCommit) throw new TransactionSystemException("commit"); eventos.add("commit");
        }
        @Override protected void doRollback(DefaultTransactionStatus s) {eventos.add("rollback");}
    }
}
