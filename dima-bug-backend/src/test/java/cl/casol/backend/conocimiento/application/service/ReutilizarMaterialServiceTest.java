package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.shared.application.archivo.*;
import cl.casol.backend.shared.infrastructure.storage.AlmacenamientoArchivoFilesystemAdapter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ReutilizarMaterialServiceTest {
    @TempDir Path temporal;
    final ConocimientoRepository conocimientos=mock(ConocimientoRepository.class);
    final MaterialApoyoRepository materiales=mock(MaterialApoyoRepository.class);
    final MantenerMaterialApoyoService mantener=mock(MantenerMaterialApoyoService.class);
    AlmacenamientoArchivoFilesystemAdapter almacen; ReutilizarMaterialService service; Manager manager;
    MaterialApoyo original;String ref;
    static final byte[] PDF="%PDF-1.7\nDocumento sintético".getBytes(StandardCharsets.UTF_8);
    Conocimiento c(int id,EstadoConocimiento estado){return new Conocimiento(id,"K"+id,"D",estado,null,null,null,null,null,null,null,null,null);}
    @BeforeEach void preparar(){
        almacen=new AlmacenamientoArchivoFilesystemAdapter(temporal.toString());manager=new Manager();
        service=new ReutilizarMaterialService(conocimientos,materiales,new ArchivoMaterialService(almacen,manager),mantener);
        ref=almacen.guardar(PDF);original=new MaterialApoyo(3,1,null,"Manual",TipoMaterial.PDF,ref);
        when(conocimientos.buscarPorId(1)).thenReturn(Optional.of(c(1,EstadoConocimiento.PUBLICADO)));
        when(conocimientos.buscarPorId(2)).thenReturn(Optional.of(c(2,EstadoConocimiento.BORRADOR)));
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(original));
        when(mantener.crear(anyInt(),anyString(),any(),anyString())).thenAnswer(i->new MaterialApoyo(99,i.getArgument(0),null,i.getArgument(1),i.getArgument(2),i.getArgument(3)));
    }
    long archivos()throws Exception{try(var lista=Files.list(temporal)){return lista.count();}}
    @Test void copiaConIdReferenciaIndependientesYContenidoIgual()throws Exception{
        var copia=service.reutilizar(1,3,2);assertEquals(99,copia.id());assertEquals(2,copia.conocimientoId());assertNull(copia.pasoId());assertNotEquals(original.url(),copia.url());assertArrayEquals(PDF,almacen.leer(copia.url()));assertEquals(2,archivos());
        almacen.eliminar(original.url());assertArrayEquals(PDF,almacen.leer(copia.url()));
    }
    @Test void duplicaContenidoNoNombreYNoEscribeOtroArchivo()throws Exception{
        String otra=almacen.guardar(PDF);when(materiales.buscarDirectosPorConocimiento(2)).thenReturn(List.of(new MaterialApoyo(6,2,null,"Otro nombre",TipoMaterial.PDF,otra)));
        assertThrows(MaterialDuplicadoException.class,()->service.reutilizar(1,3,2));assertEquals(2,archivos());verifyNoInteractions(mantener);
    }
    @Test void copiaEnlaceSinLeerNiSubirArchivo(){when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,1,null,"Video",TipoMaterial.VIDEO,"https://example.invalid/video")));var copia=service.reutilizar(1,3,2);assertEquals("https://example.invalid/video",copia.url());assertEquals(TipoMaterial.VIDEO,copia.tipo());}
    @Test void enlaceDuplicadoNormalizaUri(){when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,1,null,"M",TipoMaterial.ENLACE,"https://EXAMPLE.invalid/a/../manual")));when(materiales.buscarDirectosPorConocimiento(2)).thenReturn(List.of(new MaterialApoyo(6,2,null,"Otro",TipoMaterial.ENLACE,"https://example.invalid/manual")));assertThrows(MaterialDuplicadoException.class,()->service.reutilizar(1,3,2));verifyNoInteractions(mantener);}
    @Test void noReutilizaBorradorNiLeeArchivo(){when(conocimientos.buscarPorId(1)).thenReturn(Optional.of(c(1,EstadoConocimiento.BORRADOR)));assertThrows(ClasificacionInvalidaException.class,()->service.reutilizar(1,3,2));verifyNoInteractions(materiales,mantener);}
    @Test void destinoInexistente(){when(conocimientos.buscarPorId(2)).thenReturn(Optional.empty());assertThrows(ConocimientoNoEncontradoException.class,()->service.reutilizar(1,3,2));verifyNoInteractions(materiales,mantener);}
    @Test void origenInexistente(){when(conocimientos.buscarPorId(1)).thenReturn(Optional.empty());assertThrows(ConocimientoNoEncontradoException.class,()->service.reutilizar(1,3,2));}
    @Test void mismoConocimientoSeRechaza(){assertThrows(ClasificacionInvalidaException.class,()->service.reutilizar(1,3,1));verifyNoInteractions(conocimientos,materiales,mantener);}
    @Test void materialAjenoODePasoSeRechaza(){for(var m:List.of(new MaterialApoyo(3,9,null,"M",TipoMaterial.PDF,ref),new MaterialApoyo(3,1,7,"M",TipoMaterial.PDF,ref))){when(materiales.buscarPorId(3)).thenReturn(Optional.of(m));assertThrows(MaterialApoyoNoEncontradoException.class,()->service.reutilizar(1,3,2));}verifyNoInteractions(mantener);}
    @Test void archivoAusenteNoCreaReferencia(){almacen.eliminar(ref);var ex=assertThrows(ArchivoException.class,()->service.reutilizar(1,3,2));assertEquals(ArchivoException.Motivo.NO_ENCONTRADO,ex.motivo());verifyNoInteractions(mantener);}
    @Test void firmaOTipoIncorrectoNoSeCopia()throws Exception{Files.write(temporal.resolve(FormatoArchivo.clave(ref)),new byte[]{1,2,3});assertThrows(ArchivoException.class,()->service.reutilizar(1,3,2));verifyNoInteractions(mantener);}
    @Test void archivoSuperiorAlLimiteSeRechaza()throws Exception{Files.write(temporal.resolve(FormatoArchivo.clave(ref)),new byte[FormatoArchivo.MAX_BYTES+1]);var ex=assertThrows(ArchivoException.class,()->service.reutilizar(1,3,2));assertEquals(ArchivoException.Motivo.DEMASIADO_GRANDE,ex.motivo());verifyNoInteractions(mantener);}
    @Test void noComparteDescargasPrivadasMedianteEnlace(){for(String url:List.of("/api/conocimientos/1/materiales/3/archivo","https://example.invalid/api/conocimientos/1/materiales/3/archivo","file:/tmp/privado","javascript:alert(1)","https://u:p@example.invalid/manual")){when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,1,null,"M",TipoMaterial.ENLACE,url)));assertThrows(ClasificacionInvalidaException.class,()->service.reutilizar(1,3,2));}verifyNoInteractions(mantener);}
    @Test void falloPersistenciaCompensaArchivoCopiaYConservaOrigen()throws Exception{doThrow(new IllegalStateException("índice")).when(mantener).crear(anyInt(),anyString(),any(),anyString());assertThrows(IllegalStateException.class,()->service.reutilizar(1,3,2));assertEquals(1,archivos());assertArrayEquals(PDF,almacen.leer(ref));}
    @Test void falloCommitCompensaArchivoNuevo()throws Exception{manager.falla=true;assertThrows(TransactionSystemException.class,()->service.reutilizar(1,3,2));assertEquals(1,archivos());assertArrayEquals(PDF,almacen.leer(ref));}
    @Test void bloqueaPadresAntesDeLeerReferencias(){service.reutilizar(1,3,2);var o=inOrder(conocimientos,materiales);o.verify(conocimientos).bloquear(1);o.verify(conocimientos).bloquear(2);o.verify(conocimientos).buscarPorId(1);o.verify(conocimientos).buscarPorId(2);o.verify(materiales).buscarPorId(3);}
    static class Manager extends AbstractPlatformTransactionManager{
        boolean falla;
        protected Object doGetTransaction(){return new Object();}protected void doBegin(Object t,TransactionDefinition d){}
        protected void doCommit(DefaultTransactionStatus s){if(falla)throw new TransactionSystemException("commit");}protected void doRollback(DefaultTransactionStatus s){}
    }
}
