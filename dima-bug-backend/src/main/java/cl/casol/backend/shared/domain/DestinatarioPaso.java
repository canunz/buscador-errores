package cl.casol.backend.shared.domain;
import java.util.*;
public record DestinatarioPaso(String correo,Tipo tipo) {
    public enum Tipo { PRINCIPAL, COPIA }
    public DestinatarioPaso {
        if(correo==null || correo.length()>120 || !correo.strip().matches("(?i)[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@dimarsa\\.cl"))
            throw new DestinatarioInvalidoException("Usa un correo válido @dimarsa.cl, sin listas ni caracteres de control");
        correo=correo.strip().toLowerCase(Locale.ROOT);
        String local=correo.substring(0,correo.indexOf('@'));
        if(local.startsWith(".") || local.endsWith(".") || local.contains("..") || local.length()>64)throw new DestinatarioInvalidoException("Correo inválido");
        if(tipo==null)tipo=Tipo.PRINCIPAL;
    }
    public static List<DestinatarioPaso> normalizar(Collection<DestinatarioPaso> valores) {
        var unicos=new LinkedHashMap<String,DestinatarioPaso>();
        if(valores!=null)for(var d:valores){if(d==null)throw new DestinatarioInvalidoException("Destinatario vacío");var previo=unicos.get(d.correo());if(previo==null || d.tipo()==Tipo.PRINCIPAL)unicos.put(d.correo(),d);}
        if(unicos.size()>20)throw new DestinatarioInvalidoException("Máximo 20 destinatarios distintos");
        return List.copyOf(unicos.values());
    }
}
