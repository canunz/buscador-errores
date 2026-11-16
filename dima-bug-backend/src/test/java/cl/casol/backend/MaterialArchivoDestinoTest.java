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
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialArchivoDestinoTest {
    final ConocimientoRepository conocimientos = mock(ConocimientoRepository.class);
    final ProcedimientoRepository procedimientos = mock(ProcedimientoRepository.class);
    final PasoRepository pasos = mock(PasoRepository.class);
    final MaterialApoyoRepository materiales = mock(MaterialApoyoRepository.class);
    final ArchivoMaterialService archivos = mock(ArchivoMaterialService.class);
    final MantenerMaterialApoyoService mantenerConocimiento = mock(MantenerMaterialApoyoService.class);
    final MantenerMaterialPasoService mantenerPaso = mock(MantenerMaterialPasoService.class);
    final MaterialConocimientoArchivoService conocimiento = new MaterialConocimientoArchivoService(conocimientos,materiales,mantenerConocimiento,archivos,mock(IndexarConocimientoService.class));
    final MaterialPasoArchivoService paso = new MaterialPasoArchivoService(procedimientos,pasos,materiales,mantenerPaso,archivos);
    @Test void subidaConocimientoPersisteReferenciaEnDestinoCorrecto() {
        existeConocimiento(); String ref = "file:materiales/9d50c602-4bf2-438a-82c3-e197c9153250";
        var entrada = new ArchivoSubido("application/pdf", "%PDF-1.7".getBytes());
        var esperado = new MaterialApoyo(3,1,null,"M",TipoMaterial.PDF,ref);
        when(mantenerConocimiento.crear(1,"M",TipoMaterial.PDF,ref)).thenReturn(esperado);
        when(archivos.crear(eq("M"),eq(TipoMaterial.PDF),eq(entrada),any())).thenAnswer(i -> {
            java.util.function.Function<String,MaterialApoyo> persistir = i.getArgument(3);
            return persistir.apply(ref);
        });
        assertEquals(esperado,conocimiento.subir(1,"M",TipoMaterial.PDF,entrada));
        verify(mantenerConocimiento).crear(1,"M",TipoMaterial.PDF,ref);
    }
    @Test void subidaPasoPersisteReferenciaEnDestinoCorrecto() {
        existePaso(); String ref = "file:materiales/9d50c602-4bf2-438a-82c3-e197c9153250";
        var entrada = new ArchivoSubido("application/pdf", "%PDF-1.7".getBytes());
        var esperado = new MaterialApoyo(3,null,2,"M",TipoMaterial.PDF,ref);
        when(mantenerPaso.crear(1,2,"M",TipoMaterial.PDF,ref)).thenReturn(esperado);
        when(archivos.crear(eq("M"),eq(TipoMaterial.PDF),eq(entrada),any())).thenAnswer(i -> {
            java.util.function.Function<String,MaterialApoyo> persistir = i.getArgument(3);
            return persistir.apply(ref);
        });
        assertEquals(esperado,paso.subir(1,2,"M",TipoMaterial.PDF,entrada));
        verify(mantenerPaso).crear(1,2,"M",TipoMaterial.PDF,ref);
    }
    @Test void materialInexistenteNoLeeArchivo() {
        existeConocimiento(); existePaso();
        assertThrows(MaterialApoyoNoEncontradoException.class, () -> conocimiento.descargar(1,3));
        assertThrows(MaterialApoyoNoEncontradoException.class, () -> paso.descargar(1,2,3));
        verifyNoInteractions(archivos);
    }
    @Test void conocimientoInexistenteNoAlmacena() {
        assertThrows(ConocimientoNoEncontradoException.class, () -> conocimiento.subir(1,"M",TipoMaterial.PDF,null));
        verifyNoInteractions(archivos);
    }
    @Test void procedimientoInexistenteNoAlmacena() {
        assertThrows(ProcedimientoNoEncontradoException.class, () -> paso.subir(1,2,"M",TipoMaterial.PDF,null));
        verifyNoInteractions(archivos);
    }
    @Test void pasoInexistenteNoAlmacena() {
        when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(mock(Procedimiento.class)));
        assertThrows(PasoNoEncontradoException.class, () -> paso.subir(1,2,"M",TipoMaterial.PDF,null));
        verifyNoInteractions(archivos);
    }
    @Test void pasoDeOtroProcedimientoNoAlmacena() {
        when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(mock(Procedimiento.class)));
        when(pasos.buscarPorId(2)).thenReturn(Optional.of(new Paso(2,9,1,"X",false)));
        assertThrows(PasoNoEncontradoException.class, () -> paso.subir(1,2,"M",TipoMaterial.PDF,null));
        verifyNoInteractions(archivos);
    }
    @Test void noDescargaMaterialDeOtroConocimiento() {
        existeConocimiento(); when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,9,null,"M",TipoMaterial.PDF,"file:materiales/x")));
        assertThrows(MaterialApoyoNoEncontradoException.class, () -> conocimiento.descargar(1,3)); verifyNoInteractions(archivos);
    }
    @Test void noDescargaMaterialDePasoComoConocimiento() {
        existeConocimiento(); when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,1,2,"M",TipoMaterial.PDF,"file:materiales/x")));
        assertThrows(MaterialApoyoNoEncontradoException.class, () -> conocimiento.descargar(1,3)); verifyNoInteractions(archivos);
    }
    @Test void noDescargaMaterialDeOtroPaso() {
        existePaso(); when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,null,9,"M",TipoMaterial.PDF,"file:materiales/x")));
        assertThrows(MaterialApoyoNoEncontradoException.class, () -> paso.descargar(1,2,3)); verifyNoInteractions(archivos);
    }
    @Test void noDescargaConocimientoComoPaso() {
        existePaso(); when(materiales.buscarPorId(3)).thenReturn(Optional.of(new MaterialApoyo(3,1,2,"M",TipoMaterial.PDF,"file:materiales/x")));
        assertThrows(MaterialApoyoNoEncontradoException.class, () -> paso.descargar(1,2,3)); verifyNoInteractions(archivos);
    }
    @Test void descargaDestinoConocimientoCorrecto() {
        existeConocimiento(); var m = new MaterialApoyo(3,1,null,"M",TipoMaterial.PDF,"file:materiales/x");
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(m)); conocimiento.descargar(1,3); verify(archivos).descargar(m);
    }
    @Test void descargaDestinoPasoCorrecto() {
        existePaso(); var m = new MaterialApoyo(3,null,2,"M",TipoMaterial.PDF,"file:materiales/x");
        when(materiales.buscarPorId(3)).thenReturn(Optional.of(m)); paso.descargar(1,2,3); verify(archivos).descargar(m);
    }
    private void existeConocimiento() {when(conocimientos.buscarPorId(1)).thenReturn(Optional.of(mock(Conocimiento.class)));}
    private void existePaso() {
        when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(mock(Procedimiento.class)));
        when(pasos.buscarPorId(2)).thenReturn(Optional.of(new Paso(2,1,1,"X",false)));
    }
}
