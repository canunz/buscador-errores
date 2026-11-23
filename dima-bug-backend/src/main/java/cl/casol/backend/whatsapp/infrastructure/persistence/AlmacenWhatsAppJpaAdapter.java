package cl.casol.backend.whatsapp.infrastructure.persistence;

import cl.casol.backend.whatsapp.application.port.out.AlmacenWhatsAppPort;
import cl.casol.backend.whatsapp.domain.*;
import jakarta.persistence.*;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** Cada método abre una transacción corta independiente; nunca contiene OCR ni red. */
@Repository
@Profile(PerfilesWhatsApp.DURABLE)
public class AlmacenWhatsAppJpaAdapter implements AlmacenWhatsAppPort {
    private static final List<String> ACTIVOS=List.of("PENDIENTE","EN_PROCESO","REINTENTO");
    private final EntityManager em;
    private final TransactionTemplate tx;
    private final CifradoWhatsApp crypto;
    private final boolean mysql;
    public AlmacenWhatsAppJpaAdapter(EntityManager em,PlatformTransactionManager manager,CifradoWhatsApp crypto,DataSource datasource){
        this.em=em;this.crypto=crypto;tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(10);
        try(var conexion=datasource.getConnection()){
            String producto=conexion.getMetaData().getDatabaseProductName();mysql="MySQL".equals(producto);
            if(!mysql&&!"H2".equals(producto))throw new IllegalStateException("Motor WhatsApp no validado");
        }catch(java.sql.SQLException e){throw new IllegalStateException("Base WhatsApp no disponible");}
    }
    private <T>T atomico(Supplier<T> operacion){return tx.execute(s->operacion.get());}
    private Instant ahora(){
        // Epoch evita interpretar DATETIME sin zona con la zona local de la JVM.
        Object v=em.createNativeQuery(mysql?"SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))":"SELECT EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)").getSingleResult();
        long micros=new java.math.BigDecimal(v.toString()).movePointRight(6).longValueExact();
        return Instant.ofEpochSecond(Math.floorDiv(micros,1_000_000),Math.floorMod(micros,1_000_000)*1000);
    }
    private byte[] clave(MensajeCanal m,boolean evento){
        return evento?crypto.huella("evento-v1",m.cuenta(),m.canal(),m.id()):crypto.huella("conversacion-v1",m.cuenta(),m.canal(),m.usuario());
    }
    private String contexto(byte[] clave){return HexFormat.of().formatHex(clave);}
    public Recibo recibir(List<Entrada> entradas){
        if(entradas.size()>10)throw new IllegalArgumentException("Lote WhatsApp inválido");
        return recibirLote(entradas,List.of());
    }
    public Recibo recibirLote(List<Entrada> entradas,List<EstadoEntregaCanal> estados){
        if(entradas.size()+estados.size()>1000)throw new IllegalArgumentException("Lote WhatsApp inválido");
        // Orden estable de los agregados, incluidos los lotes de varias conversaciones.
        var ordenadas=entradas.stream().sorted(Comparator.comparing(e->contexto(clave(e.mensaje(),false)))).toList();
        var estadosOrdenados=estados.stream().sorted(Comparator.comparing(e->contexto(claveEstado(e)))).toList();
        for(int i=0;;i++)try{return atomico(()->{
            var mensajes=ingresar(ordenadas);var entrega=ingresarEstados(estadosOrdenados);
            return new Recibo(mensajes.aceptados()+entrega.aceptados(),mensajes.duplicados()+entrega.duplicados());
        });}
        catch(RuntimeException e){
            boolean recuperable=e instanceof DataIntegrityViolationException||e instanceof EntityExistsException;
            for(Throwable causa=e;causa!=null;causa=causa.getCause())if(causa instanceof java.sql.SQLException sql){
                String estado=sql.getSQLState();recuperable|=estado!=null&&(estado.startsWith("23")||estado.startsWith("40"));
            }
            if(i>=2||!recuperable)throw e;
        }
    }
    private byte[] claveEstado(EstadoEntregaCanal e){return crypto.huella("estado-v1",e.cuenta(),e.canal(),e.mensaje(),e.estado(),e.emitido().toString());}
    private Recibo ingresarEstados(List<EstadoEntregaCanal> estados){
        Instant now=ahora();int aceptados=0,duplicados=0;
        for(var notificacion:estados){
            byte[] clave=claveEstado(notificacion),huella=crypto.huella("contenido-estado-v1",notificacion.participante());
            var existentes=em.createQuery("from EventoWhatsAppEntity where claveHmac=:k",EventoWhatsAppEntity.class).setParameter("k",clave).getResultList();
            if(!existentes.isEmpty()){
                if(!MessageDigest.isEqual(existentes.getFirst().contenidoHmac,huella))throw new EventoConflictivo();
                duplicados++;continue;
            }
            var e=new EventoWhatsAppEntity();e.claveHmac=clave;e.contenidoHmac=huella;e.tipo="ESTADO";e.estado="IGNORADO";e.esquema=1;
            e.contenidoCifrado=crypto.proteger(notificacion,"evento:"+contexto(clave));e.emitidoEn=notificacion.emitido();
            e.recibidoEn=now;e.actualizadoEn=now;e.terminadoEn=now;e.venceEn=now.plus(Duration.ofHours(24));e.retenerHasta=now.plus(Duration.ofDays(30));
            e.intentos=0;e.proximoIntento=now;e.cercado=0L;e.resultadoCodigo="ESTADO_SIN_CORRELACION";em.persist(e);aceptados++;
        }
        em.flush();return new Recibo(aceptados,duplicados);
    }
    private Recibo ingresar(List<Entrada> entradas){
        Instant now=ahora();int aceptados=0,duplicados=0;
        for(var entrada:entradas){
            var m=entrada.mensaje();byte[] ck=clave(m,false),ek=clave(m,true);
            byte[] huella=crypto.huella("contenido-v1",m.cuenta(),m.canal(),m.usuario(),m.tipo().name(),m.contenido(),entrada.emitido().toString());
            var conversaciones=em.createQuery("from ConversacionWhatsAppEntity where claveHmac=:k",ConversacionWhatsAppEntity.class)
                .setParameter("k",ck).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            ConversacionWhatsAppEntity c;
            if(conversaciones.isEmpty()){
                c=new ConversacionWhatsAppEntity();c.claveHmac=ck;c.estado="LIBRE";c.revisionEstado=0L;c.secuenciaRecibida=0L;
                c.cercado=0L;c.ingresados=0;c.creadoEn=now;c.actualizadoEn=now;c.retenerHasta=now.plus(Duration.ofDays(30));em.persist(c);em.flush();
            }else c=conversaciones.getFirst();
            var existentes=em.createQuery("from EventoWhatsAppEntity where claveHmac=:k",EventoWhatsAppEntity.class).setParameter("k",ek).getResultList();
            if(!existentes.isEmpty()){
                if(!MessageDigest.isEqual(existentes.getFirst().contenidoHmac,huella))throw new EventoConflictivo();
                duplicados++;continue;
            }
            if(c.ventanaIngreso==null||!c.ventanaIngreso.plusSeconds(60).isAfter(now)){c.ventanaIngreso=now;c.ingresados=0;}
            if(c.ingresados>=100)throw new IllegalStateException("Ingreso WhatsApp temporalmente limitado");
            c.ingresados++;c.secuenciaRecibida++;c.actualizadoEn=now;c.retenerHasta=now.plus(Duration.ofDays(30));
            var e=new EventoWhatsAppEntity();e.claveHmac=ek;e.contenidoHmac=huella;e.conversacionId=c.id;e.secuencia=c.secuenciaRecibida;
            e.tipo=m.tipo()==MensajeCanal.Tipo.NO_COMPATIBLE?"DESCONOCIDO":m.tipo().name();e.estado="PENDIENTE";e.esquema=1;
            e.contenidoCifrado=crypto.proteger(m,"evento:"+contexto(ek));e.emitidoEn=entrada.emitido();e.recibidoEn=now;e.actualizadoEn=now;
            e.venceEn=now.plus(m.tipo()==MensajeCanal.Tipo.IMAGEN?Duration.ofMinutes(15):Duration.ofHours(24));
            e.retenerHasta=now.plus(Duration.ofDays(30));e.intentos=0;e.proximoIntento=now;e.cercado=0L;
            if(entrada.emitido().isAfter(now.plusSeconds(300)))throw new IllegalArgumentException("Fecha WhatsApp inválida");
            if(!entrada.emitido().plus(m.tipo()==MensajeCanal.Tipo.IMAGEN?Duration.ofMinutes(15):Duration.ofHours(24)).isAfter(now)){
                e.estado="EXPIRADO";e.terminadoEn=now;e.contenidoCifrado=null;
            }
            em.persist(e);em.flush();
            if(m.tipo()==MensajeCanal.Tipo.IMAGEN&&e.estado.equals("PENDIENTE")){
                if(c.ultimaImagenEmitida!=null&&entrada.emitido().isBefore(c.ultimaImagenEmitida)){
                    e.estado="SUPERADO";e.terminadoEn=now;e.contenidoCifrado=null;
                }else{
                    c.ultimaImagenEvento=e.id;c.ultimaImagenEmitida=entrada.emitido();
                    // Invalida referencias y respuestas anteriores sin invadir una reserva en curso.
                    c.revisionEstado++;c.estado="ESPERA_OCR";c.referencia=null;c.propuestaExpira=null;c.estadoCifrado=null;
                }
            }
            aceptados++;
        }
        em.flush();return new Recibo(aceptados,duplicados);
    }
    public Reserva reservar(boolean imagen){return atomico(()->{
        Instant now=ahora();
        var candidatos=em.createQuery("select e from EventoWhatsAppEntity e where e.estado in ('PENDIENTE','REINTENTO') and e.proximoIntento<=:now and "+
            (imagen?"e.tipo='IMAGEN'":"e.tipo in ('TEXTO','DESCONOCIDO')")+" and not exists (select anterior.id from EventoWhatsAppEntity anterior where anterior.conversacionId=e.conversacionId and anterior.secuencia<e.secuencia and anterior.estado in :activos) order by e.id",EventoWhatsAppEntity.class)
            .setParameter("now",now).setParameter("activos",ACTIVOS).setMaxResults(32).getResultList();
        for(var candidato:candidatos){
            var c=em.find(ConversacionWhatsAppEntity.class,candidato.conversacionId,LockModeType.PESSIMISTIC_WRITE);
            if(c.reservaHasta!=null&&c.reservaHasta.isAfter(now))continue;
            var e=em.find(EventoWhatsAppEntity.class,candidato.id,LockModeType.PESSIMISTIC_WRITE);em.refresh(e);
            if(!List.of("PENDIENTE","REINTENTO").contains(e.estado)||e.proximoIntento.isAfter(now))continue;
            if(!e.venceEn.isAfter(now)){terminar(e,"EXPIRADO",now);continue;}
            if(e.tipo.equals("IMAGEN")&&!e.id.equals(c.ultimaImagenEvento)){terminar(e,"SUPERADO",now);continue;}
            String token=UUID.randomUUID().toString();e.estado="EN_PROCESO";e.reservaToken=token;e.reservaHasta=now.plusSeconds(120);e.cercado++;e.intentos++;e.actualizadoEn=now;
            c.reservaToken=token;c.reservaHasta=e.reservaHasta;c.cercado++;c.actualizadoEn=now;
            try{
                var mensaje=crypto.leer(e.contenidoCifrado,MensajeCanal.class,"evento:"+contexto(e.claveHmac));
                var estado=c.estadoCifrado==null?null:crypto.leer(c.estadoCifrado,EstadoConversacion.class,"conversacion:"+contexto(c.claveHmac));
                return new Reserva(e.id,c.id,token,e.cercado,mensaje,estado,now);
            }catch(IllegalStateException fallo){
                // No revertir el contador: un registro corrupto no debe bloquear la cabecera indefinidamente.
                e.ultimoErrorCodigo="CONTENIDO_INVALIDO";e.reservaToken=null;e.reservaHasta=null;liberar(c);
                e.estado=e.intentos>=5?"ERROR_PERMANENTE":"REINTENTO";e.proximoIntento=now.plusSeconds(demora(e.intentos));
                if(e.intentos>=5)e.terminadoEn=now;
                // Se conserva cifrado hasta vence_en para permitir corregir una clave mal configurada.
                return null;
            }
        }return null;
    });}
    private EventoWhatsAppEntity vigente(Reserva r,Instant now){
        var e=em.find(EventoWhatsAppEntity.class,r.evento(),LockModeType.PESSIMISTIC_WRITE);
        return e!=null&&"EN_PROCESO".equals(e.estado)&&r.token().equals(e.reservaToken)&&r.cercado()==e.cercado&&e.reservaHasta.isAfter(now)?e:null;
    }
    private ConversacionWhatsAppEntity conversacion(Reserva r){return em.find(ConversacionWhatsAppEntity.class,r.conversacion(),LockModeType.PESSIMISTIC_WRITE);}
    public boolean renovar(Reserva r){return atomico(()->{
        Instant now=ahora();var c=conversacion(r);var e=vigente(r,now);
        if(e==null||!r.token().equals(c.reservaToken)||!r.ahora().plusSeconds(45).isAfter(now))return false;
        e.reservaHasta=now.plusSeconds(120);c.reservaHasta=e.reservaHasta;return true;
    });}
    public boolean finalizar(Reserva r,EstadoConversacion estado,RespuestaCanal respuesta){return atomico(()->{
        Instant now=ahora();var c=conversacion(r);var e=vigente(r,now);
        if(e==null||!r.token().equals(c.reservaToken)||!r.ahora().plusSeconds(45).isAfter(now))return false;
        // Una imagen posterior recibida durante OCR invalida el resultado del trabajador antiguo.
        boolean posterior=c.ultimaImagenEvento!=null&&c.ultimaImagenEvento>e.id;
        if(posterior&&(r.mensaje().tipo()==MensajeCanal.Tipo.IMAGEN||r.estado()!=null)){terminar(e,"SUPERADO",now);liberar(c);return false;}
        c.revisionEstado++;
        if(!posterior){
            c.estado=estado==null?"LIBRE":"ESPERA_CONFIRMACION";
            c.referencia=estado==null?null:estado.referencia();c.propuestaExpira=estado==null?null:Instant.ofEpochMilli(estado.venceEpochMilli());
            c.estadoCifrado=estado==null?null:crypto.proteger(estado,"conversacion:"+contexto(c.claveHmac));
        }
        c.actualizadoEn=now;liberar(c);
        var salida=new RespuestaWhatsAppEntity();salida.id=UUID.randomUUID().toString();salida.eventoId=e.id;salida.conversacionId=c.id;
        salida.estado="PENDIENTE";salida.revisionObjetivo=respuesta.estado()==RespuestaCanal.Estado.CONFIRMACION?c.revisionEstado:null;salida.planCifrado=crypto.proteger(respuesta,"respuesta:"+salida.id);
        salida.intentosPreparacion=0;salida.intentosEnvio=0;salida.cercado=0L;salida.proximoIntento=now;
        salida.creadoEn=now;salida.actualizadoEn=now;salida.venceEn=estado==null?now.plus(Duration.ofHours(24)):c.propuestaExpira;
        // Una propuesta caducada durante el procesamiento no se publica.
        if(salida.venceEn.isBefore(now)){salida.venceEn=now;salida.estado="EXPIRADA";salida.planCifrado=null;}
        salida.retenerHasta=now.plus(Duration.ofDays(30));em.persist(salida);
        e.resultadoCodigo=respuesta.estado().name();terminar(e,"TERMINADO",now);return true;
    });}
    private void liberar(ConversacionWhatsAppEntity c){c.reservaToken=null;c.reservaHasta=null;}
    private void terminar(EventoWhatsAppEntity e,String estado,Instant now){e.estado=estado;e.reservaToken=null;e.reservaHasta=null;e.terminadoEn=now;e.actualizadoEn=now;e.contenidoCifrado=null;}
    private long demora(int intento){return Math.min(300,5L*(long)Math.pow(3,Math.min(intento-1,4)))+java.util.concurrent.ThreadLocalRandom.current().nextLong(4);}
    private long esperaMinima(Duration espera){
        if(espera==null||espera.isNegative()||espera.compareTo(Duration.ofDays(1))>0)throw new IllegalArgumentException("Espera de catálogo inválida");
        return espera.getSeconds();
    }
    public void reintentar(Reserva r,String codigo,int maximo){reintentar(r,codigo,maximo,Duration.ZERO);}
    @Override public void reintentar(Reserva r,String codigo,int maximo,Duration espera){
        long minima=esperaMinima(espera);atomico(()->{
        Instant now=ahora();var c=conversacion(r);var e=vigente(r,now);if(e==null||!r.token().equals(c.reservaToken))return null;
        e.ultimoErrorCodigo=Set.of("ERROR_BUSCADOR","ERROR_OCR","TIEMPO_AGOTADO").contains(codigo)?codigo:"PROCESAMIENTO";
        if(e.intentos>=maximo||!e.venceEn.isAfter(now))terminar(e,e.intentos>=maximo?"ERROR_PERMANENTE":"EXPIRADO",now);
        else{e.estado="REINTENTO";e.proximoIntento=now.plusSeconds(Math.max(demora(e.intentos),minima));e.reservaToken=null;e.reservaHasta=null;e.actualizadoEn=now;}
        liberar(c);return null;
    });}
    public Salida reservarSalida(){return atomico(()->{
        Instant now=ahora();
        var lista=em.createQuery("from RespuestaWhatsAppEntity s where s.estado in ('PENDIENTE','REINTENTO') and s.proximoIntento<=:now "+
            "and not exists (select anterior.id from RespuestaWhatsAppEntity anterior where anterior.conversacionId=s.conversacionId and anterior.eventoId<s.eventoId and anterior.estado in ('PENDIENTE','PREPARANDO','PREPARADA','ENVIANDO','REINTENTO','INCIERTA')) order by s.eventoId",RespuestaWhatsAppEntity.class)
            .setParameter("now",now).setMaxResults(32).getResultList();
        for(var candidata:lista){
            var c=em.find(ConversacionWhatsAppEntity.class,candidata.conversacionId,LockModeType.PESSIMISTIC_WRITE);
            var s=em.find(RespuestaWhatsAppEntity.class,candidata.id,LockModeType.PESSIMISTIC_WRITE);em.refresh(s);
            if(!List.of("PENDIENTE","REINTENTO").contains(s.estado)||s.proximoIntento.isAfter(now))continue;
            if(!s.venceEn.isAfter(now)||s.revisionObjetivo!=null&&!s.revisionObjetivo.equals(c.revisionEstado)){
                cerrarSalida(s,!s.venceEn.isAfter(now)?"EXPIRADA":"CANCELADA",now);continue;
            }
            s.estado="PREPARANDO";s.faseReintento=null;s.reservaToken=UUID.randomUUID().toString();s.reservaHasta=now.plusSeconds(120);s.cercado++;s.intentosPreparacion++;s.actualizadoEn=now;
            try{return new Salida(s.id,s.reservaToken,s.cercado,crypto.leer(s.planCifrado,RespuestaCanal.class,"respuesta:"+s.id));}
            catch(IllegalStateException fallo){
                s.ultimoErrorCodigo="CONTENIDO_INVALIDO";
                if(s.intentosPreparacion>=5)cerrarSalida(s,"ERROR_PERMANENTE",now);
                else{s.estado="REINTENTO";s.faseReintento="PREPARACION";s.reservaToken=null;s.reservaHasta=null;s.proximoIntento=now.plusSeconds(demora(s.intentosPreparacion));}
                return null;
            }
        }return null;
    });}
    private boolean salidaVigente(RespuestaWhatsAppEntity s,Salida r,Instant now){return s!=null&&s.estado.equals("PREPARANDO")&&r.token().equals(s.reservaToken)&&r.cercado()==s.cercado&&s.reservaHasta.isAfter(now);}
    public boolean simular(Salida r,RespuestaCanal publica){return atomico(()->{
        Instant now=ahora();var inicial=em.find(RespuestaWhatsAppEntity.class,r.id());if(inicial==null)return false;
        var c=em.find(ConversacionWhatsAppEntity.class,inicial.conversacionId,LockModeType.PESSIMISTIC_WRITE);
        var s=em.find(RespuestaWhatsAppEntity.class,r.id(),LockModeType.PESSIMISTIC_WRITE);em.refresh(s);
        if(!salidaVigente(s,r,now))return false;
        if(!s.venceEn.isAfter(now)||s.revisionObjetivo!=null&&!s.revisionObjetivo.equals(c.revisionEstado)){cerrarSalida(s,"CANCELADA",now);return false;}
        s.preparadoCifrado=crypto.proteger(publica,"preparado:"+s.id);cerrarSalida(s,"SIMULADA",now);
        // El contenido se elimina: la evidencia durable conserva solamente estado y códigos.
        s.preparadoCifrado=null;return true;
    });}
    public boolean aceptar(Salida r,RespuestaCanal publica,String idProveedor){
        if(idProveedor==null||!idProveedor.matches("[A-Za-z0-9_.:+=/-]{1,512}"))throw new IllegalArgumentException("Identificador del proveedor inválido");
        return atomico(()->{
            Instant now=ahora();var inicial=em.find(RespuestaWhatsAppEntity.class,r.id());if(inicial==null)return false;
            em.find(ConversacionWhatsAppEntity.class,inicial.conversacionId,LockModeType.PESSIMISTIC_WRITE);
            var s=em.find(RespuestaWhatsAppEntity.class,r.id(),LockModeType.PESSIMISTIC_WRITE);em.refresh(s);
            // Ya despachado: aunque la reserva haya vencido se registra la aceptación para no reenviar.
            if(s==null||!r.token().equals(s.reservaToken)||r.cercado()!=s.cercado)return false;
            s.intentosEnvio=s.intentosEnvio+1;s.proveedorClaveHmac=crypto.huella("proveedor-v1",idProveedor);
            cerrarSalida(s,"ACEPTADA",now);s.estadoEntrega="ENVIADA";return true;
        });
    }
    public void reintentarEnvio(Salida r,boolean reintentable){atomico(()->{
        Instant now=ahora();var s=em.find(RespuestaWhatsAppEntity.class,r.id(),LockModeType.PESSIMISTIC_WRITE);
        if(!salidaVigente(s,r,now))return null;s.ultimoErrorCodigo="ENVIO";s.intentosEnvio=s.intentosEnvio+1;
        if(!reintentable||s.intentosEnvio>=5||!s.venceEn.isAfter(now))cerrarSalida(s,"ERROR_PERMANENTE",now);
        else{s.estado="REINTENTO";s.faseReintento="ENVIO";s.reservaToken=null;s.reservaHasta=null;s.proximoIntento=now.plusSeconds(demora(s.intentosEnvio));}
        return null;
    });}
    private void cerrarSalida(RespuestaWhatsAppEntity s,String estado,Instant now){s.estado=estado;s.reservaToken=null;s.reservaHasta=null;s.faseReintento=null;s.actualizadoEn=now;s.planCifrado=null;s.preparadoCifrado=null;}
    public void reintentarSalida(Salida r){reintentarSalida(r,5,Duration.ZERO);}
    @Override public void reintentarSalida(Salida r,int maximo,Duration espera){
        long minima=esperaMinima(espera);atomico(()->{
        Instant now=ahora();var s=em.find(RespuestaWhatsAppEntity.class,r.id(),LockModeType.PESSIMISTIC_WRITE);
        if(!salidaVigente(s,r,now))return null;s.ultimoErrorCodigo="PREPARACION";
        if(s.intentosPreparacion>=maximo||!s.venceEn.isAfter(now))cerrarSalida(s,"ERROR_PERMANENTE",now);
        else{s.estado="REINTENTO";s.faseReintento="PREPARACION";s.reservaToken=null;s.reservaHasta=null;s.proximoIntento=now.plusSeconds(Math.max(demora(s.intentosPreparacion),minima));}
        return null;
    });}
    public void recuperar(){atomico(()->{
        Instant now=ahora();
        // Siempre conversación antes de evento/salida: mismo orden que el resto de operaciones.
        var convs=em.createQuery("from ConversacionWhatsAppEntity where reservaHasta<=:now or propuestaExpira<=:now",ConversacionWhatsAppEntity.class)
            .setParameter("now",now).setMaxResults(100).getResultList();
        for(var candidata:convs){
            var c=em.find(ConversacionWhatsAppEntity.class,candidata.id,LockModeType.PESSIMISTIC_WRITE);em.refresh(c);
            if(c.reservaHasta!=null&&!c.reservaHasta.isAfter(now)){
                var eventos=em.createQuery("from EventoWhatsAppEntity where conversacionId=:c and estado='EN_PROCESO'",EventoWhatsAppEntity.class).setParameter("c",c.id).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
                for(var e:eventos)if(!e.reservaHasta.isAfter(now)){
                    if(e.intentos>=5||!e.venceEn.isAfter(now))terminar(e,e.intentos>=5?"ERROR_PERMANENTE":"EXPIRADO",now);
                    else{e.estado="REINTENTO";e.reservaToken=null;e.reservaHasta=null;e.proximoIntento=now.plusSeconds(demora(e.intentos));e.ultimoErrorCodigo="RESERVA_VENCIDA";}
                }liberar(c);
            }
            if(c.propuestaExpira!=null&&!c.propuestaExpira.isAfter(now)&&c.reservaToken==null){
                c.estado="EXPIRADA";c.referencia=null;c.propuestaExpira=null;c.estadoCifrado=null;c.revisionEstado++;c.actualizadoEn=now;
            }
        }
        var salidas=em.createQuery("from RespuestaWhatsAppEntity where estado in ('PREPARANDO','ENVIANDO') and reservaHasta<=:now",RespuestaWhatsAppEntity.class).setParameter("now",now).setMaxResults(100).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for(var s:salidas){
            if(s.estado.equals("ENVIANDO")){cerrarSalida(s,"INCIERTA",now);continue;}
            if(s.intentosPreparacion>=5||!s.venceEn.isAfter(now)){cerrarSalida(s,"ERROR_PERMANENTE",now);continue;}
            s.estado="REINTENTO";s.faseReintento="PREPARACION";s.reservaToken=null;s.reservaHasta=null;s.proximoIntento=now.plusSeconds(demora(s.intentosPreparacion));
        }
        // Borrado acotado de datos sensibles; tombstones de deduplicación duran 30 días.
        var vencidos=em.createQuery("from EventoWhatsAppEntity where venceEn<=:now and contenidoCifrado is not null and estado<>'EN_PROCESO'",EventoWhatsAppEntity.class).setParameter("now",now).setMaxResults(100).getResultList();
        for(var candidato:vencidos){
            if(candidato.conversacionId!=null)em.find(ConversacionWhatsAppEntity.class,candidato.conversacionId,LockModeType.PESSIMISTIC_WRITE);
            var e=em.find(EventoWhatsAppEntity.class,candidato.id,LockModeType.PESSIMISTIC_WRITE);em.refresh(e);
            if(e.estado.equals("EN_PROCESO"))continue;
            if(ACTIVOS.contains(e.estado))terminar(e,"EXPIRADO",now);else e.contenidoCifrado=null;
        }
        var vencidas=em.createQuery("from RespuestaWhatsAppEntity where venceEn<=:now and estado in ('PENDIENTE','REINTENTO','PREPARADA')",RespuestaWhatsAppEntity.class).setParameter("now",now).setMaxResults(100).getResultList();
        for(var candidata:vencidas){
            em.find(ConversacionWhatsAppEntity.class,candidata.conversacionId,LockModeType.PESSIMISTIC_WRITE);
            var s=em.find(RespuestaWhatsAppEntity.class,candidata.id,LockModeType.PESSIMISTIC_WRITE);em.refresh(s);
            if(List.of("PENDIENTE","REINTENTO","PREPARADA").contains(s.estado))cerrarSalida(s,"EXPIRADA",now);
        }
        purgar(now);
        return null;
    });}
    private void purgar(Instant now){
        var antiguos=em.createQuery("from EventoWhatsAppEntity e where e.retenerHasta<=:now and e.estado not in :activos order by e.id",EventoWhatsAppEntity.class)
            .setParameter("now",now).setParameter("activos",ACTIVOS).setMaxResults(100).getResultList();
        for(var candidato:antiguos){
            var c=candidato.conversacionId==null?null:em.find(ConversacionWhatsAppEntity.class,candidato.conversacionId,LockModeType.PESSIMISTIC_WRITE);
            var e=em.find(EventoWhatsAppEntity.class,candidato.id,LockModeType.PESSIMISTIC_WRITE);em.refresh(e);
            var salidas=em.createQuery("from RespuestaWhatsAppEntity where eventoId=:id",RespuestaWhatsAppEntity.class).setParameter("id",e.id).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            if(salidas.stream().anyMatch(s->s.retenerHasta.isAfter(now)||List.of("PENDIENTE","PREPARANDO","PREPARADA","ENVIANDO","REINTENTO").contains(s.estado)))continue;
            salidas.forEach(em::remove);em.flush();
            if(c!=null&&Objects.equals(c.ultimaImagenEvento,e.id)){c.ultimaImagenEvento=null;c.ultimaImagenEmitida=null;}
            em.remove(e);em.flush();
        }
        var vacias=em.createQuery("from ConversacionWhatsAppEntity c where c.retenerHasta<=:now and c.reservaToken is null and c.estadoCifrado is null and not exists (select e.id from EventoWhatsAppEntity e where e.conversacionId=c.id)",ConversacionWhatsAppEntity.class)
            .setParameter("now",now).setMaxResults(100).getResultList();
        for(var candidata:vacias){
            var c=em.find(ConversacionWhatsAppEntity.class,candidata.id,LockModeType.PESSIMISTIC_WRITE);em.refresh(c);
            if(c.retenerHasta.isAfter(now)||c.reservaToken!=null||c.estadoCifrado!=null)continue;
            long eventos=em.createQuery("select count(e) from EventoWhatsAppEntity e where e.conversacionId=:id",Long.class).setParameter("id",c.id).getSingleResult();
            if(eventos==0)em.remove(c);
        }
    }
}
