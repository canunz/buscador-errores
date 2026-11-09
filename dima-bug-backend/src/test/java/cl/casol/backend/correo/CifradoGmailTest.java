package cl.casol.backend.correo;
import cl.casol.backend.correo.infrastructure.gmail.*;
import cl.casol.backend.correo.application.service.GmailException;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class CifradoGmailTest {
    static GmailConfig config(String clave){return new GmailConfig(true,false,"id-qa","secreto-qa","http://localhost:9090/login/oauth2/code/google","qa@dimarsa.cl",clave,"http://localhost:4200/admin/gmail");}
    static String clave(){return Base64.getEncoder().encodeToString(new byte[32]);}
    @Test void cifraConNonceAleatorioYRecupera(){var c=new CifradoGmailAes(config(clave()));String a=c.cifrar("token-sintetico","gmail:refresh"),b=c.cifrar("token-sintetico","gmail:refresh");assertNotEquals(a,b);assertFalse(a.contains("token-sintetico"));assertEquals("token-sintetico",c.descifrar(a,"gmail:refresh"));}
    @Test void rechazaCambioDeContexto(){var c=new CifradoGmailAes(config(clave()));String valor=c.cifrar("qa","gmail:refresh");assertThrows(GmailException.class,()->c.descifrar(valor,"gmail:access"));}
    @Test void rechazaManipulacionYCifradoConOtraClave(){var c=new CifradoGmailAes(config(clave()));String valor=c.cifrar("qa","gmail:refresh");assertThrows(GmailException.class,()->c.descifrar(valor.substring(0,valor.length()-5)+"AAAAA","gmail:refresh"));byte[] key=new byte[32];key[0]=1;var otro=new CifradoGmailAes(config(Base64.getEncoder().encodeToString(key)));assertThrows(GmailException.class,()->otro.descifrar(valor,"gmail:refresh"));}
    @Test void claveAusenteNoPermiteCifrar(){var c=new CifradoGmailAes(config(""));assertFalse(c.disponible());assertThrows(GmailException.class,()->c.cifrar("qa","gmail:refresh"));}
    @Test void configNoAceptaCallbackExternoSinTls(){var c=new GmailConfig(true,false,"id","secret","http://servidor-remoto/login/oauth2/code/google","qa@dimarsa.cl",clave(),"http://localhost:4200/admin/gmail");assertFalse(c.configurado());}
}
