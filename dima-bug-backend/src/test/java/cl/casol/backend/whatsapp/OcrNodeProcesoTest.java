package cl.casol.backend.whatsapp;
import cl.casol.backend.whatsapp.infrastructure.ocr.OcrNodeProcesoAdapter;
import cl.casol.backend.whatsapp.application.port.out.ReconocerImagenPort;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
class OcrNodeProcesoTest {
    Path raiz=Path.of("..").toAbsolutePath().normalize();
    @Test void imagenRealYAmbigua(){var ocr=new OcrNodeProcesoAdapter(raiz);assertEquals("Error 0x0000011b",ocr.reconocer("qa-media-legible").getFirst().texto());var a=ocr.reconocer("qa-media-ambiguo");assertTrue(a.getFirst().texto().contains("OX0000011b"));}
    @Test void sinTexto(){assertTrue(new OcrNodeProcesoAdapter(raiz).reconocer("qa-media-vacia").isEmpty());}
    @Test void corrupta(){assertThrows(ReconocerImagenPort.FalloOcr.class,()->new OcrNodeProcesoAdapter(raiz).reconocer("qa-media-corrupta"));}
    @Test void plazoRealMataProceso(){assertThrows(ReconocerImagenPort.PlazoOcr.class,()->new OcrNodeProcesoAdapter(raiz,Duration.ofMillis(1)).reconocer("qa-media-legible"));}
    @Test void rutaNoPermitida(){assertThrows(ReconocerImagenPort.FalloOcr.class,()->new OcrNodeProcesoAdapter(raiz).reconocer("../../secreto"));}
}
