package cl.casol.backend.shared.application.archivo;

import java.nio.charset.StandardCharsets;

/** Identificación de firma; no sustituye un análisis antivirus o validación completa del formato. */
public final class FormatoArchivo {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final String PREFIJO = "file:materiales/";
    private FormatoArchivo() { }

    public static String mime(byte[] contenido) {
        if (empieza(contenido, new byte[]{37, 80, 68, 70, 45})) return "application/pdf";
        if (empieza(contenido, new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10})) return "image/png";
        if (empieza(contenido, new byte[]{(byte)255, (byte)216, (byte)255})) return "image/jpeg";
        if (contenido != null && contenido.length >= 16
                && texto(contenido, 0, 4).equals("RIFF") && texto(contenido, 8, 4).equals("WEBP")
                && (texto(contenido, 12, 4).equals("VP8 ") || texto(contenido, 12, 4).equals("VP8L")
                    || texto(contenido, 12, 4).equals("VP8X"))) return "image/webp";
        throw new ArchivoException(ArchivoException.Motivo.INVALIDO, "El contenido del archivo no es un formato permitido");
    }
    private static String texto(byte[] b, int inicio, int longitud) {
        return new String(b, inicio, longitud, StandardCharsets.US_ASCII);
    }
    private static boolean empieza(byte[] b, byte[] firma) {
        if (b == null || b.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) if (b[i] != firma[i]) return false;
        return true;
    }
}
