package cl.casol.backend.whatsapp.application.service;

import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.domain.*;
import static cl.casol.backend.whatsapp.domain.RespuestaCanal.Estado.*;
import java.time.*;
import java.util.*;

/** Estado exclusivamente temporal QA. Serialización acotada: un OCR a la vez. */
public final class ProcesarMensajeService implements ConversacionCanal {
    private record Conversacion(String cuenta, String canal, String usuario) { }
    private record Evento(Conversacion conversacion, String id) { }
    private record Pendiente(String referencia, String consulta,
                             List<RespuestaCanal.Candidato> candidatos, Instant vence) { }
    private final Map<Conversacion, Pendiente> pendientes = new HashMap<>();
    private final Map<Evento, Instant> vistos = new HashMap<>();
    private final BuscarSolucionesCanalPort buscador;
    private final ReconocerImagenPort ocr;
    private final Clock reloj;
    public ProcesarMensajeService(BuscarSolucionesCanalPort buscador, ReconocerImagenPort ocr, Clock reloj) {
        this.buscador = buscador; this.ocr = ocr; this.reloj = reloj;
    }
    /** Una instancia por reserva durable: reutiliza las reglas sin depender de JPA. */
    public synchronized void restaurar(MensajeCanal m, EstadoConversacion estado) {
        if (!pendientes.isEmpty() || !vistos.isEmpty()) throw new IllegalStateException("Procesador ya utilizado");
        if (estado!=null) pendientes.put(new Conversacion(m.cuenta(),m.canal(),m.usuario()),
            new Pendiente(estado.referencia(),estado.consulta(),estado.candidatos(),Instant.ofEpochMilli(estado.venceEpochMilli())));
    }
    public synchronized EstadoConversacion instantanea(MensajeCanal m) {
        var p=pendientes.get(new Conversacion(m.cuenta(),m.canal(),m.usuario()));
        return p==null?null:new EstadoConversacion(p.referencia(),p.consulta(),p.candidatos(),p.vence().toEpochMilli());
    }
    public synchronized RespuestaCanal procesar(MensajeCanal m) {
        Instant ahora = reloj.instant();
        var clave = new Conversacion(m.cuenta(),m.canal(),m.usuario());
        boolean vencida = pendientes.containsKey(clave) && !pendientes.get(clave).vence().isAfter(ahora);
        pendientes.entrySet().removeIf(e -> !e.getValue().vence().isAfter(ahora));
        vistos.entrySet().removeIf(e -> !e.getValue().isAfter(ahora));
        var evento = new Evento(clave,m.id());
        if (vistos.containsKey(evento)) return simple(DUPLICADO,"Este mensaje ya fue procesado.");
        if (vistos.size() >= 1000) return simple(SATURADO,"La simulación alcanzó su límite temporal. Intenta más tarde.");
        vistos.put(evento,ahora.plus(Duration.ofHours(1)));
        if (m.tipo()==MensajeCanal.Tipo.NO_COMPATIBLE) return simple(NO_COMPATIBLE,"La simulación admite texto e imágenes.");
        if (m.tipo()==MensajeCanal.Tipo.IMAGEN) {
            pendientes.remove(clave);
            if (pendientes.size()>=100) return simple(SATURADO,"Hay demasiadas conversaciones pendientes.");
            try {
                var candidatos = List.copyOf(ocr.reconocer(m.contenido()));
                if (candidatos.size()>3 || candidatos.stream().anyMatch(c -> c.texto()==null || c.texto().length()>20000))
                    return simple(ERROR_OCR,"El reconocimiento devolvió un resultado no válido.");
                var p = new Pendiente(UUID.randomUUID().toString(), candidatos.isEmpty()?"":candidatos.getFirst().texto(),
                        candidatos,reloj.instant().plus(Duration.ofMinutes(15)));
                pendientes.put(clave,p);
                return propuesta(p);
            } catch (ReconocerImagenPort.PlazoOcr e) { return simple(TIEMPO_AGOTADO,"El reconocimiento tardó demasiado. Intenta con otra imagen."); }
              catch (RuntimeException e) { return simple(ERROR_OCR,"No fue posible reconocer la imagen. Intenta otra o escribe una consulta."); }
        }
        String texto = m.contenido().strip();
        if (texto.isEmpty() || texto.length()>450 || texto.codePoints().anyMatch(c -> Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'))
            return simple(INVALIDO,"Escribe una consulta válida de hasta 300 caracteres.");
        String[] partes = texto.split("\\s+",3);
        String orden = partes[0].toUpperCase(Locale.ROOT);
        boolean comando = Set.of("CONFIRMAR","CORREGIR","ELEGIR","CANCELAR").contains(orden);
        var p = pendientes.get(clave);
        if (comando) {
            if (p==null) return simple(vencida?EXPIRADO:INVALIDO,"No hay una propuesta vigente. Envía otra imagen.");
            if (partes.length<2 || !p.referencia().equals(partes[1]))
                return simple(INVALIDO,"Usa la referencia de la propuesta vigente para confirmar, corregir o cancelar.");
            if (orden.equals("CANCELAR") && partes.length==2) { pendientes.remove(clave); return simple(CANCELADO,"Reconocimiento cancelado."); }
            if (orden.equals("CONFIRMAR") && partes.length==2) {
                if (!consultaValida(p.consulta())) return propuesta(p);
                var resultado = buscar(p.consulta());
                if (resultado.estado()!=ERROR_BUSCADOR) pendientes.remove(clave);
                return resultado;
            }
            String nueva = "";
            if (orden.equals("CORREGIR") && partes.length==3) nueva=partes[2].strip();
            if (orden.equals("ELEGIR") && partes.length==3 && partes[2].matches("[1-3]")) {
                int indice=Integer.parseInt(partes[2])-1;
                if (indice<p.candidatos().size()) nueva=p.candidatos().get(indice).texto();
            }
            if (!consultaValida(nueva)) return simple(INVALIDO,"La consulta debe tener entre 1 y 300 caracteres.");
            var corregida = new Pendiente(UUID.randomUUID().toString(),nueva,p.candidatos(),p.vence());
            pendientes.put(clave,corregida);
            return propuesta(corregida);
        }
        if (p!=null) return propuesta(p); // Nunca interpretar texto suelto como confirmación.
        if (!consultaValida(texto)) return simple(INVALIDO,"La consulta debe tener entre 1 y 300 caracteres.");
        return buscar(texto);
    }
    private boolean consultaValida(String t) { return t!=null && !t.isBlank() && t.length()<=300; }
    private RespuestaCanal buscar(String consulta) {
        try {
            var soluciones=buscador.buscar(consulta);
            return new RespuestaCanal(soluciones.isEmpty()?SIN_RESULTADOS:RESULTADOS,
                    soluciones.isEmpty()?"No encontré una solución de autoservicio. Contacta a Soporte.":"Encontré estas soluciones de autoservicio.",
                    null,soluciones,List.of(),List.of());
        } catch (cl.casol.backend.whatsapp.application.port.out.FalloCatalogoPublico e) { throw e; }
          catch (RuntimeException e) { return simple(ERROR_BUSCADOR,"No fue posible consultar. Reintenta con un nuevo mensaje; tu propuesta sigue disponible si no ha vencido."); }
    }
    private RespuestaCanal propuesta(Pendiente p) {
        var avisos = new ArrayList<String>();
        p.candidatos().forEach(c -> avisos.addAll(c.avisos()));
        avisos.add("Revisa los códigos: no se sustituyen O/0 ni l/1.");
        return new RespuestaCanal(CONFIRMACION,
                (p.consulta().isEmpty()?"No se identificó un error. Corrige manualmente.":"Consulta propuesta: "+p.consulta())+
                " Usa CONFIRMAR <referencia>, CORREGIR <referencia> <texto>, ELEGIR <referencia> <1-3> o CANCELAR <referencia>.",
                p.referencia(),List.of(),p.candidatos(),avisos);
    }
    private RespuestaCanal simple(RespuestaCanal.Estado e,String mensaje) { return RespuestaCanal.simple(e,mensaje); }
}
