package cl.casol.backend.whatsapp.infrastructure.http;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Secretos externos: nunca se incluyen en respuestas, excepciones ni toString. */
public final class ValidacionWebhookLocal {
    public static final int MAX_BYTES = 16 * 1024;
    private final byte[] token, secreto;
    private final int limite;
    public enum Motivo { PARAMETROS, FIRMA, TAMANO, EVENTO, INTERNO }
    public static final class Rechazo extends RuntimeException {
        private final Motivo motivo;
        public Rechazo(Motivo motivo) { super("Solicitud de webhook rechazada"); this.motivo=motivo; }
        public Motivo motivo() { return motivo; }
    }
    public ValidacionWebhookLocal(String token, String secreto) {
        this(token,secreto,MAX_BYTES);
    }
    public ValidacionWebhookLocal(String token,String secreto,int limite) {
        if(limite<MAX_BYTES||limite>3*1024*1024)throw new IllegalArgumentException("Límite de webhook inválido");
        this.limite=limite;
        if (!secretoValido(token) || !secretoValido(secreto))
            throw new IllegalArgumentException("Configura ambos secretos externos del webhook local (16 a 256 caracteres).");
        this.token=token.getBytes(StandardCharsets.UTF_8);
        this.secreto=secreto.getBytes(StandardCharsets.UTF_8);
    }
    private boolean secretoValido(String valor) {
        return valor!=null && valor.length()>=16 && valor.length()<=256 && !valor.isBlank() &&
                valor.codePoints().noneMatch(Character::isISOControl);
    }
    public String verificar(String modo, String recibido, String challenge) {
        if (!"subscribe".equals(modo) || recibido==null || recibido.length()>256 ||
                !MessageDigest.isEqual(token,recibido.getBytes(StandardCharsets.UTF_8)) ||
                challenge==null || challenge.isEmpty() || challenge.length()>1024 ||
                challenge.codePoints().anyMatch(Character::isISOControl)) throw new Rechazo(Motivo.PARAMETROS);
        return challenge; // Nunca convertir a número, trim, JSON ni añadir salto de línea.
    }
    public void autenticar(byte[] bytes,String firma) {
        if (bytes==null || bytes.length>limite) throw new Rechazo(Motivo.TAMANO);
        if (firma==null || !firma.matches("sha256=[0-9a-fA-F]{64}")) throw new Rechazo(Motivo.FIRMA);
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto,"HmacSHA256"));
            byte[] esperada=mac.doFinal(bytes);
            byte[] recibida=HexFormat.of().parseHex(firma.substring(7));
            if (!MessageDigest.isEqual(esperada,recibida)) throw new Rechazo(Motivo.FIRMA);
        } catch(GeneralSecurityException e) { throw new Rechazo(Motivo.INTERNO); }
    }
    @Override public String toString(){return "ValidacionWebhookLocal[secretos omitidos]";}
}
