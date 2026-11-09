package cl.casol.backend.correo.infrastructure.gmail;

import cl.casol.backend.correo.application.port.out.CifradoGmailPort;
import cl.casol.backend.correo.application.service.GmailException;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cl.casol.backend.correo.application.service.GmailException.Tipo.CONFIGURACION;

@Component
public class CifradoGmailAes implements CifradoGmailPort {
    private final byte[] clave;private final SecureRandom random=new SecureRandom();
    public CifradoGmailAes(GmailConfig config){byte[] temporal;try{temporal=Base64.getDecoder().decode(config.claveCifrado);}catch(IllegalArgumentException ex){temporal=new byte[0];}clave=temporal;}
    public boolean disponible(){return clave.length==32;}
    public String cifrar(String texto,String contexto){
        if(!disponible() || texto==null)throw new GmailException(CONFIGURACION);
        try{byte[] iv=new byte[12];random.nextBytes(iv);var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(clave,"AES"),new GCMParameterSpec(128,iv));c.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));byte[] resultado=c.doFinal(texto.getBytes(StandardCharsets.UTF_8));byte[] junto=new byte[iv.length+resultado.length];System.arraycopy(iv,0,junto,0,iv.length);System.arraycopy(resultado,0,junto,iv.length,resultado.length);return "v1:"+Base64.getEncoder().encodeToString(junto);}
        catch(Exception ex){throw new GmailException(CONFIGURACION);}
    }
    public String descifrar(String texto,String contexto){
        if(!disponible() || texto==null || !texto.startsWith("v1:"))throw new GmailException(CONFIGURACION);
        try{byte[] junto=Base64.getDecoder().decode(texto.substring(3));if(junto.length<29)throw new IllegalArgumentException();var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(clave,"AES"),new GCMParameterSpec(128,Arrays.copyOf(junto,12)));c.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));return new String(c.doFinal(junto,12,junto.length-12),StandardCharsets.UTF_8);}
        catch(Exception ex){throw new GmailException(CONFIGURACION);}
    }
}
