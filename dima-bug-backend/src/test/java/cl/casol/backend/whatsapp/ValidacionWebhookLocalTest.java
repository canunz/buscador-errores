package cl.casol.backend.whatsapp;
import cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class ValidacionWebhookLocalTest {
 @Test void hmacConVectorIndependiente(){
   var v=new ValidacionWebhookLocal("token-sintetico-http-2026","clave-sintetica-http-2026");
   byte[] b="{\"mensaje\":\"á error\"}\n".getBytes(StandardCharsets.UTF_8);
   assertDoesNotThrow(()->v.autenticar(b,"sha256=86a694e8db404e7692f5e526c28c4a7b33bcc89b427860876cef6b879f6c8260"));
   b[0]='[';assertThrows(ValidacionWebhookLocal.Rechazo.class,()->v.autenticar(b,"sha256=86a694e8db404e7692f5e526c28c4a7b33bcc89b427860876cef6b879f6c8260"));
 }
 @Test void secretosObligatoriosSinFiltrarlos(){
   for(String s:new String[]{null,"","corto","x".repeat(257)})assertThrows(IllegalArgumentException.class,()->new ValidacionWebhookLocal(s,"clave-sintetica-http-2026"));
   assertThrows(IllegalArgumentException.class,()->new ValidacionWebhookLocal("token-sintetico-http-2026",null));
   var v=new ValidacionWebhookLocal("token-sintetico-http-2026","clave-sintetica-http-2026");assertFalse(v.toString().contains("sintetica"));
 }
 @Test void challengeYTokenFueraDeLimitesSeRechazan(){
   var v=new ValidacionWebhookLocal("token-sintetico-http-2026","clave-sintetica-http-2026");
   for(String c:new String[]{"","x".repeat(1025),"x\n"})assertThrows(ValidacionWebhookLocal.Rechazo.class,()->v.verificar("subscribe","token-sintetico-http-2026",c));
   assertThrows(ValidacionWebhookLocal.Rechazo.class,()->v.verificar("subscribe","x".repeat(257),"x"));
 }
}
