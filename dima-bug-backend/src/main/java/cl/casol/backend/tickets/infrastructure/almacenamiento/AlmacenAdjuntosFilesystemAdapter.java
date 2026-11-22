package cl.casol.backend.tickets.infrastructure.almacenamiento;

import cl.casol.backend.shared.application.archivo.FormatoArchivo;
import cl.casol.backend.tickets.application.port.out.AlmacenAdjuntosPort;
import cl.casol.backend.tickets.domain.exception.AdjuntoNoEncontradoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.regex.Pattern;

/** Guarda los archivos de los tickets en una carpeta del servidor, con nombre aleatorio. */
@Component
public class AlmacenAdjuntosFilesystemAdapter implements AlmacenAdjuntosPort {
    private static final Pattern CLAVE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private final Path directorio;
    private final Path materiales;

    public AlmacenAdjuntosFilesystemAdapter(
            @Value("${dimabug.storage.tickets-dir:./uploads/tickets}") String directorio,
            @Value("${dimabug.storage.materiales-dir:./uploads/materiales}") String materiales) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        this.materiales = Path.of(materiales).toAbsolutePath().normalize();
    }

    // Guarda el contenido.
    @Override
    public String guardar(byte[] contenido) {
        String clave = UUID.randomUUID().toString();
        boolean creado = false;
        try {
            Files.createDirectories(directorio);
            Path archivo = resolver(clave);
            var atributos = Files.getFileStore(directorio).supportsFileAttributeView("posix")
                    ? new java.nio.file.attribute.FileAttribute<?>[]{java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))}
                    : new java.nio.file.attribute.FileAttribute<?>[0];
            try (var canal = Files.newByteChannel(archivo,
                    java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), atributos)) {
                creado = true;
                var buffer = java.nio.ByteBuffer.wrap(contenido);
                while (buffer.hasRemaining()) canal.write(buffer);
            }
            return clave;
        } catch (IOException ex) {
            if (creado) try { Files.deleteIfExists(resolver(clave)); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw new UncheckedIOException("No fue posible guardar el archivo", ex);
        }
    }

    // Lee la referencia.
    @Override
    public byte[] leer(String referencia) {
        try {
            return Files.readAllBytes(resolver(referencia));
        } catch (NoSuchFileException ex) {
            throw new AdjuntoNoEncontradoException(null);
        } catch (IOException ex) {
            throw new UncheckedIOException("No fue posible leer el archivo", ex);
        }
    }

    // Elimina la referencia.
    @Override
    public void eliminar(String referencia) {
        try {
            Files.deleteIfExists(resolver(referencia));
        } catch (IOException ignorada) {
            // Si no se puede borrar queda huérfano en disco; no afecta al ticket.
        }
    }

    // Resuelve el clave.
    private Path resolver(String clave) {
        if (clave != null && clave.startsWith(FormatoArchivo.PREFIJO)) {
            return materiales.resolve(FormatoArchivo.clave(clave));
        }
        if (clave == null || !CLAVE.matcher(clave).matches()) throw new TicketInvalidoException("Referencia de archivo inválida");
        return directorio.resolve(clave);
    }
}
