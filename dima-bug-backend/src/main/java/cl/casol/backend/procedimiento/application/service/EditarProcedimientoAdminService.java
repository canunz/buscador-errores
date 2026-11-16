package cl.casol.backend.procedimiento.application.service;

import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import cl.casol.backend.conocimiento.application.port.out.MaterialApoyoRepository;
import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import cl.casol.backend.catalogo.application.port.out.*;
import cl.casol.backend.shared.application.port.out.AlmacenamientoArchivoPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.*;

@Service @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class EditarProcedimientoAdminService {
    private final ProcedimientoRepository procedimientos;private final PasoRepository pasos;private final MaterialApoyoRepository materiales;
    private final HardwareRepository hardware;private final SistemaRepository sistemas;private final ModuloRepository modulos;private final AlmacenamientoArchivoPort archivos;
    private final MantenerProcedimientoService mantener;private final AuditoriaProcedimientoService auditoria;
    public EditarProcedimientoAdminService(ProcedimientoRepository p,PasoRepository pasos,MaterialApoyoRepository m,SistemaRepository s,ModuloRepository mod,AlmacenamientoArchivoPort a,MantenerProcedimientoService mantener,AuditoriaProcedimientoService audit,HardwareRepository hardware){this.procedimientos=p;this.pasos=pasos;this.materiales=m;this.sistemas=s;this.modulos=mod;this.archivos=a;this.mantener=mantener;this.auditoria=audit;this.hardware=hardware;}
    private Procedimiento buscar(Integer id){return procedimientos.buscarPorId(id).orElseThrow(()->new ProcedimientoNoEncontradoException(id));}
    private Integer raiz(Procedimiento p){return p.datos().origenId()==null?p.id():p.datos().origenId();}
    private List<Procedimiento> familia(Integer id){return procedimientos.buscarTodos().stream().filter(p->p.id().equals(id)||Objects.equals(p.datos().origenId(),id)).toList();}
    /** El publicado nunca se vuelve editable: se trabaja sobre una revisión que no tiene ejecuciones. */
    public Procedimiento preparar(Integer id,String email){
        var actor=auditoria.administrador(email);Integer raiz=raiz(buscar(id));procedimientos.bloquear(raiz);var origen=buscar(id);
        if(origen.estado()==EstadoProcedimiento.BORRADOR && !procedimientos.tieneEjecuciones(origen.id()))return origen;
        var familia=familia(raiz);
        var pendiente=familia.stream().filter(p->p.estado()==EstadoProcedimiento.BORRADOR && !procedimientos.tieneEjecuciones(p.id())).findFirst();
        if(pendiente.isPresent())return pendiente.get();
        if(!origen.datos().vigente())origen=familia.stream().filter(p->p.estado()==EstadoProcedimiento.PUBLICADO && p.datos().vigente()).findFirst().orElseThrow(()->new EstadoProcedimientoInvalidoException("No se encontró una versión vigente"));
        int revision=familia.stream().mapToInt(p->p.datos().revision()).max().orElse(1)+1;
        var d=origen.datos();var nuevo=procedimientos.guardar(new Procedimiento(null,origen.nombre(),origen.descripcion(),EstadoProcedimiento.BORRADOR,actor,LocalDateTime.now(),null,null,new DatosEditorialesProcedimiento(d.sistemaId(),d.moduloId(),d.informacionComplementaria(),raiz,revision,true),origen.hardwareId()));
        for(var paso:pasos.buscarPorProcedimientoOrdenados(origen.id())){
            var copia=pasos.guardar(new Paso(null,nuevo.id(),paso.orden(),paso.instruccion(),paso.esCritico(),paso.modoEjecucion(),paso.mensajeEvidencia(),paso.configuracion()));
            for(var m:materiales.buscarPorPaso(paso.id())){
                String url=m.url();
                if(url!=null && url.startsWith("file:")){
                    url=archivos.guardar(archivos.leer(url));String referencia=url;
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)archivos.compensar(referencia);}});
                }
                materiales.guardar(new MaterialApoyo(null,null,copia.id(),m.nombre(),m.tipo(),url));
            }
        }
        auditoria.registrar(nuevo.id(),"REVISION_CREADA",actor.getId(),AuditoriaProcedimientoService.datos(origen),AuditoriaProcedimientoService.datos(nuevo));return nuevo;
    }
    public Procedimiento guardar(Integer id,String nombre,String descripcion,Integer sistemaId,Integer moduloId,String informacion,EstadoProcedimiento estado,String email){return guardarInterno(id,nombre,descripcion,sistemaId,moduloId,informacion,estado,email,null,false);}
    public Procedimiento guardar(Integer id,String nombre,String descripcion,Integer sistemaId,Integer moduloId,String informacion,EstadoProcedimiento estado,String email,Integer hardwareId){return guardarInterno(id,nombre,descripcion,sistemaId,moduloId,informacion,estado,email,hardwareId,true);}
    private Procedimiento guardarInterno(Integer id,String nombre,String descripcion,Integer sistemaId,Integer moduloId,String informacion,EstadoProcedimiento estado,String email,Integer hardwareId,boolean cambiarHardware){
        var actor=auditoria.administrador(email);Procedimiento antes=id==null?null:buscar(id);
        if(antes!=null){procedimientos.bloquear(raiz(antes));if(!raiz(antes).equals(antes.id()))procedimientos.bloquear(antes.id());antes=buscar(id);}
        if(!cambiarHardware && sistemaId!=null)sistemas.buscarActivoPorId(sistemaId).orElseThrow(()->new EstadoProcedimientoInvalidoException("Selecciona un sistema activo"));
        if(!cambiarHardware && moduloId!=null && (sistemaId==null || modulos.buscarActivoPorId(moduloId).filter(m->Objects.equals(m.sistemaId(),sistemaId)).isEmpty()))throw new EstadoProcedimientoInvalidoException("El módulo no pertenece al sistema seleccionado");
        Integer hardwareFinal=cambiarHardware?hardwareId:antes==null?null:antes.hardwareId();
        if(cambiarHardware && hardwareFinal!=null){if(hardwareFinal<1 || hardware.buscarPorId(hardwareFinal).isEmpty())throw new EstadoProcedimientoInvalidoException("El hardware seleccionado no existe");}
        Integer sistemaFinal=cambiarHardware?(antes==null?null:antes.datos().sistemaId()):sistemaId;
        Integer moduloFinal=cambiarHardware?(antes==null?null:antes.datos().moduloId()):moduloId;
        var p=id==null?mantener.crear(nombre,descripcion,email):mantener.modificar(id,nombre,descripcion,email);var d=p.datos();
        var guardado=procedimientos.guardar(new Procedimiento(p.id(),p.nombre(),p.descripcion(),p.estado(),p.creadoPor(),p.fechaCreacion(),actor,LocalDateTime.now(),new DatosEditorialesProcedimiento(sistemaFinal,moduloFinal,informacion,d.origenId(),d.revision(),d.vigente()),hardwareFinal));
        auditoria.registrar(guardado.id(),id==null?"PROCEDIMIENTO_CREADO":"DATOS_MODIFICADOS",actor.getId(),antes==null?null:AuditoriaProcedimientoService.datos(antes),AuditoriaProcedimientoService.datos(guardado));
        return estado==EstadoProcedimiento.PUBLICADO?publicar(guardado.id(),email):guardado;
    }
    public Procedimiento publicar(Integer id,String email){
        var actor=auditoria.administrador(email);Integer raiz=raiz(buscar(id));procedimientos.bloquear(raiz);if(!raiz.equals(id))procedimientos.bloquear(id);var actual=buscar(id);
        EdicionProcedimiento.exigirEditable(actual,procedimientos);
        if(pasos.buscarPorProcedimientoOrdenados(id).isEmpty())throw new EstadoProcedimientoInvalidoException("Agrega al menos un paso antes de publicar");
        for(var previa:familia(raiz))if(!previa.id().equals(id) && previa.estado()==EstadoProcedimiento.PUBLICADO && previa.datos().vigente()){
            var retirada=procedimientos.guardar(new Procedimiento(previa.id(),previa.nombre(),previa.descripcion(),previa.estado(),previa.creadoPor(),previa.fechaCreacion(),previa.modificadoPor(),previa.fechaModificacion(),previa.datos().vigencia(false),previa.hardwareId()));
            auditoria.registrar(previa.id(),"VERSION_REEMPLAZADA",actor.getId(),AuditoriaProcedimientoService.datos(previa),AuditoriaProcedimientoService.datos(retirada));
        }
        var publicada=mantener.publicar(id,email);auditoria.registrar(id,"REVISION_PUBLICADA",actor.getId(),AuditoriaProcedimientoService.datos(actual),AuditoriaProcedimientoService.datos(publicada));return publicada;
    }
}
