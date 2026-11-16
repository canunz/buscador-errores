package cl.casol.backend.shared.application.archivo;

import java.nio.charset.StandardCharsets;

/** Identificación de firma; no sustituye un análisis antivirus o validación completa del formato. */
public final class FormatoArchivo {
    private static final java.util.regex.Pattern CLAVE = java.util.regex.Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final String PREFIJO = "file:materiales/";
    private FormatoArchivo() { }

    public static String clave(String referencia) {
        if (referencia == null || !referencia.startsWith(PREFIJO))
            throw new ArchivoException(ArchivoException.Motivo.INVALIDO, "Referencia de archivo inválida");
        String clave = referencia.substring(PREFIJO.length());
        if (!CLAVE.matcher(clave).matches())
            throw new ArchivoException(ArchivoException.Motivo.INVALIDO, "Referencia de archivo inválida");
        return clave;
    }

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
