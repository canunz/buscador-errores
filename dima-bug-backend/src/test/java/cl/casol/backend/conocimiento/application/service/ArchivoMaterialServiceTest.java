package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.shared.application.archivo.*;
import cl.casol.backend.shared.application.port.out.AlmacenamientoArchivoPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArchivoMaterialServiceTest {
    private final AlmacenamientoArchivoPort almacenamiento = mock(AlmacenamientoArchivoPort.class);
    private final Manager manager = new Manager();
    private final ArchivoMaterialService service = new ArchivoMaterialService(almacenamiento, manager);
    private static final byte[] PDF = "%PDF-1.7\ncontenido".getBytes(StandardCharsets.US_ASCII);
    private static final String REFERENCIA = "file:materiales/9d50c602-4bf2-438a-82c3-e197c9153250";
    static Stream<Arguments> formatos() {
        return Stream.of(Arguments.of(TipoMaterial.PDF, "application/pdf", PDF),
                Arguments.of(TipoMaterial.IMAGEN, "image/png", imagen("png")),
                Arguments.of(TipoMaterial.IMAGEN, "image/jpeg", imagen("jpeg")),
                Arguments.of(TipoMaterial.IMAGEN, "image/webp", java.util.Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA")));
    }
    private static byte[] imagen(String formato) {
        try {
            var salida = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB),formato,salida);
            return salida.toByteArray();
        } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }
    @ParameterizedTest @MethodSource("formatos")
    void subeFormatosPermitidos(TipoMaterial tipo, String mime, byte[] contenido) {
        when(almacenamiento.guardar(contenido)).thenReturn(REFERENCIA);
        MaterialApoyo material = service.crear("Manual", tipo, new ArchivoSubido(mime, contenido),
                ref -> new MaterialApoyo(1,2,null,"Manual",tipo,ref));
        assertEquals(REFERENCIA, material.url());
        verify(almacenamiento, never()).compensar(any());
    }
    @Test void rechazaVacio() { rechaza(TipoMaterial.PDF, "application/pdf", new byte[0]); }
    @Test void rechazaMasDe10Mb() {
        ArchivoException ex = assertThrows(ArchivoException.class, () -> crear(TipoMaterial.PDF,
                new ArchivoSubido("application/pdf", new byte[FormatoArchivo.MAX_BYTES + 1])));
        assertEquals(ArchivoException.Motivo.DEMASIADO_GRANDE, ex.motivo()); verifyNoInteractions(almacenamiento);
    }
    @Test void aceptaExactamente10Mb() {
        byte[] b = new byte[FormatoArchivo.MAX_BYTES]; System.arraycopy(PDF,0,b,0,PDF.length);
        when(almacenamiento.guardar(b)).thenReturn(REFERENCIA);
        assertNotNull(crear(TipoMaterial.PDF, new ArchivoSubido("application/pdf", b)));
    }
    @Test void rechazaMimeInvalido() { rechaza(TipoMaterial.PDF, "text/plain", PDF); }
    @Test void rechazaTipoMimeIncompatible() { rechaza(TipoMaterial.IMAGEN, "application/pdf", PDF); }
    @Test void rechazaFirmaFalsa() { rechaza(TipoMaterial.PDF, "application/pdf", new byte[]{1,2,3}); }
    @ParameterizedTest @EnumSource(value=TipoMaterial.class,names={"ENLACE","VIDEO"})
    void rechazaTiposLocalesNoPermitidos(TipoMaterial tipo) { rechaza(tipo, "application/pdf", PDF); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"  "})
    void rechazaNombreInvalido(String nombre) {
        assertThrows(ArchivoException.class, () -> service.crear(nombre, TipoMaterial.PDF,
                new ArchivoSubido("application/pdf",PDF), ref -> null)); verifyNoInteractions(almacenamiento);
    }
    @Test void rechazaNombreLargo() {
        assertThrows(ArchivoException.class, () -> service.crear("x".repeat(151), TipoMaterial.PDF,
                new ArchivoSubido("application/pdf",PDF), ref -> null)); verifyNoInteractions(almacenamiento);
    }
    @Test void compensaSiFallaPersistencia() {
        when(almacenamiento.guardar(PDF)).thenReturn(REFERENCIA);
        assertThrows(IllegalStateException.class, () -> service.crear("M", TipoMaterial.PDF,
                new ArchivoSubido("application/pdf",PDF), ref -> {throw new IllegalStateException("DB");}));
        verify(almacenamiento).compensar(REFERENCIA);
    }
    @Test void compensaSiFallaCommit() {
        when(almacenamiento.guardar(PDF)).thenReturn(REFERENCIA); manager.fallarCommit = true;
        assertThrows(TransactionSystemException.class, () -> crear(TipoMaterial.PDF,new ArchivoSubido("application/pdf",PDF)));
        verify(almacenamiento).compensar(REFERENCIA);
    }
    @Test void conservaErrorOriginalSiFallaCompensacion() {
        when(almacenamiento.guardar(PDF)).thenReturn(REFERENCIA);
        doThrow(new IllegalStateException("disco")).when(almacenamiento).compensar(REFERENCIA);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.crear("M",TipoMaterial.PDF,
                new ArchivoSubido("application/pdf",PDF), ref -> {throw new IllegalStateException("DB");}));
        assertEquals("DB",ex.getMessage());
    }
    @ParameterizedTest @MethodSource("formatos")
    void descargaMimeDesdeContenido(TipoMaterial tipo,String mime,byte[] contenido) {
        when(almacenamiento.leer(REFERENCIA)).thenReturn(contenido);
        ArchivoDescarga descarga = service.descargar(new MaterialApoyo(1,2,null,"Manual",tipo,REFERENCIA));
        assertEquals(mime,descarga.mime()); assertArrayEquals(contenido,descarga.contenido());
    }
    @Test void noDescargaEnlaceExterno() {
        assertThrows(ArchivoException.class, () -> service.descargar(new MaterialApoyo(1,2,null,"M",TipoMaterial.PDF,"https://x")));
        verifyNoInteractions(almacenamiento);
    }
    @Test void noDescargaContenidoIncompatibleConTipo() {
        when(almacenamiento.leer(REFERENCIA)).thenReturn(PDF);
        assertThrows(ArchivoException.class, () -> service.descargar(new MaterialApoyo(1,2,null,"M",TipoMaterial.IMAGEN,REFERENCIA)));
    }
    private MaterialApoyo crear(TipoMaterial tipo,ArchivoSubido archivo) {
        return service.crear("M",tipo,archivo,ref -> new MaterialApoyo(1,2,null,"M",tipo,ref));
    }
    private void rechaza(TipoMaterial tipo,String mime,byte[] contenido) {
        assertThrows(ArchivoException.class, () -> crear(tipo,new ArchivoSubido(mime,contenido)));
        verifyNoInteractions(almacenamiento);
    }
    private static class Manager extends AbstractPlatformTransactionManager {
        boolean fallarCommit;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object t,TransactionDefinition d) { }
        @Override protected void doCommit(DefaultTransactionStatus s) {
            if (fallarCommit) throw new TransactionSystemException("commit");
        }
        @Override protected void doRollback(DefaultTransactionStatus s) { }
    }
}
