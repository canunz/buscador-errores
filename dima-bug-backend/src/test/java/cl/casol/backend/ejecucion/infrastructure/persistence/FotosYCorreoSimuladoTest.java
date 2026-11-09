package cl.casol.backend.ejecucion.infrastructure.persistence;
import cl.casol.backend.ejecucion.infrastructure.automatizacion.*;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.domain.exception.ReglaEjecucionException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
class FotosYCorreoSimuladoTest {
    final FotosEvidenciaAdapter fotos=new FotosEvidenciaAdapter();
    @Test void normalizaFotografiasSinteticasAJpegSinDependerDelNombre()throws Exception{byte[] imagen=fotos.normalizar(fotos.muestra(EvidenciaFotografica.Categoria.MUEBLE));assertEquals(0xff,imagen[0]&255);assertEquals(0xd8,imagen[1]&255);assertNotNull(ImageIO.read(new ByteArrayInputStream(imagen)));}
    @Test void rechazaHtmlSvgYArchivoExcesivo(){for(byte[] archivo:new byte[][]{"<svg/>".getBytes(),"<html>foto</html>".getBytes(),new byte[2*1024*1024+1]})assertThrows(ReglaEjecucionException.class,()->fotos.normalizar(archivo));}
    @Test void noActivaGmailNiSimulaSilenciosamenteConModoDesconocido(){var c=new CorreoSimuladoAdapter("GMAIL",new MockEnvironment());assertFalse(c.disponible());assertFalse(c.simulado());assertThrows(ReglaEjecucionException.class,()->c.enviar(null,0));}
    @Test void simulacionSeBloqueaEnProduccionYBaseRemota(){assertThrows(IllegalStateException.class,()->new CorreoSimuladoAdapter("SIMULADO",new MockEnvironment().withProperty("spring.datasource.url","jdbc:mysql://remoto/produccion")));var env=new MockEnvironment();env.setActiveProfiles("prod");assertThrows(IllegalStateException.class,()->new CorreoSimuladoAdapter("SIMULADO",env));}
}
