package cl.casol.backend.shared.infrastructure.storage;

import cl.casol.backend.shared.application.archivo.ArchivoException;
import cl.casol.backend.shared.application.archivo.FormatoArchivo;
import cl.casol.backend.shared.application.port.out.AlmacenamientoArchivoPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;
import static cl.casol.backend.shared.application.archivo.ArchivoException.Motivo.*;

@Component
public class AlmacenamientoArchivoFilesystemAdapter implements AlmacenamientoArchivoPort {
    private final Path directorio;

    public AlmacenamientoArchivoFilesystemAdapter(
            @Value("${dimabug.storage.materiales-dir:./uploads/materiales}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(byte[] contenido) {
        if (contenido == null || contenido.length == 0) throw new ArchivoException(INVALIDO, "El archivo está vacío");
        if (contenido.length > FormatoArchivo.MAX_BYTES) throw new ArchivoException(DEMASIADO_GRANDE, "El archivo supera 10 MB");
        String referencia = FormatoArchivo.PREFIJO + UUID.randomUUID();
        Path destino = resolver(referencia);
        boolean creado = false;
        try {
            Files.createDirectories(directorio);
            // CREATE_NEW no sobrescribe archivos ni sigue un enlace simbólico preexistente.
            try (var salida = Files.newOutputStream(destino, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                creado = true;
                salida.write(contenido);
            }
            return referencia;
        } catch (IOException ex) {
            if (creado) {
                try { Files.deleteIfExists(destino); } catch (IOException limpieza) { ex.addSuppressed(limpieza); }
            }
            throw new ArchivoException(ALMACENAMIENTO, "No fue posible almacenar el archivo", ex);
        }
    }

    @Override
    public byte[] leer(String referencia) {
        Path archivo = resolver(referencia);
        try (InputStream entrada = Files.newInputStream(archivo, LinkOption.NOFOLLOW_LINKS)) {
            byte[] contenido = entrada.readNBytes(FormatoArchivo.MAX_BYTES + 1);
            if (contenido.length > FormatoArchivo.MAX_BYTES)
                throw new ArchivoException(DEMASIADO_GRANDE, "El archivo supera 10 MB");
            return contenido;
        } catch (NoSuchFileException ex) {
            throw new ArchivoException(NO_ENCONTRADO, "El archivo no existe", ex);
        } catch (IOException ex) {
            throw new ArchivoException(ALMACENAMIENTO, "No fue posible leer el archivo", ex);
        }
    }

    @Override
    public void eliminar(String referencia) {
        Path archivo = resolver(referencia);
        try { Files.deleteIfExists(archivo); }
        catch (IOException ex) { throw new ArchivoException(ALMACENAMIENTO, "No fue posible eliminar el archivo", ex); }
    }

    @Override
    public void compensar(String referencia) {
        try { Files.deleteIfExists(resolver(referencia)); }
        catch (IOException ex) { throw new ArchivoException(ALMACENAMIENTO, "No fue posible compensar el archivo", ex); }
    }

    private Path resolver(String referencia) {
        String clave = FormatoArchivo.clave(referencia);
        Path resultado = directorio.resolve(clave).normalize();
        if (!resultado.getParent().equals(directorio)) throw new ArchivoException(INVALIDO, "Referencia de archivo inválida");
        return resultado;
    }
}
