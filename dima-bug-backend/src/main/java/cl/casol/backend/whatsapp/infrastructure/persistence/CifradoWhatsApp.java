package cl.casol.backend.whatsapp.infrastructure.persistence;

import tools.jackson.databind.json.JsonMapper;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Sobre v1 AES-256-GCM con AAD; HMAC independiente y sin secretos por defecto. */
public final class CifradoWhatsApp {
    private final byte[] cifrado,hmac;
    private final SecureRandom random=new SecureRandom();
    private final JsonMapper json=JsonMapper.builder().build();
    public CifradoWhatsApp(String cifradoBase64,String hmacBase64) {
        try { cifrado=Base64.getDecoder().decode(cifradoBase64);hmac=Base64.getDecoder().decode(hmacBase64); }
        catch(RuntimeException e){throw new IllegalArgumentException("Claves WhatsApp inválidas");}
        if(cifrado.length!=32 || hmac.length!=32 || MessageDigest.isEqual(cifrado,hmac))
            throw new IllegalArgumentException("Se requieren dos claves WhatsApp diferentes de 32 bytes");
    }
    public byte[] huella(String... partes) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(hmac,"HmacSHA256"));
            for(String parte:partes){byte[] b=parte.getBytes(StandardCharsets.UTF_8);mac.update(ByteBuffer.allocate(4).putInt(b.length).array());mac.update(b);}
            return mac.doFinal();
        } catch(GeneralSecurityException e){throw new IllegalStateException("HMAC no disponible");}
    }
    public byte[] proteger(Object valor,String contexto) {
        try {
            byte[] claro=json.writeValueAsBytes(valor);
            if(claro.length>262144)throw new IllegalArgumentException();
            byte[] iv=new byte[12];random.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(cifrado,"AES"),new GCMParameterSpec(128,iv));
            cipher.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            byte[] protegido=cipher.doFinal(claro);
            return ByteBuffer.allocate(13+protegido.length).put((byte)1).put(iv).put(protegido).array();
        } catch(Exception e){throw new IllegalStateException("No se pudo proteger el contenido WhatsApp");}
    }
    public <T>T leer(byte[] sobre,Class<T> tipo,String contexto) {
        try {
            if(sobre==null || sobre.length<29 || sobre.length>262173 || sobre[0]!=1)throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(cifrado,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(sobre,1,13)));
            cipher.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            return json.readValue(cipher.doFinal(sobre,13,sobre.length-13),tipo);
        } catch(Exception e){throw new IllegalStateException("Contenido WhatsApp no disponible");}
    }
    @Override public String toString(){return "CifradoWhatsApp[claves omitidas]";}
}
