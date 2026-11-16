package cl.casol.backend.ejecucion.infrastructure.automatizacion;
import cl.casol.backend.ejecucion.application.port.out.FotosEvidenciaPort;
import cl.casol.backend.ejecucion.domain.EvidenciaFotografica.Categoria;
import cl.casol.backend.ejecucion.domain.exception.ReglaEjecucionException;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
@Component
public class FotosEvidenciaAdapter implements FotosEvidenciaPort {
    public byte[] normalizar(byte[] entrada) {
        if(entrada==null || entrada.length==0 || entrada.length>2*1024*1024)throw invalida();
        try(var in=ImageIO.createImageInputStream(new ByteArrayInputStream(entrada))) {
            var readers=ImageIO.getImageReaders(in);
            if(!readers.hasNext())throw invalida();
            var reader=readers.next();
            try {
                String formato=reader.getFormatName();
                if(!formato.equalsIgnoreCase("JPEG") && !formato.equalsIgnoreCase("PNG"))throw invalida();
                reader.setInput(in,true,true);int ancho=reader.getWidth(0),alto=reader.getHeight(0);
                if(ancho<1 || alto<1 || ancho>4000 || alto>4000 || (long)ancho*alto>4_000_000)throw invalida();
                return jpeg(reader.read(0));
            } finally {reader.dispose();}
        } catch(IOException | RuntimeException e) {throw invalida();}
    }
    public byte[] muestra(Categoria categoria) {
        var image=new BufferedImage(900,500,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try {
            g.setColor(new Color(16,34,80));g.fillRect(0,0,900,500);g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,36));g.drawString("SIMULACIÓN DIMABUG",65,120);
            g.drawString(categoria.name(),65,230);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,24));
            g.drawString("Imagen de prueba. No acredita una instalación real.",65,360);
        } finally {g.dispose();}
        try {return jpeg(image);} catch(IOException e) {throw new IllegalStateException("No se pudo generar la imagen de prueba");}
    }
    private byte[] jpeg(BufferedImage original) throws IOException {
        var limpia=new BufferedImage(original.getWidth(),original.getHeight(),BufferedImage.TYPE_INT_RGB);var g=limpia.createGraphics();
        try {g.setColor(Color.WHITE);g.fillRect(0,0,limpia.getWidth(),limpia.getHeight());g.drawImage(original,0,0,null);}finally{g.dispose();}
        var out=new ByteArrayOutputStream();if(!ImageIO.write(limpia,"jpg",out) || out.size()>2*1024*1024)throw invalida();return out.toByteArray();
    }
    private ReglaEjecucionException invalida() {return new ReglaEjecucionException("Foto inválida: usa JPEG o PNG, máximo 2 MB y 4 megapíxeles por categoría");}
}
