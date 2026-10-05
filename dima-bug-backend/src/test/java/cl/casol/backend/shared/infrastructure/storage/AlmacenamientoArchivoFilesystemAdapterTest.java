package cl.casol.backend.shared.infrastructure.storage;

import cl.casol.backend.shared.application.archivo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AlmacenamientoArchivoFilesystemAdapterTest {
    @TempDir Path directorio;
    @Test void guardaConUuidLeeYCompensa() throws Exception {
        var adapter = new AlmacenamientoArchivoFilesystemAdapter(directorio.toString());
        byte[] contenido = "%PDF-1.7".getBytes();
        String ref = adapter.guardar(contenido);
        assertTrue(ref.matches("file:materiales/[0-9a-f-]{36}"));
        assertFalse(ref.contains(directorio.toString()));
        assertArrayEquals(contenido,adapter.leer(ref));
        adapter.compensar(ref);
        assertEquals(ArchivoException.Motivo.NO_ENCONTRADO, assertThrows(ArchivoException.class, () -> adapter.leer(ref)).motivo());
        try (var archivos = Files.list(directorio)) { assertEquals(0,archivos.count()); }
    }
    @ParameterizedTest @ValueSource(strings={"file:materiales/../secreto", "file:materiales/..\\secreto",
            "file:materiales//etc/passwd", "C:\\secreto", "file:materiales/../../x", "file:materiales/%2e%2e/x",
            "https://ejemplo.cl/x", "file:materiales/9d50c602-4bf2-438a-82c3-e197c9153250/../x"})
    void rechazaTraversalEnLecturaYCompensacion(String ref) {
        var adapter = new AlmacenamientoArchivoFilesystemAdapter(directorio.toString());
        assertThrows(ArchivoException.class, () -> adapter.leer(ref));
        assertThrows(ArchivoException.class, () -> adapter.compensar(ref));
    }
    @Test void noSobrescribeArchivos() {
        var adapter = new AlmacenamientoArchivoFilesystemAdapter(directorio.toString());
        assertNotEquals(adapter.guardar(new byte[]{1}),adapter.guardar(new byte[]{1}));
    }
    @Test void rechazaTamanoExcesivoAlLeer() throws Exception {
        var adapter = new AlmacenamientoArchivoFilesystemAdapter(directorio.toString());
        String ref = adapter.guardar(new byte[]{1});
        Files.write(directorio.resolve(ref.substring(FormatoArchivo.PREFIJO.length())),new byte[FormatoArchivo.MAX_BYTES + 1]);
        assertThrows(ArchivoException.class, () -> adapter.leer(ref));
    }
}
