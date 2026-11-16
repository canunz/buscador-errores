package cl.casol.backend.tickets.domain;

import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;

import java.nio.charset.StandardCharsets;

/**
 * Reconoce el formato real del archivo por sus primeros bytes (no por la extensión).
 * Permite imágenes, videos cortos y PDF.
 */
public final class FormatoAdjunto {
    public static final String MENSAJE_NO_PERMITIDO =
            "Solo se pueden adjuntar imágenes (PNG, JPG, GIF, WEBP), videos (MP4, MOV, WEBM) o PDF";

    private FormatoAdjunto() { }

    public static String mime(byte[] b) {
        if (empieza(b, 0, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return "image/png";
        if (empieza(b, 0, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return "image/jpeg";
        if (texto(b, 0, 6).equals("GIF87a") || texto(b, 0, 6).equals("GIF89a")) return "image/gif";
        if (texto(b, 0, 4).equals("RIFF") && texto(b, 8, 4).equals("WEBP")) return "image/webp";
        if (texto(b, 0, 5).equals("%PDF-")) return "application/pdf";
        if (texto(b, 4, 4).equals("ftyp")) return texto(b, 8, 4).equals("qt  ") ? "video/quicktime" : "video/mp4";
        if (empieza(b, 0, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3})) return "video/webm";
        throw new TicketInvalidoException(MENSAJE_NO_PERMITIDO);
    }

    public static TipoAdjunto tipo(String mime) {
        if (mime == null) return TipoAdjunto.DOCUMENTO;
        if (mime.startsWith("image/")) return TipoAdjunto.IMAGEN;
        if (mime.startsWith("video/")) return TipoAdjunto.VIDEO;
        return TipoAdjunto.DOCUMENTO;
    }

    private static String texto(byte[] b, int inicio, int largo) {
        if (b == null || b.length < inicio + largo) return "";
        return new String(b, inicio, largo, StandardCharsets.ISO_8859_1);
    }

    private static boolean empieza(byte[] b, int inicio, byte[] firma) {
        if (b == null || b.length < inicio + firma.length) return false;
        for (int i = 0; i < firma.length; i++) if (b[inicio + i] != firma[i]) return false;
        return true;
    }
}
