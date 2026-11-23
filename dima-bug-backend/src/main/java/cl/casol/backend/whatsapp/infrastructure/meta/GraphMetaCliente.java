package cl.casol.backend.whatsapp.infrastructure.meta;

import cl.casol.backend.whatsapp.application.port.out.EnviarMensajeCanalPort.FalloEnvio;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * Cliente mínimo de WhatsApp Cloud API (Graph). Sin redirecciones, con límites de tamaño y sin
 * registrar tokens, teléfonos ni contenido. Solo lo crea MetaQaConfig cuando la conexión está habilitada.
 */
public class GraphMetaCliente {
    public static final int MAX_MEDIO = 5 * 1024 * 1024;
    private static final Set<String> MIMES = Set.of("image/png", "image/jpeg", "image/webp");
    private final HttpClient http;
    private final String base, token;
    private final JsonMapper json = JsonMapper.builder().build();

    public GraphMetaCliente(String version, String token) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build(),
                "https://graph.facebook.com", version, token);
    }
    GraphMetaCliente(HttpClient http, String raiz, String version, String token) {
        if (version == null || !version.matches("v[0-9]{1,3}\\.[0-9]{1,3}")) throw new IllegalArgumentException("Versión Graph inválida");
        if (token == null || token.isBlank() || token.length() > 1024 || !token.matches("[A-Za-z0-9_.|-]+"))
            throw new IllegalArgumentException("Configura dimabug.whatsapp.meta-qa.graph-token");
        this.http = http; this.base = raiz + "/" + version; this.token = token;
    }

    /** Descarga una imagen recibida: GET /{media-id} → url temporal → bytes. */
    public byte[] descargarImagen(String mediaId, String phoneNumberId) {
        if (mediaId == null || !mediaId.matches("[0-9]{1,64}")) throw new IllegalArgumentException("Identificador de medio inválido");
        var meta = json.readTree(enviar(HttpRequest.newBuilder(URI.create(base + "/" + mediaId + "?phone_number_id=" + phoneNumberId))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).GET().build(), 65536));
        String url = meta.path("url").asText(""), mime = meta.path("mime_type").asText("");
        long tam = meta.path("file_size").asLong(0);
        if (!MIMES.contains(mime.split(";")[0].strip()) || tam > MAX_MEDIO) throw new IllegalArgumentException("Medio no admitido");
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || !(host.endsWith(".fbsbx.com") || host.endsWith(".facebook.com") || host.endsWith(".whatsapp.net")))
            throw new IllegalArgumentException("Origen del medio no autorizado");
        return enviar(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token).GET().build(), MAX_MEDIO);
    }

    /** POST /{phone-number-id}/messages con un mensaje de texto. Devuelve el wamid asignado. */
    public String enviarTexto(String phoneNumberId, String telefono, String cuerpo) {
        var mensaje = new LinkedHashMap<String, Object>();
        mensaje.put("messaging_product", "whatsapp");
        mensaje.put("recipient_type", "individual");
        mensaje.put("to", telefono);
        mensaje.put("type", "text");
        mensaje.put("text", Map.of("preview_url", false, "body", cuerpo));
        byte[] respuesta = enviar(HttpRequest.newBuilder(URI.create(base + "/" + phoneNumberId + "/messages"))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(mensaje))).build(), 65536);
        String id = json.readTree(respuesta).path("messages").path(0).path("id").asText("");
        if (id.isEmpty()) throw new FalloEnvio(true);
        return id;
    }

    private byte[] enviar(HttpRequest request, int maximo) {
        HttpResponse<InputStream> r;
        try { r = http.send(request, HttpResponse.BodyHandlers.ofInputStream()); }
        catch (IOException e) { throw new FalloEnvio(true); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new FalloEnvio(true); }
        try (var in = r.body()) {
            byte[] cuerpo = in.readNBytes(maximo + 1);
            if (cuerpo.length > maximo) throw new FalloEnvio(false);
            int s = r.statusCode();
            if (s >= 200 && s < 300) return cuerpo;
            // 429 y 5xx son transitorios; 4xx (token, permisos, destinatario) requieren intervención.
            throw new FalloEnvio(s == 429 || s >= 500);
        } catch (IOException e) { throw new FalloEnvio(true); }
    }

    @Override public String toString() { return "GraphMetaCliente[token omitido]"; }
}
