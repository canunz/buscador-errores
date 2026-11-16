package cl.casol.backend.ejecucion.application.service;

import cl.casol.backend.ejecucion.application.port.out.*;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.shared.domain.DestinatarioPaso;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static cl.casol.backend.ejecucion.domain.SolicitudEvidencia.Estado.*;

@Service @Transactional(readOnly=true, timeout=8)
public class AutomatizarEvidenciaService implements AutomatizacionEjecucionPort {
    private final SolicitudEvidenciaRepository solicitudes;
    private final EjecucionRepository ejecuciones;
    private final EjecucionPasoRepository pasos;
    private final SucursalRepository sucursales;
    private final UsuarioRepository usuarios;
    private final CorreoEvidenciaPort correo;
    private final FotosEvidenciaPort fotos;
    private final Clock reloj;
    private final org.springframework.context.ApplicationEventPublisher eventosCorreo;
    @Value("${dimabug.correo.maximo-intentos-hora:20}") private int maximoIntentosHora=20;
    private final SolicitudEvidencia.Politica politica;
    public AutomatizarEvidenciaService(SolicitudEvidenciaRepository solicitudes,EjecucionRepository ejecuciones,
            EjecucionPasoRepository pasos,SucursalRepository sucursales,UsuarioRepository usuarios,
            CorreoEvidenciaPort correo,FotosEvidenciaPort fotos,Clock reloj,org.springframework.context.ApplicationEventPublisher eventosCorreo,
            @Value("${dimabug.automatizacion.recordatorio-1-horas:24}") int hora1,
            @Value("${dimabug.automatizacion.recordatorio-2-horas:72}") int hora2,
            @Value("${dimabug.automatizacion.escalamiento-horas:120}") int hora3) {
        this.solicitudes=solicitudes;this.ejecuciones=ejecuciones;this.pasos=pasos;this.sucursales=sucursales;
        this.usuarios=usuarios;this.correo=correo;this.fotos=fotos;this.reloj=reloj;this.eventosCorreo=eventosCorreo;
        politica=new SolicitudEvidencia.Politica("Enviar fotografías del mueble, punto de red y electricidad para revisión del técnico.",hora1,hora2,hora3);
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.SUPPORTS,readOnly=true)
    public boolean disponible(){return correo.disponible();}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.SUPPORTS,readOnly=true)
    public boolean simulado(){return correo.simulado();}
    public List<SolicitudEvidencia> avisos(String email){return solicitudes.avisos(actor(email).getId()).stream().filter(s->!Set.of(ESPERANDO_EVIDENCIA,ESPERANDO_CONFIRMACION).contains(s.estado()) || s.reglaAplicada()>=s.politica().configuracion().plazoRespuestaHoras()).toList();}
    public void validarInicio(Integer sucursalId) {
        if(!correo.disponible())throw regla("Gmail no está conectado. Habilita la simulación solo en desarrollo para probar este procedimiento");
        sucursal(sucursalId);
    }
    private Sucursal sucursal(Integer id) {
        if(id==null)throw regla("Selecciona una sucursal activa");
        var s=sucursales.buscar(id).filter(Sucursal::activa).orElseThrow(()->regla("Selecciona una sucursal activa"));
        if(s.responsableNombre()==null || s.responsableNombre().isBlank() || s.responsableEmail()==null ||
                !s.responsableEmail().matches("(?i)[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@dimarsa\\.cl"))
            throw regla("Un administrador debe registrar el responsable y un correo @dimarsa.cl válido en el catálogo de sucursales");
        return s;
    }
    public Optional<Integer> buscarInicio(Integer actor,String clave,Integer procedimiento,Integer sucursal) {
        if(clave==null)throw regla("El inicio automatizado requiere una clave UUID");
        try{UUID.fromString(clave);}catch(IllegalArgumentException e){throw regla("Clave de inicio inválida");}
        return solicitudes.porInicio(actor,clave).map(s->{
            if(!s.contexto().procedimientoId().equals(procedimiento) || !Objects.equals(s.contexto().sucursalId(),sucursal))
                throw conflicto("La clave de inicio ya se usó con otro contexto");
            return s.ejecucionId();
        });
    }
    public Optional<Integer> buscarInicio(Integer actor,String clave,Integer procedimiento,Integer sucursal,List<DestinatarioPaso> adicionales) {
        var resultado=buscarInicio(actor,clave,procedimiento,sucursal);
        resultado.ifPresent(id->{
            String huella=sha(DestinatarioPaso.normalizar(adicionales).toString().getBytes(StandardCharsets.UTF_8));
            var registrado=solicitudes.eventos(id).stream().filter(v->v.tipo().equals("CONTEXTO_INICIO")).findFirst();
            if(registrado.isPresent() && !registrado.get().detalle().equals(huella))throw conflicto("La clave de inicio ya se usó con otros destinatarios");
            if(registrado.isEmpty() && !adicionales.isEmpty())throw conflicto("El inicio anterior no tenía destinatarios adicionales");
        });return resultado;
    }
    @Transactional public void iniciar(Ejecucion e,List<EjecucionPaso> instancias,List<Paso> definiciones,Integer sucursal,String clave){iniciar(e,instancias,definiciones,sucursal,clave,List.of());}
    @Transactional public void iniciar(Ejecucion e,List<EjecucionPaso> instancias,List<Paso> definiciones,Integer sucursalId,String clave,List<DestinatarioPaso> adicionales) {
        var sucursal=sucursal(sucursalId);boolean primera=true;
        var permitidos=new HashSet<String>();sucursal.contactos().forEach(d->permitidos.add(d.correo()));
        definiciones.forEach(p->p.configuracion().destinatarios().forEach(d->permitidos.add(d.correo())));
        boolean admin="ADMINISTRADOR".equalsIgnoreCase(e.usuario().getRol().getNombre().strip());
        var extras=DestinatarioPaso.normalizar(adicionales);
        if(!admin && extras.stream().anyMatch(d->!permitidos.contains(d.correo())))throw new AccessDeniedException("Un administrador debe autorizar los destinatarios adicionales");
        for(var p:instancias) {
            if(!p.contrato().modo().tieneAccion())continue;
            var cfg=p.contrato().configuracion();var contactos=new ArrayList<DestinatarioPaso>(cfg.destinatarios());
            if(cfg.usarDestinatariosSucursal()){contactos.addAll(sucursal.contactos());contactos.addAll(extras);}
            var destinatarios=DestinatarioPaso.normalizar(contactos);
            if(p.contrato().modo()!=ModoEjecucionPaso.AUTOMATICO && destinatarios.stream().noneMatch(d->d.tipo()==DestinatarioPaso.Tipo.PRINCIPAL))throw regla("Configura al menos un destinatario principal para el paso "+p.contrato().orden());
            if(destinatarios.isEmpty())destinatarios=sucursal.contactos();
            String principal=destinatarios.stream().filter(d->d.tipo()==DestinatarioPaso.Tipo.PRINCIPAL).findFirst().orElseThrow().correo();
            var contexto=new SolicitudEvidencia.Contexto(sucursal.id(),sucursal.codigo(),sucursal.nombre(),sucursal.responsableNombre(),principal,e.usuario().getId(),e.procedimientoId(),primera?clave:null,destinatarios);
            String mensaje=cfg.mensaje()==null || cfg.mensaje().isBlank()?p.contrato().instruccion():cfg.mensaje();
            var configuracion=new SolicitudEvidencia.Politica(mensaje,politica.primerRecordatorioHoras(),politica.segundoRecordatorioHoras(),politica.escalamientoHoras(),cfg);
            var solicitud=solicitudes.guardar(new SolicitudEvidencia(UUID.randomUUID().toString(),e.id(),p.id(),1,contexto,configuracion,PENDIENTE,null,null,0,null,null,null,null));
            evento(solicitud,"SOLICITUD_CREADA",e.usuario().getId(),"Acción "+p.contrato().modo()+" capturada; modo SIMULADO");
            if(primera)evento(solicitud,"CONTEXTO_INICIO",e.usuario().getId(),sha(extras.toString().getBytes(StandardCharsets.UTF_8)));
            primera=false;
        }
        avanzar(e);
    }
    @Transactional public SolicitudEvidencia enviarPaso(Integer id,Integer pasoId,Integer sucursalId,List<DestinatarioPaso> destinatarios,String asunto,String mensaje,String email){
        ejecuciones.bloquear(id);var e=autorizar(id,email);enCurso(e);var usuario=actor(email);
        var p=pasos.buscarPorId(pasoId).filter(v->v.ejecucionId().equals(id)).orElseThrow(()->regla("El paso no pertenece a esta ejecución"));
        if(p.contrato()==null || !(p.contrato().modo().esEvidencia() || p.contrato().modo()==ModoEjecucionPaso.ENVIAR_CORREO))throw regla("Este paso no tiene una acción de correo");
        var cfg=p.contrato().configuracion();boolean evidencia=p.contrato().modo().esEvidencia();
        var sucursal=evidencia && sucursalId!=null?sucursal(sucursalId):null;
        boolean directo=sucursal==null;
        // ENVIAR_CORREO no depende del directorio, aunque un cliente legado envíe sucursalId.
        if(!evidencia)sucursalId=null;
        var permitidos=new ArrayList<DestinatarioPaso>(cfg.destinatarios());if(sucursal!=null)permitidos.addAll(sucursal.contactos());
        var elegidos=DestinatarioPaso.normalizar(destinatarios);
        if(elegidos.isEmpty() || elegidos.stream().noneMatch(d->d.tipo()==DestinatarioPaso.Tipo.PRINCIPAL))throw regla("Selecciona al menos un destinatario principal");
        boolean admin="ADMINISTRADOR".equalsIgnoreCase(usuario.getRol().getNombre().strip());
        if(evidencia && !directo && !admin && elegidos.stream().anyMatch(d->permitidos.stream().noneMatch(a->a.correo().equals(d.correo()))))throw new AccessDeniedException("Un administrador debe autorizar los correos adicionales");
        String baseAsunto=(cfg.asunto()==null?"Solicitud del procedimiento":cfg.asunto())+(sucursal==null?"":" — "+sucursal.nombre());
        if(baseAsunto.length()>200)baseAsunto=baseAsunto.substring(0,200);
        String baseMensaje=cfg.mensaje()==null || cfg.mensaje().isBlank()?p.contrato().instruccion():cfg.mensaje();
        String titulo=asunto==null?baseAsunto:asunto.strip(),cuerpo=mensaje==null?baseMensaje:mensaje.strip();
        if(evidencia && !directo && !admin && (!titulo.equals(baseAsunto) || !cuerpo.equals(baseMensaje)))throw new AccessDeniedException("Solo un administrador puede editar el contenido del mensaje");
        if(titulo.isBlank() || titulo.length()>200 || titulo.contains("\r") || titulo.contains("\n"))throw regla("Asunto obligatorio; máximo 200 caracteres y sin saltos de línea");
        if(cuerpo.isBlank() || cuerpo.length()>2000)throw regla("Mensaje obligatorio; máximo 2000 caracteres");
        var capturada=new ConfiguracionPaso(elegidos,!directo && cfg.usarDestinatariosSucursal(),cuerpo,cfg.evidenciasRequeridas(),cfg.requiereAprobacion(),cfg.recordatoriosHoras(),cfg.plazoRespuestaHoras(),cfg.condicionFinalizacion(),cfg.accionAutomatica(),titulo);
        String huella=sha((sucursalId+"|"+elegidos.stream().sorted(Comparator.comparing(DestinatarioPaso::correo)).toList()+"|"+titulo+"|"+cuerpo).getBytes(StandardCharsets.UTF_8));
        var existente=solicitudes.porEjecucion(id).stream().filter(v->v.ejecucionPasoId().equals(pasoId)).max(Comparator.comparingInt(SolicitudEvidencia::ronda));
        if(existente.isPresent()){
            var anterior=existente.get();var firma=solicitudes.eventos(id).stream().filter(v->Objects.equals(v.solicitudId(),anterior.id()) && v.tipo().equals("CONFIGURACION_CORREO")).findFirst();
            if(firma.isPresent() && firma.get().detalle().equals(huella)){if(anterior.estado()==PENDIENTE)avanzar(e);return solicitudes.buscar(anterior.id()).orElseThrow();}
            throw conflicto("Este paso ya tiene una comunicación. Actualiza su historial antes de continuar");
        }
        if(p.cumplido() || pasos.buscarPorEjecucion(id).stream().anyMatch(v->!v.cumplido() && v.contrato()!=null && v.contrato().orden()<p.contrato().orden()))throw conflicto("Primero completa los pasos anteriores");
        if(!directo)validarInicio(sucursalId);else if(!correo.disponible())throw regla("El servicio de correo no está configurado o disponible");
        var principal=elegidos.stream().filter(d->d.tipo()==DestinatarioPaso.Tipo.PRINCIPAL).findFirst().orElseThrow();
        var contexto=new SolicitudEvidencia.Contexto(sucursal==null?null:sucursal.id(),sucursal==null?null:sucursal.codigo(),sucursal==null?null:sucursal.nombre(),sucursal==null?null:sucursal.responsableNombre(),principal.correo(),usuario.getId(),e.procedimientoId(),null,elegidos);
        var politicaCapturada=new SolicitudEvidencia.Politica(cuerpo,politica.primerRecordatorioHoras(),politica.segundoRecordatorioHoras(),politica.escalamientoHoras(),capturada);
        String clave=UUID.nameUUIDFromBytes(("correo-paso:"+id+":"+pasoId+":1").getBytes(StandardCharsets.UTF_8)).toString();
        var nueva=solicitudes.guardar(new SolicitudEvidencia(clave,id,pasoId,1,contexto,politicaCapturada,PENDIENTE,null,null,0,null,null,null,null));
        evento(nueva,"CONFIGURACION_CORREO",usuario.getId(),huella);
        evento(nueva,"SOLICITUD_CREADA",usuario.getId(),"Solicitud preparada desde el paso; envío aún pendiente");
        avanzar(e);
        return solicitudes.buscar(nueva.id()).orElseThrow();
    }
    @Transactional public void avanzar(Ejecucion e) {
        if(e.estado()!=EstadoEjecucion.EN_CURSO)return;
        int limite=pasos.buscarPorEjecucion(e.id()).size()+1;
        while(limite-->0){
            var siguiente=pasos.buscarPorEjecucion(e.id()).stream().filter(p->!p.cumplido()).min(Comparator.comparing(p->p.contrato()==null?Integer.MAX_VALUE:p.contrato().orden()));
            if(siguiente.isEmpty())return;var p=siguiente.get();if(p.contrato()==null)return;
            var modo=p.contrato().modo();var cfg=p.contrato().configuracion();
            if(modo.esManual() || (modo==ModoEjecucionPaso.INFORMATIVO && cfg.condicionFinalizacion()!=ConfiguracionPaso.Condicion.AL_ACTIVAR))return;
            if(modo==ModoEjecucionPaso.INFORMATIVO){cambiarPaso(p,EstadoEjecucionPaso.COMPLETADO);solicitudes.registrar(new EventoAutomatizacion(null,e.id(),null,"INFORMACION_PRESENTADA",null,"Paso "+p.contrato().orden()+" informativo, sin acreditar trabajo físico",reloj.instant()));continue;}
            var actual=solicitudes.porEjecucion(e.id()).stream().filter(s->s.ejecucionPasoId().equals(p.id())).max(Comparator.comparingInt(SolicitudEvidencia::ronda)).orElse(null);
            if(actual==null && modo==ModoEjecucionPaso.AUTOMATICO){
                var contextoPrevio=solicitudes.porEjecucion(e.id()).stream().filter(s->s.fechaEnvio()!=null && s.contexto().sucursalId()!=null).max(Comparator.comparingInt((SolicitudEvidencia s)->paso(s).contrato().orden()).thenComparingInt(SolicitudEvidencia::ronda));
                if(contextoPrevio.isEmpty())return;
                var c=contextoPrevio.get().contexto();var contexto=new SolicitudEvidencia.Contexto(c.sucursalId(),c.sucursalCodigo(),c.sucursalNombre(),c.responsableNombre(),c.responsableEmail(),e.usuario().getId(),e.procedimientoId(),null,c.destinatarios());
                var politicaCapturada=new SolicitudEvidencia.Politica(p.contrato().instruccion(),politica.primerRecordatorioHoras(),politica.segundoRecordatorioHoras(),politica.escalamientoHoras(),cfg);
                actual=solicitudes.guardar(new SolicitudEvidencia(UUID.randomUUID().toString(),e.id(),p.id(),1,contexto,politicaCapturada,PENDIENTE,null,null,0,null,null,null,null));
            }
            if(actual==null)return;
            if(actual.estado()!=PENDIENTE)return;
            if(modo==ModoEjecucionPaso.AUTOMATICO){
                sucursal(actual.contexto().sucursalId());
                evento(actual,"CONTEXTO_VERIFICADO",null,"Sucursal activa y contexto autorizado verificados por handler permitido");
                if(cfg.requiereAprobacion()){solicitudes.guardar(actual.estado(ACCION_PENDIENTE_APROBACION));cambiarPaso(p,EstadoEjecucionPaso.EN_VALIDACION);return;}
                solicitudes.guardar(actual.estado(APROBADA));cambiarPaso(p,EstadoEjecucionPaso.COMPLETADO);continue;
            }
            if(!correo.simulado()){eventosCorreo.publishEvent(new CorreoPendiente(actual.id(),0));return;}
            String mensajeId=correo.enviar(actual,0);
            var estado=modo.esEvidencia()?ESPERANDO_EVIDENCIA:cfg.condicionFinalizacion()==ConfiguracionPaso.Condicion.RESPUESTA_VALIDADA?ESPERANDO_CONFIRMACION:cfg.requiereAprobacion()?ENVIADA_PENDIENTE_APROBACION:ENVIADA;
            var enviada=solicitudes.guardar(actual.enviada(reloj.instant(),mensajeId,estado));
            evento(enviada,"SOLICITUD_ENVIADA_SIMULADA",null,"Envío lógico a destinatarios PRINCIPAL/COPIA registrados; no correo real ni carga de folios acreditada");
            if(estado==ENVIADA){solicitudes.guardar(enviada.estado(ENVIADA));cambiarPaso(p,EstadoEjecucionPaso.COMPLETADO);continue;}
            cambiarPaso(p,modo.esEvidencia()?EstadoEjecucionPaso.ESPERANDO_EVIDENCIA:estado==ESPERANDO_CONFIRMACION?EstadoEjecucionPaso.ESPERANDO_CONFIRMACION:EstadoEjecucionPaso.EN_VALIDACION);return;
        }
    }
    /** Reserva durable antes del efecto externo. No se reenvía ENVIANDO/ERROR automáticamente. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Optional<SolicitudEvidencia> reservarEnvioSmtp(String id,int reglaHoras){
        var inicial=solicitudes.buscar(id).orElseThrow();ejecuciones.bloquear(inicial.ejecucionId());
        var e=ejecuciones.buscarPorId(inicial.ejecucionId()).orElseThrow();
        var s=solicitudes.buscar(id).orElseThrow();
        if(!correo.simulado() && esEnvioSimulado(s))return Optional.empty();
        if(e.estado()!=EstadoEjecucion.EN_CURSO)return Optional.empty();
        if(reglaHoras==0 && s.estado()!=PENDIENTE)return Optional.empty();
        if(!solicitudes.cuotaCorreoDisponible(s.contexto().actorId(),reloj.instant().minusSeconds(3600),Math.max(1,maximoIntentosHora))){
            if(reglaHoras==0)solicitudes.guardar(s.estado(ERROR));
            evento(s,nombreCorreo("CUOTA_AGOTADA"),s.contexto().actorId(),"Se alcanzó el máximo de intentos de correo por hora. No se envió este mensaje");return Optional.empty();
        }
        if(reglaHoras==0){
            if(s.estado()!=PENDIENTE)return Optional.empty();
            s=solicitudes.guardar(s.estado(ENVIANDO));
        }else{
            if(!Set.of(ESPERANDO_EVIDENCIA,ESPERANDO_CONFIRMACION).contains(s.estado()) || s.reglaAplicada()!=reglaHoras)return Optional.empty();
            if(solicitudes.eventos(s.ejecucionId()).stream().anyMatch(v->Objects.equals(v.solicitudId(),id) && Set.of("RECORDATORIO_SMTP_INICIADO","RECORDATORIO_GMAIL_INICIADO").contains(v.tipo()) && v.detalle().equals("Regla "+reglaHoras)))return Optional.empty();
        }
        evento(s,reglaHoras==0?nombreCorreo("INICIADO"):"RECORDATORIO_"+nombreCorreo("INICIADO"),s.contexto().actorId(),"Regla "+reglaHoras);
        return Optional.of(s);
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void registrarResultadoSmtp(String id,int reglaHoras,String referencia){
        var inicial=solicitudes.buscar(id).orElseThrow();ejecuciones.bloquear(inicial.ejecucionId());
        var e=ejecuciones.buscarPorId(inicial.ejecucionId()).orElseThrow();var s=solicitudes.buscar(id).orElseThrow();
        if(reglaHoras>0){evento(s,"RECORDATORIO_"+nombreCorreo(referencia==null?"NO_CONFIRMADO":"ACEPTADO"),null,referencia==null?"Envío no confirmado; no se reintenta esta regla automáticamente":"Servicio de correo aceptó el recordatorio; entrega no confirmada");return;}
        if(s.estado()!=ENVIANDO && s.estado()!=CANCELADA)return;
        if(referencia==null){
            solicitudes.guardar(s.estado(s.estado()==CANCELADA?CANCELADA:ERROR));
            evento(s,nombreCorreo("NO_CONFIRMADO"),null,"Envío no confirmado; la entrega puede ser incierta. Revisar antes de reenviar");return;
        }
        var p=paso(s);var cfg=s.politica().configuracion();
        var estado=p.contrato().modo().esEvidencia()?ESPERANDO_EVIDENCIA:cfg.condicionFinalizacion()==ConfiguracionPaso.Condicion.RESPUESTA_VALIDADA?ESPERANDO_CONFIRMACION:cfg.requiereAprobacion()?ENVIADA_PENDIENTE_APROBACION:ENVIADA;
        var enviada=solicitudes.guardar(s.enviada(reloj.instant(),referencia,e.estado()==EstadoEjecucion.EN_CURSO?estado:CANCELADA));
        evento(enviada,nombreCorreo("ACEPTADO"),null,"Servicio de correo aceptó el mensaje; entrega en bandeja de entrada no confirmada");
        if(e.estado()!=EstadoEjecucion.EN_CURSO)return;
        cambiarPaso(p,estado==ENVIADA?EstadoEjecucionPaso.COMPLETADO:p.contrato().modo().esEvidencia()?EstadoEjecucionPaso.ESPERANDO_EVIDENCIA:estado==ESPERANDO_CONFIRMACION?EstadoEjecucionPaso.ESPERANDO_CONFIRMACION:EstadoEjecucionPaso.EN_VALIDACION);
        if(estado==ENVIADA)avanzar(e);
    }
    @Transactional public void registrarManual(Ejecucion e,EjecucionPaso p,Integer actor){solicitudes.registrar(new EventoAutomatizacion(null,e.id(),null,p.contrato()!=null && p.contrato().esCritico()?"PASO_CRITICO_CONFIRMADO":"PASO_GUIADO_CONFIRMADO",actor,"Confirmación explícita mediante Siguiente del paso "+(p.contrato()==null?p.pasoId():p.contrato().orden()),reloj.instant()));}
    public List<SolicitudEvidencia> listar(Integer id,String email){autorizar(id,email);return solicitudes.porEjecucion(id);}
    public SolicitudEvidencia resultadoEnvio(Integer id,String solicitudId,String email){
        autorizar(id,email);return solicitudes.buscarActualizada(solicitudId).filter(s->s.ejecucionId().equals(id)).orElseThrow(()->new AccessDeniedException("Solicitud ajena o inexistente"));
    }
    public List<EventoAutomatizacion> eventos(Integer id,String email){autorizar(id,email);return solicitudes.eventos(id);}
    public List<EvidenciaFotografica> evidencias(Integer id,String solicitudId,String email){autorizar(id,email);pertenece(id,solicitudId);return solicitudes.evidencias(solicitudId);}
    public EvidenciaFotografica descargar(Integer id,String solicitudId,Long evidenciaId,String email) {
        autorizar(id,email);pertenece(id,solicitudId);
        return solicitudes.evidencia(evidenciaId).filter(e->e.solicitudId().equals(solicitudId)).orElseThrow(()->new AccessDeniedException("Evidencia ajena o inexistente"));
    }
    @Transactional
    public SolicitudEvidencia recibirSimulada(Integer id,String solicitudId,String remitente,String mensajeId,
            Map<EvidenciaFotografica.Categoria,byte[]> archivos,String email) {
        exigirSimulacion();
        return registrarRecepcion(id,solicitudId,remitente,mensajeId,archivos,email,true);
    }
    @Transactional
    public SolicitudEvidencia recibir(Integer id,String solicitudId,String remitente,String mensajeId,
            Map<EvidenciaFotografica.Categoria,byte[]> archivos,String email) {
        return registrarRecepcion(id,solicitudId,remitente,mensajeId,archivos,email,false);
    }
    private SolicitudEvidencia registrarRecepcion(Integer id,String solicitudId,String remitente,String mensajeId,
            Map<EvidenciaFotografica.Categoria,byte[]> archivos,String email,boolean simulada) {
        ejecuciones.bloquear(id);var e=autorizar(id,email);enCurso(e);var s=vigente(id,solicitudId);
        if(!paso(s).contrato().modo().esEvidencia())throw regla("Este paso no solicita fotografías");
        if(remitente==null || s.contexto().destinatarios().stream().noneMatch(d->d.correo().equalsIgnoreCase(remitente.strip())))throw new AccessDeniedException("El remitente no corresponde al responsable capturado");
        try{UUID.fromString(mensajeId);}catch(RuntimeException x){throw regla("ID de mensaje inválido");}
        if(archivos==null || !archivos.keySet().equals(categorias(s)))throw regla("Adjunta una fotografía para cada categoría: mueble, red y electricidad");
        var limpias=new EnumMap<EvidenciaFotografica.Categoria,byte[]>(EvidenciaFotografica.Categoria.class);
        for(var categoria:categorias(s))limpias.put(categoria,fotos.normalizar(archivos.get(categoria)));
        String huella=sha(limpias.entrySet().stream().map(v->v.getKey()+":"+sha(v.getValue())).reduce("",String::concat).getBytes(StandardCharsets.UTF_8));
        if(mensajeId.equals(s.mensajeRecibidoId())) {
            if(!huella.equals(s.huellaRecibida()))throw conflicto("El ID de mensaje ya se usó para otras fotografías");
            return s;
        }
        if(s.estado()!=ESPERANDO_EVIDENCIA)throw conflicto("Esta ronda ya no espera fotografías");
        for(var foto:limpias.entrySet())solicitudes.guardar(new EvidenciaFotografica(null,s.id(),foto.getKey(),sha(foto.getValue()),foto.getValue(),reloj.instant()));
        var recibida=solicitudes.guardar(s.recibida(mensajeId,huella));cambiarPaso(paso(s),EstadoEjecucionPaso.EVIDENCIA_RECIBIDA);
        evento(recibida,"EVIDENCIA_RECIBIDA",actor(email).getId(),simulada?"Recepción SIMULADA por usuario interno autorizado":"Recepción manual registrada por técnico autorizado; fotografías validadas y normalizadas. No se consultó la bandeja Gmail");
        evento(recibida,"TECNICO_NOTIFICADO",null,"Hay fotografías disponibles para revisión en el checklist y sus avisos");
        if(!paso(s).contrato().esCritico() && !s.politica().configuracion().requiereAprobacion()){
            var terminada=solicitudes.guardar(recibida.estado(APROBADA));cambiarPaso(paso(s),EstadoEjecucionPaso.COMPLETADO);evento(terminada,"EVIDENCIA_VALIDADA_AUTOMATICAMENTE",null,"Condición de evidencia completa configurada en paso no crítico");avanzar(e);return terminada;
        }
        return recibida;
    }
    public Map<EvidenciaFotografica.Categoria,byte[]> muestras(){exigirSimulacion();var result=new EnumMap<EvidenciaFotografica.Categoria,byte[]>(EvidenciaFotografica.Categoria.class);for(var c:List.of(EvidenciaFotografica.Categoria.MUEBLE,EvidenciaFotografica.Categoria.RED,EvidenciaFotografica.Categoria.ELECTRICIDAD))result.put(c,fotos.muestra(c));return result;}
    @Transactional
    public SolicitudEvidencia decidir(Integer id,String solicitudId,long version,boolean aprobar,boolean definitiva,String motivo,String email) {
        return decidir(id,solicitudId,version,aprobar,definitiva,motivo,email,true);
    }
    @Transactional
    public SolicitudEvidencia decidir(Integer id,String solicitudId,long version,boolean aprobar,boolean definitiva,String motivo,String email,boolean repetir) {
        ejecuciones.bloquear(id);var e=autorizar(id,email);enCurso(e);var s=vigente(id,solicitudId);
        if(s.version()!=version)throw conflicto("La solicitud cambió. Actualiza antes de decidir");
        if(!Set.of(EVIDENCIA_RECIBIDA,CONFIRMACION_RECIBIDA,ENVIADA_PENDIENTE_APROBACION,ACCION_PENDIENTE_APROBACION).contains(s.estado()))throw conflicto("La acción todavía no tiene un resultado revisable");
        if(paso(s).contrato().modo().esEvidencia() && !solicitudes.evidencias(s.id()).stream().map(EvidenciaFotografica::categoria).collect(java.util.stream.Collectors.toSet()).equals(categorias(s)))throw conflicto("Faltan categorías de evidencia");
        if(!aprobar && (motivo==null || motivo.isBlank() || motivo.length()>1000))throw regla("El rechazo requiere un motivo de hasta 1000 caracteres");
        var decidida=solicitudes.guardar(s.estado(aprobar?APROBADA:definitiva?RECHAZADA_DEFINITIVA:RECHAZADA));
        evento(decidida,aprobar?"EVIDENCIA_APROBADA":definitiva?"RECHAZO_DEFINITIVO":"EVIDENCIA_RECHAZADA",actor(email).getId(),aprobar?"Aprobación explícita de las evidencias por usuario autorizado":motivo.strip());
        cambiarPaso(paso(s),aprobar?EstadoEjecucionPaso.COMPLETADO:EstadoEjecucionPaso.RECHAZADO);
        if(!aprobar && !definitiva && repetir) {
            var c=s.contexto();var contexto=new SolicitudEvidencia.Contexto(c.sucursalId(),c.sucursalCodigo(),c.sucursalNombre(),c.responsableNombre(),c.responsableEmail(),c.actorId(),c.procedimientoId(),null,c.destinatarios());
            var nueva=solicitudes.guardar(new SolicitudEvidencia(UUID.randomUUID().toString(),id,s.ejecucionPasoId(),s.ronda()+1,contexto,s.politica(),PENDIENTE,null,null,0,null,null,null,null));
            evento(nueva,"NUEVA_RONDA",actor(email).getId(),"Se solicitan nuevas fotografías tras rechazo");cambiarPaso(paso(s),EstadoEjecucionPaso.PENDIENTE);
        }
        if(aprobar || repetir)avanzar(e);return decidida;
    }
    @Transactional
    public SolicitudEvidencia solicitarNuevamente(Integer id,String solicitudId,long version,String email) {
        ejecuciones.bloquear(id);var e=autorizar(id,email);enCurso(e);var anterior=pertenece(id,solicitudId);
        var ultima=solicitudes.porEjecucion(id).stream().filter(s->s.ejecucionPasoId().equals(anterior.ejecucionPasoId())).max(Comparator.comparingInt(SolicitudEvidencia::ronda)).orElseThrow();
        // Repetir el mismo clic nunca crea o envía una segunda ronda.
        if(ultima.ronda()>anterior.ronda())return ultima;
        if(anterior.version()!=version || anterior.estado()!=RECHAZADA)throw conflicto("Actualiza la solicitud: solo se puede repetir una ronda rechazada");
        if(!paso(anterior).contrato().modo().esEvidencia())throw regla("Este paso no solicita fotografías");
        if(!correo.disponible())throw regla("El servicio de correo no está disponible");
        var c=anterior.contexto();var contexto=new SolicitudEvidencia.Contexto(c.sucursalId(),c.sucursalCodigo(),c.sucursalNombre(),c.responsableNombre(),c.responsableEmail(),c.actorId(),c.procedimientoId(),null,c.destinatarios());
        var nueva=solicitudes.guardar(new SolicitudEvidencia(UUID.randomUUID().toString(),id,anterior.ejecucionPasoId(),anterior.ronda()+1,contexto,anterior.politica(),PENDIENTE,null,null,0,null,null,null,null));
        evento(nueva,"NUEVA_RONDA",actor(email).getId(),"Nueva solicitud explícita tras rechazo; evidencias anteriores conservadas");
        cambiarPaso(paso(anterior),EstadoEjecucionPaso.PENDIENTE);avanzar(e);
        return solicitudes.buscar(nueva.id()).orElseThrow();
    }
    @Transactional
    public void cancelar(Ejecucion e,Integer actorId) {
        for(var s:solicitudes.porEjecucion(e.id()))if(Set.of(PENDIENTE,ESPERANDO_EVIDENCIA,EVIDENCIA_RECIBIDA,ERROR,ESPERANDO_CONFIRMACION,CONFIRMACION_RECIBIDA,ENVIADA_PENDIENTE_APROBACION,ACCION_PENDIENTE_APROBACION).contains(s.estado())) {
            solicitudes.guardar(s.estado(CANCELADA));evento(s,"SOLICITUD_CANCELADA",actorId,"Se cancela el seguimiento con la ejecución");
        }
    }
    public List<String> vencidas(){return correo.disponible()?solicitudes.vencidas(reloj.instant(),!correo.simulado()):List.of();}
    @Transactional
    public void procesarSeguimiento(String solicitudId) {
        var inicial=solicitudes.buscar(solicitudId).orElseThrow();ejecuciones.bloquear(inicial.ejecucionId());
        var e=ejecuciones.buscarPorId(inicial.ejecucionId()).orElseThrow();
        if(e.estado()!=EstadoEjecucion.EN_CURSO){cancelar(e,null);return;}
        seguimiento(solicitudes.buscar(solicitudId).orElseThrow(),reloj.instant());
    }
    @Transactional
    public void simularTiempo(Integer id,String solicitudId,int horas,String email) {
        exigirSimulacion();ejecuciones.bloquear(id);enCurso(autorizar(id,email));var s=vigente(id,solicitudId);
        if(s.fechaEnvio()==null || horas<0 || horas>s.politica().configuracion().plazoRespuestaHoras())throw regla("Plazo de simulación inválido");
        seguimiento(s,s.fechaEnvio().plusSeconds(horas*3600L));
    }
    private void seguimiento(SolicitudEvidencia s,Instant ahora) {
        if(!correo.simulado() && esEnvioSimulado(s))return;
        if(!Set.of(ESPERANDO_EVIDENCIA,ESPERANDO_CONFIRMACION).contains(s.estado()) || s.fechaEnvio()==null)return;
        var cfg=s.politica().configuracion();long horas=Duration.between(s.fechaEnvio(),ahora).toHours();
        var reglas=new TreeSet<Integer>(cfg.recordatoriosHoras());reglas.add(cfg.plazoRespuestaHoras());
        int regla=reglas.stream().filter(h->h<=horas).mapToInt(Integer::intValue).max().orElse(0);
        if(regla<=s.reglaAplicada())return;
        if(regla<cfg.plazoRespuestaHoras()){if(correo.simulado())correo.enviar(s,regla);else eventosCorreo.publishEvent(new CorreoPendiente(s.id(),regla));}
        Integer proxima=reglas.higher(regla);Instant proximo=proxima==null?null:s.fechaEnvio().plusSeconds(proxima*3600L);
        solicitudes.guardar(s.seguimiento(regla,proximo));
        evento(s,regla==cfg.plazoRespuestaHoras()?"ESCALADO_AL_TECNICO":correo.simulado()?"RECORDATORIO_SIMULADO":"RECORDATORIO_PROGRAMADO",null,"Regla "+regla+" horas; vencimientos anteriores coalescidos");
    }
    private Set<EvidenciaFotografica.Categoria> categorias(SolicitudEvidencia s){return s.politica().configuracion().evidenciasRequeridas().stream().map(EvidenciaFotografica.Categoria::valueOf).collect(java.util.stream.Collectors.toSet());}
    public Map<EvidenciaFotografica.Categoria,byte[]> muestras(Integer id,String solicitud,String email){exigirSimulacion();autorizar(id,email);var s=pertenece(id,solicitud);var result=new EnumMap<EvidenciaFotografica.Categoria,byte[]>(EvidenciaFotografica.Categoria.class);for(var c:categorias(s))result.put(c,fotos.muestra(c));return result;}
    @Transactional public SolicitudEvidencia confirmarRespuesta(Integer id,String solicitud,String remitente,String mensajeId,String texto,String email){
        exigirSimulacion();ejecuciones.bloquear(id);var e=autorizar(id,email);enCurso(e);var s=vigente(id,solicitud);
        if(remitente==null || s.contexto().destinatarios().stream().noneMatch(d->d.correo().equalsIgnoreCase(remitente.strip())))throw new AccessDeniedException("Remitente no autorizado para la confirmación");
        try{UUID.fromString(mensajeId);}catch(RuntimeException x){throw regla("ID de mensaje inválido");}
        if(texto==null || texto.isBlank() || texto.length()>1000)throw regla("La confirmación requiere texto de hasta 1000 caracteres");
        String huella=sha(texto.strip().getBytes(StandardCharsets.UTF_8));
        if(mensajeId.equals(s.mensajeRecibidoId())){if(!huella.equals(s.huellaRecibida()))throw conflicto("Mensaje reutilizado con otro contenido");return s;}
        if(s.estado()!=ESPERANDO_CONFIRMACION)throw conflicto("El paso no espera una confirmación");
        var recibida=solicitudes.guardar(s.confirmacion(mensajeId,huella));evento(recibida,"CONFIRMACION_RECIBIDA",actor(email).getId(),texto.strip());
        if(s.politica().configuracion().requiereAprobacion()){cambiarPaso(paso(s),EstadoEjecucionPaso.EN_VALIDACION);return recibida;}
        var terminada=solicitudes.guardar(recibida.estado(APROBADA));cambiarPaso(paso(s),EstadoEjecucionPaso.COMPLETADO);avanzar(e);return terminada;
    }
    private SolicitudEvidencia pertenece(Integer id,String solicitudId){return solicitudes.buscar(solicitudId).filter(s->s.ejecucionId().equals(id)).orElseThrow(()->new AccessDeniedException("Solicitud ajena o inexistente"));}
    private SolicitudEvidencia vigente(Integer id,String solicitudId) {
        var s=pertenece(id,solicitudId);
        if(solicitudes.porEjecucion(id).stream().anyMatch(otra->otra.ejecucionPasoId().equals(s.ejecucionPasoId()) && otra.ronda()>s.ronda()))throw conflicto("La ronda fue reemplazada");
        return s;
    }
    private EjecucionPaso paso(SolicitudEvidencia s){return pasos.buscarPorId(s.ejecucionPasoId()).filter(p->p.ejecucionId().equals(s.ejecucionId())).orElseThrow();}
    private void cambiarPaso(EjecucionPaso p,EstadoEjecucionPaso estado) {
        pasos.guardar(new EjecucionPaso(p.id(),p.ejecucionId(),p.pasoId(),estado==EstadoEjecucionPaso.COMPLETADO,p.observacion(),
                estado==EstadoEjecucionPaso.COMPLETADO?LocalDateTime.ofInstant(reloj.instant(),ZoneId.of("America/Santiago")):null,p.contrato(),estado,p.version()+1));
    }
    private Ejecucion autorizar(Integer id,String email) {
        var u=actor(email);var e=ejecuciones.buscarPorId(id).orElseThrow(()->new EjecucionNoEncontradaException(id));
        if(!u.getId().equals(e.usuario().getId()) && !"ADMINISTRADOR".equalsIgnoreCase(u.getRol().getNombre().strip()))throw new AccessDeniedException("Ejecución ajena");return e;
    }
    private Usuario actor(String email) {
        var u=usuarios.buscarPorEmail(email).filter(Usuario::isActivo).orElseThrow(()->new AccessDeniedException("Usuario no disponible"));
        if(u.getRol()==null || !Set.of("ADMINISTRADOR","TECNICO","SOPORTE").contains(u.getRol().getNombre().strip().toUpperCase(Locale.ROOT)))throw new AccessDeniedException("Rol no autorizado");return u;
    }
    private void enCurso(Ejecucion e){if(e.estado()!=EstadoEjecucion.EN_CURSO)throw conflicto("La ejecución está cerrada");}
    private void exigirSimulacion(){if(!correo.simulado())throw regla("La recepción de prueba solo está disponible en modo SIMULADO");}
    private boolean esEnvioSimulado(SolicitudEvidencia s){return s.mensajeEnviadoId()!=null && s.mensajeEnviadoId().startsWith("SIMULADO-");}
    private String nombreCorreo(String resultado){return ("GMAIL_API".equals(correo.transporte())?"GMAIL_":"SMTP_")+resultado;}
    private void evento(SolicitudEvidencia s,String tipo,Integer actor,String detalle){solicitudes.registrar(new EventoAutomatizacion(null,s.ejecucionId(),s.id(),tipo,actor,detalle,reloj.instant()));}
    public record Comunicacion(EventoAutomatizacion evento,Integer pasoId,String estado,String responsable,List<DestinatarioPaso> destinatarios,List<Long> evidencias){}
    public record SolicitudLectura(SolicitudEvidencia detalle,List<FotoEvidenciaResumen> fotos,boolean simulado){}
    public record LecturaAutomatizacion(List<SolicitudLectura> solicitudes,List<EventoAutomatizacion> actividad,boolean disponible,boolean simulado,List<Comunicacion> comunicaciones){}
    /** Una autorización y una sola transacción para toda la respuesta; nunca envía ni crea solicitudes. */
    public LecturaAutomatizacion consultarEstado(Integer id,String email){
        autorizar(id,email);var rondas=solicitudes.porEjecucion(id);var eventos=solicitudes.eventos(id);var fotos=fotosDe(rondas);
        var lista=rondas.stream().map(r->new SolicitudLectura(r,fotos.get(r.id()),r.mensajeEnviadoId()==null?correo.simulado():r.mensajeEnviadoId().startsWith("SIMULADO-"))).toList();
        return new LecturaAutomatizacion(lista,eventos,correo.disponible(),correo.simulado(),construirHistorial(rondas,eventos,fotos));
    }
    public List<Comunicacion> historial(Integer id,String email){
        autorizar(id,email);var rondas=solicitudes.porEjecucion(id);return construirHistorial(rondas,solicitudes.eventos(id),fotosDe(rondas));
    }
    private Map<String,List<FotoEvidenciaResumen>> fotosDe(List<SolicitudEvidencia> rondas){var fotos=new HashMap<String,List<FotoEvidenciaResumen>>();for(var r:rondas)fotos.put(r.id(),solicitudes.fotosResumen(r.id()));return fotos;}
    private List<Comunicacion> construirHistorial(List<SolicitudEvidencia> rondas,List<EventoAutomatizacion> eventos,Map<String,List<FotoEvidenciaResumen>> fotos){
        var nombres=new HashMap<Integer,String>();var actores=eventos.stream().map(EventoAutomatizacion::actorId).filter(Objects::nonNull).distinct().toList();
        if(!actores.isEmpty())usuarios.buscarPorIds(actores).forEach(u->nombres.put(u.getId(),u.getNombre()));
        return eventos.stream().filter(v->v.solicitudId()!=null && !Set.of("CONTEXTO_INICIO","CONFIGURACION_CORREO").contains(v.tipo())).map(v->{
            var solicitud=rondas.stream().filter(r->r.id().equals(v.solicitudId())).findFirst().orElseThrow();
            String responsable=v.actorId()==null?"Sistema":nombres.getOrDefault(v.actorId(),"Usuario #"+v.actorId());
            String estado=switch(v.tipo()) {case "SOLICITUD_CREADA","NUEVA_RONDA"->"PENDIENTE";case "EVIDENCIA_RECIBIDA"->"EVIDENCIA_RECIBIDA";case "EVIDENCIA_APROBADA"->"APROBADA";case "EVIDENCIA_RECHAZADA"->"RECHAZADA";case "RECHAZO_DEFINITIVO"->"RECHAZADA_DEFINITIVA";case "SOLICITUD_CANCELADA"->"CANCELADA";case "CONFIRMACION_RECIBIDA"->"CONFIRMACION_RECIBIDA";case "SOLICITUD_ENVIADA_SIMULADA","RECORDATORIO_SIMULADO","ESCALADO_AL_TECNICO"->Set.of(ConfiguracionPaso.Condicion.EVIDENCIAS_APROBADAS,ConfiguracionPaso.Condicion.EVIDENCIAS_RECIBIDAS).contains(solicitud.politica().configuracion().condicionFinalizacion())?"ESPERANDO_EVIDENCIA":solicitud.politica().configuracion().condicionFinalizacion()==ConfiguracionPaso.Condicion.RESPUESTA_VALIDADA?"ESPERANDO_CONFIRMACION":solicitud.politica().configuracion().requiereAprobacion()?"ENVIADA_PENDIENTE_APROBACION":"ENVIADA";default->"REGISTRADO";};
            var evidencias=Set.of("EVIDENCIA_RECIBIDA","EVIDENCIA_APROBADA","EVIDENCIA_RECHAZADA","RECHAZO_DEFINITIVO").contains(v.tipo())?fotos.getOrDefault(solicitud.id(),List.of()).stream().map(FotoEvidenciaResumen::id).toList():List.<Long>of();
            return new Comunicacion(v,solicitud.ejecucionPasoId(),estado,responsable,solicitud.contexto().destinatarios(),evidencias);
        }).toList();
    }
    private static String sha(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(Exception e){throw new IllegalStateException(e);}}
    private ReglaEjecucionException regla(String mensaje){return new ReglaEjecucionException(mensaje);}
    private ConflictoEjecucionException conflicto(String mensaje){return new ConflictoEjecucionException(mensaje);}
}
