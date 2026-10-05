package cl.casol.backend.shared.infrastructure.web;

import cl.casol.backend.shared.application.archivo.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ArchivoHttp {
    private ArchivoHttp() { }
    public static ArchivoSubido convertir(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty())
            throw new ArchivoException(ArchivoException.Motivo.INVALIDO, "El archivo está vacío");
        if (archivo.getSize() > FormatoArchivo.MAX_BYTES)
            throw new ArchivoException(ArchivoException.Motivo.DEMASIADO_GRANDE, "El archivo supera 10 MB");
        try (var entrada = archivo.getInputStream()) {
            byte[] contenido = entrada.readNBytes(FormatoArchivo.MAX_BYTES + 1);
            if (contenido.length > FormatoArchivo.MAX_BYTES)
                throw new ArchivoException(ArchivoException.Motivo.DEMASIADO_GRANDE, "El archivo supera 10 MB");
            return new ArchivoSubido(archivo.getContentType(), contenido);
        } catch (IOException ex) {
            throw new ArchivoException(ArchivoException.Motivo.ALMACENAMIENTO, "No fue posible recibir el archivo", ex);
        }
    }
    public static ResponseEntity<byte[]> respuesta(ArchivoDescarga archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.mime()))
                .contentLength(archivo.contenido().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(archivo.contenido());
    }
    public static void validarEnlace(String url) {
        if (url != null && url.trim().startsWith(FormatoArchivo.PREFIJO))
            throw new ArchivoException(ArchivoException.Motivo.INVALIDO, "Las referencias administradas solo se crean mediante upload");
    }
}
