package cl.casol.backend.procedimiento.domain;
import cl.casol.backend.shared.domain.DestinatarioPaso;
import cl.casol.backend.procedimiento.domain.exception.EstadoProcedimientoInvalidoException;
import java.util.*;
public record ConfiguracionPaso(List<DestinatarioPaso> destinatarios,Boolean usarDestinatariosSucursal,String mensaje,
        List<String> evidenciasRequeridas,Boolean requiereAprobacion,List<Integer> recordatoriosHoras,
        Integer plazoRespuestaHoras,Condicion condicionFinalizacion,String accionAutomatica,String asunto) {
    public ConfiguracionPaso(List<DestinatarioPaso> d,Boolean u,String m,List<String> e,Boolean a,List<Integer> r,Integer p,Condicion c,String accion){this(d,u,m,e,a,r,p,c,accion,null);}
    public enum Condicion { CONFIRMACION_USUARIO, AL_ACTIVAR, EVIDENCIAS_APROBADAS, EVIDENCIAS_RECIBIDAS, ENVIO_REGISTRADO, RESPUESTA_VALIDADA, ACCION_EXITOSA }
    public ConfiguracionPaso {
        destinatarios=DestinatarioPaso.normalizar(destinatarios);
        if(asunto!=null && (asunto.isBlank() || asunto.length()>200 || asunto.codePoints().anyMatch(Character::isISOControl)))throw invalida("Asunto: entre 1 y 200 caracteres, sin controles");
        if(mensaje!=null && mensaje.length()>2000)throw invalida("Mensaje: máximo 2000 caracteres");
        if(evidenciasRequeridas!=null && evidenciasRequeridas.stream().anyMatch(c->c==null || !Set.of("MUEBLE","RED","ELECTRICIDAD","FOTO_GENERAL").contains(c)))throw invalida("Categoría de evidencia no permitida");
        evidenciasRequeridas=evidenciasRequeridas==null?null:List.copyOf(new LinkedHashSet<>(evidenciasRequeridas));
        if(recordatoriosHoras!=null && recordatoriosHoras.stream().anyMatch(Objects::isNull))throw invalida("Recordatorio vacío");
        recordatoriosHoras=recordatoriosHoras==null?null:List.copyOf(new TreeSet<>(recordatoriosHoras));
        if(plazoRespuestaHoras!=null && (plazoRespuestaHoras<1 || plazoRespuestaHoras>8760))throw invalida("Plazo: entre 1 y 8760 horas");
        if(recordatoriosHoras!=null && (recordatoriosHoras.size()>8 || recordatoriosHoras.stream().anyMatch(h->h==null || h<1 || (plazoRespuestaHoras!=null && h>=plazoRespuestaHoras))))throw invalida("Recordatorios positivos, distintos y anteriores al plazo");
        if(accionAutomatica!=null && !accionAutomatica.equals("VALIDAR_CONTEXTO_SUCURSAL"))throw invalida("Acción automática no autorizada");
    }
    public static ConfiguracionPaso resolver(ConfiguracionPaso c,ModoEjecucionPaso modo,boolean critico,String mensaje) {
        boolean evidencia=modo.esEvidencia();
        var condicion=c==null?null:c.condicionFinalizacion();
        if(condicion==null)condicion=evidencia?Condicion.EVIDENCIAS_APROBADAS:modo==ModoEjecucionPaso.ENVIAR_CORREO?Condicion.ENVIO_REGISTRADO:modo==ModoEjecucionPaso.AUTOMATICO?Condicion.ACCION_EXITOSA:Condicion.CONFIRMACION_USUARIO;
        boolean aprobacion=c==null || c.requiereAprobacion()==null?critico || evidencia || condicion==Condicion.RESPUESTA_VALIDADA:c.requiereAprobacion();
        if(critico && !aprobacion)throw invalida("Un paso crítico conserva validación humana explícita");
        if(evidencia && !Set.of(Condicion.EVIDENCIAS_APROBADAS,Condicion.EVIDENCIAS_RECIBIDAS).contains(condicion))throw invalida("Condición incompatible con solicitud de evidencia");
        if(evidencia && condicion==Condicion.EVIDENCIAS_APROBADAS && !aprobacion)throw invalida("La condición exige aprobación");
        if(modo==ModoEjecucionPaso.ENVIAR_CORREO && !Set.of(Condicion.ENVIO_REGISTRADO,Condicion.RESPUESTA_VALIDADA).contains(condicion))throw invalida("El correo finaliza por envío registrado o respuesta validada");
        if(modo.esManual() && condicion!=Condicion.CONFIRMACION_USUARIO)throw invalida("El paso manual requiere confirmación del usuario");
        if(modo==ModoEjecucionPaso.INFORMATIVO && !Set.of(Condicion.AL_ACTIVAR,Condicion.CONFIRMACION_USUARIO).contains(condicion))throw invalida("Condición informativa inválida");
        if(modo==ModoEjecucionPaso.AUTOMATICO && condicion!=Condicion.ACCION_EXITOSA)throw invalida("El automático exige una acción verificable exitosa");
        if(critico && condicion==Condicion.AL_ACTIVAR)throw invalida("Un paso crítico no se completa al activarse");
        int plazo=c==null || c.plazoRespuestaHoras()==null?120:c.plazoRespuestaHoras();
        var reglas=c==null || c.recordatoriosHoras()==null?List.of(24,72).stream().filter(h->h<plazo).toList():c.recordatoriosHoras();
        return new ConfiguracionPaso(c==null?List.of():c.destinatarios(),c==null || c.usarDestinatariosSucursal()==null?modo!=ModoEjecucionPaso.ENVIAR_CORREO:c.usarDestinatariosSucursal(),
            c!=null && c.mensaje()!=null?c.mensaje():mensaje,evidencia?(c==null || c.evidenciasRequeridas()==null?List.of("MUEBLE","RED","ELECTRICIDAD"):c.evidenciasRequeridas()):List.of(),
            aprobacion,reglas,plazo,condicion,modo==ModoEjecucionPaso.AUTOMATICO?"VALIDAR_CONTEXTO_SUCURSAL":null,c!=null && c.asunto()!=null?c.asunto():evidencia?"Solicitud de fotografías":"Solicitud del procedimiento");
    }
    private static EstadoProcedimientoInvalidoException invalida(String mensaje){return new EstadoProcedimientoInvalidoException(mensaje);}
}
