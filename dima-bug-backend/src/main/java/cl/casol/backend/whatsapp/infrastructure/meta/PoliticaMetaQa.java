package cl.casol.backend.whatsapp.infrastructure.meta;

import java.util.*;

/** Alias explícitos de QA. Nunca relaciona automáticamente teléfonos con usuarios internos. */
public final class PoliticaMetaQa {
    private final String waba,numero;
    private final Map<String,String> participantes;
    public static final class NoAutorizado extends RuntimeException {
        public enum Validacion { WABA, PHONE_NUMBER_ID, PARTICIPANTE, ALIAS }
        private final Validacion validacion;
        private final String elemento;
        public NoAutorizado(){this(Validacion.PARTICIPANTE,"MENSAJE");}
        public NoAutorizado(Validacion v,String elemento){super("Evento fuera del alcance de QA");this.validacion=v;this.elemento=elemento;}
        public Validacion validacion(){return validacion;}
        public String elemento(){return elemento;}
        public NoAutorizado enStatuses(){return new NoAutorizado(validacion,"STATUSES");}
    }
    public PoliticaMetaQa(String waba,String numero,String aliases){
        if(!waba.matches("[A-Za-z0-9_.:-]{1,128}")||!numero.matches("[A-Za-z0-9_.:-]{1,128}"))throw new IllegalArgumentException("Configura los activos de QA");
        this.waba=waba;this.numero=numero;
        var mapa=new HashMap<String,String>();
        for(String entrada:aliases.split(",",-1)){
            String[] par=entrada.strip().split("=",-1);
            if(par.length!=2||!par[0].matches("(?:tel:[0-9]{5,20}|bsuid:[A-Za-z0-9_.:/+-]{1,128})")||
                    !par[1].matches("[A-Za-z0-9_.:-]{1,128}")||mapa.putIfAbsent(par[0],par[1])!=null)
                throw new IllegalArgumentException("Lista de participantes QA inválida");
        }
        participantes=Map.copyOf(mapa);
    }
    public void cuenta(String recibida){if(!waba.equals(recibida))throw new NoAutorizado(NoAutorizado.Validacion.WABA,"LOTE");}
    public void numero(String recibido){if(!numero.equals(recibido))throw new NoAutorizado(NoAutorizado.Validacion.PHONE_NUMBER_ID,"LOTE");}
    public String participante(String telefono,String bsuid){
        String tel=telefono==null?null:participantes.get("tel:"+telefono);
        String b=bsuid==null?null:participantes.get("bsuid:"+bsuid);
        if(telefono!=null&&tel==null||bsuid!=null&&b==null||tel==null&&b==null)throw new NoAutorizado(NoAutorizado.Validacion.PARTICIPANTE,"MENSAJE");
        if(tel!=null&&b!=null&&!tel.equals(b))throw new NoAutorizado(NoAutorizado.Validacion.ALIAS,"MENSAJE");
        return b==null?tel:b;
    }
    /** Resolución inversa alias → teléfono para responder. Solo alias autorizados con entrada tel:. */
    public String telefono(String alias){
        if(alias==null)throw new NoAutorizado(NoAutorizado.Validacion.ALIAS,"RESPUESTA");
        return participantes.entrySet().stream().filter(e->e.getKey().startsWith("tel:")&&e.getValue().equals(alias))
            .map(e->e.getKey().substring(4)).sorted().findFirst()
            .orElseThrow(()->new NoAutorizado(NoAutorizado.Validacion.ALIAS,"RESPUESTA"));
    }
    public String numero(){return numero;}
    @Override public String toString(){return "PoliticaMetaQa[activos y participantes omitidos]";}
}
