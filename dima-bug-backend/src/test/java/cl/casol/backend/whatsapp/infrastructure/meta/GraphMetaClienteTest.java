package cl.casol.backend.whatsapp.infrastructure.meta;

import cl.casol.backend.whatsapp.application.port.out.EnviarMensajeCanalPort.FalloEnvio;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Graph simulado en loopback: nunca contacta a Meta. */
class GraphMetaClienteTest {
    HttpServer server;
    AtomicReference<String> cuerpo = new AtomicReference<>(), auth = new AtomicReference<>(), ruta = new AtomicReference<>();
    AtomicInteger estado = new AtomicInteger(200);
    AtomicReference<String> respuesta = new AtomicReference<>("{\"messages\":[{\"id\":\"wamid.QA123\"}]}");
    GraphMetaCliente cliente;

    @BeforeEach void iniciar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            ruta.set(ex.getRequestURI().toString()); auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            cuerpo.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = respuesta.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(estado.get(), b.length); ex.getResponseBody().write(b); ex.close();
        });
        server.start();
        cliente = new GraphMetaCliente(HttpClient.newHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort(), "v23.0", "EAAqaTOKEN");
    }
    @AfterEach void detener() { server.stop(0); }

    @Test void enviaTextoAlNumeroQa() {
        assertEquals("wamid.QA123", cliente.enviarTexto("123456", "56911111111", "¡Hola! ¿En qué puedo ayudarte?"));
        assertEquals("/v23.0/123456/messages", ruta.get());
        assertEquals("Bearer EAAqaTOKEN", auth.get());
        assertTrue(cuerpo.get().contains("\"to\":\"56911111111\""));
        assertTrue(cuerpo.get().contains("¿En qué puedo ayudarte?"));
    }
    @Test void erroresTransitoriosYDefinitivos() {
        estado.set(503); assertTrue(assertThrows(FalloEnvio.class, () -> cliente.enviarTexto("1", "2", "x")).reintentable());
        estado.set(429); assertTrue(assertThrows(FalloEnvio.class, () -> cliente.enviarTexto("1", "2", "x")).reintentable());
        estado.set(401); assertFalse(assertThrows(FalloEnvio.class, () -> cliente.enviarTexto("1", "2", "x")).reintentable());
    }
    @Test void medioSoloDesdeOrigenMetaHttps() {
        respuesta.set("{\"url\":\"http://127.0.0.1/robar\",\"mime_type\":\"image/jpeg\",\"file_size\":10}");
        assertThrows(IllegalArgumentException.class, () -> cliente.descargarImagen("123", "1"));
        respuesta.set("{\"url\":\"https://evil.example/x\",\"mime_type\":\"image/jpeg\",\"file_size\":10}");
        assertThrows(IllegalArgumentException.class, () -> cliente.descargarImagen("123", "1"));
        respuesta.set("{\"url\":\"https://lookaside.fbsbx.com/x\",\"mime_type\":\"application/pdf\",\"file_size\":10}");
        assertThrows(IllegalArgumentException.class, () -> cliente.descargarImagen("123", "1"));
        assertThrows(IllegalArgumentException.class, () -> cliente.descargarImagen("../otro", "1"));
    }
    @Test void configuracionInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new GraphMetaCliente("v23.0", ""));
        assertThrows(IllegalArgumentException.class, () -> new GraphMetaCliente("latest", "EAAqa"));
    }
    @Test void respondeSoloAParticipantesAutorizados() {
        var politica = new PoliticaMetaQa("waba", "123456", "tel:56911111111=qa-a,bsuid:CL.x=qa-b");
        var envio = new EnvioMetaGraphAdapter(cliente, politica);
        assertEquals("wamid.QA123", envio.enviar("qa-a", "hola"));
        assertTrue(cuerpo.get().contains("56911111111"));
        assertFalse(assertThrows(FalloEnvio.class, () -> envio.enviar("qa-b", "hola")).reintentable()); // Sin teléfono registrado.
        assertFalse(assertThrows(FalloEnvio.class, () -> envio.enviar("desconocido", "hola")).reintentable());
    }
}
