import { AuthService } from '../../core/services/auth.service';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { ValidacionExternaComponent } from './validacion-externa.component';
import { AutomatizacionEjecucionService, EstadoAutomatizacion, SolicitudEvidencia } from '../../core/services/automatizacion-ejecucion.service';
describe('SOLICITAR_EVIDENCIA en el checklist existente', () => {
  let c: ValidacionExternaComponent;
  let api: jasmine.SpyObj<AutomatizacionEjecucionService>;
  let estado: EstadoAutomatizacion;
  const solicitud: SolicitudEvidencia = { id: 'ronda-1', ejecucionId: 20, ejecucionPasoId: 1, ronda: 1, version: 2,
    contexto: { sucursalId: 1, sucursalCodigo: 'QA', sucursalNombre: 'Sintética', responsableNombre: 'Jefe QA', responsableEmail: 'qa-no-enviar@dimarsa.cl' },
    politica: { mensaje: 'Tres fotos', primerRecordatorioHoras: 24, segundoRecordatorioHoras: 72, escalamientoHoras: 120 },
    estado: 'ESPERANDO_EVIDENCIA', fechaEnvio: '2026-11-07T15:00:00Z', mensajeEnviadoId: 'SIMULADO-1', proximoSeguimiento: '2026-11-08T15:00:00Z', reglaAplicada: 0 };
  beforeEach(() => {
    estado = { solicitudes: [{ detalle: { ...solicitud }, fotos: [], simulado: true }], actividad: [], disponible: true, simulado: true };
    api = jasmine.createSpyObj('auto', ['estado','simular','subir','decidir','tiempo','foto','sucursales','destinatario','enviar','solicitarNuevamente']);
    api.estado.and.callFake(() => of(estado)); api.foto.and.returnValue(of(new Blob(['foto de prueba'], { type: 'image/jpeg' })));
    api.sucursales.and.returnValue(of([{id:1,codigo:"QA",nombre:"Calbuco",ciudad:"Calbuco",responsableNombre:"Jefe QA"}]));
    api.destinatario.and.returnValue(of({id:1,nombre:"Calbuco",responsableNombre:"Jefe QA",responsableCargo:"Jefe de tienda",responsableEmail:"qa-no-enviar@dimarsa.cl",activa:true,destinatarios:[{correo:"qa-no-enviar@dimarsa.cl",tipo:"PRINCIPAL"}]}));
    TestBed.configureTestingModule({ providers: [{provide:AuthService,useValue:{isAdmin:()=>false}},{ provide: AutomatizacionEjecucionService, useValue: api }] });
    c = TestBed.runInInjectionContext(() => new ValidacionExternaComponent());
    c.ejecucionId = 20; c.enCurso = true;
    c.paso = { ejecucionPasoId: 1, pasoId: 1, orden: 1, instruccion: 'Instalaciones', esCritico: true, cumplido: false, observacion: null, fecha: null, modoEjecucion: 'VALIDACION_EXTERNA' };
    c.cargar();
  });
  afterEach(() => c.ngOnDestroy());
  function listaParaRevision(): void {
    estado.solicitudes[0].detalle = { ...solicitud, estado: 'EVIDENCIA_RECIBIDA', version: 3 };
    estado.solicitudes[0].fotos = [{ id: 1, categoria: 'MUEBLE', sha256: 'a' }, { id: 2, categoria: 'RED', sha256: 'b' }, { id: 3, categoria: 'ELECTRICIDAD', sha256: 'c' }];
    c.cargar(); for (const id of [1, 2, 3]) c.fotoCargada(id);
  }
  it('registra fotografías propias en modo real sin simular ni enviar correo',()=>{
    estado.solicitudes[0].simulado=false;c.cargar();
    for(const categoria of c.categorias)c.archivos[categoria.toLowerCase()]=new File(['foto'],'foto.jpg',{type:'image/jpeg'});
    api.subir.and.returnValue(of({...solicitud,estado:'EVIDENCIA_RECIBIDA'}));
    c.recibir(false);expect(api.subir).toHaveBeenCalledTimes(1);expect(api.simular).not.toHaveBeenCalled();expect(api.enviar).not.toHaveBeenCalled();expect(c.ocupado).toBeFalse();
  });
  it('permite quitar una selección sin borrar evidencia histórica y rechaza formatos inválidos',()=>{
    c.seleccionar('mueble',{target:{files:[new File(['x'],'mal.txt',{type:'text/plain'})],value:'x'}} as unknown as Event);
    expect(c.archivos['mueble']).toBeUndefined();expect(c.error).toContain('JPEG');
    c.archivos['mueble']=new File(['foto'],'foto.jpg',{type:'image/jpeg'});c.quitarArchivo('mueble');expect(c.tresArchivos).toBeFalse();
  });
  it('no avanza si falla la aprobación',()=>{
    listaParaRevision();const emit=spyOn(c.actualizada,'emit');api.decidir.and.returnValue(throwError(()=>new Error('No autorizado')));c.decidir(true);
    expect(emit).not.toHaveBeenCalled();expect(c.error).toContain('No autorizado');expect(c.ocupado).toBeFalse();
  });
  it('rechazar no envía otra solicitud y solicitar nuevamente evita doble clic',()=>{
    listaParaRevision();c.motivo='Fotografía borrosa';api.decidir.and.returnValue(of({...solicitud,estado:'RECHAZADA'}));c.decidir(false);
    expect(api.decidir.calls.mostRecent().args[5]).toBeTrue();expect(api.solicitarNuevamente).not.toHaveBeenCalled();expect(api.enviar).not.toHaveBeenCalled();
    estado.solicitudes[0].detalle={...solicitud,estado:'RECHAZADA'};c.cargar();api.solicitarNuevamente.and.returnValue(new Subject());
    c.solicitarNuevamente();c.solicitarNuevamente();expect(api.solicitarNuevamente).toHaveBeenCalledTimes(1);
  });
  it('muestra el formulario de fotografías después de un envío real',()=>{
    estado.solicitudes[0].simulado=false;estado.simulado=false;
    const fixture=TestBed.createComponent(ValidacionExternaComponent);
    fixture.componentRef.setInput('ejecucionId',20);fixture.componentRef.setInput('paso',c.paso);fixture.componentRef.setInput('enCurso',true);
    fixture.detectChanges();
    const html=fixture.nativeElement as HTMLElement;
    expect(html.textContent).toContain('Evidencias recibidas');expect(html.textContent).toContain('Registrar recepción');
    expect(html.querySelectorAll('input[type=file]').length).toBe(3);expect(html.textContent).not.toContain('Simular recepción');
    fixture.destroy();
  });
  it('obtiene sucursal, destinatario y plazo persistidos', () => { expect(c.solicitud?.detalle.contexto.sucursalNombre).toBe('Sintética'); expect(c.remitente).toBe(solicitud.contexto.responsableEmail); expect(c.solicitud?.detalle.proximoSeguimiento).toBe(solicitud.proximoSeguimiento); });
  it('cambiar a otro paso externo limpia la solicitud anterior antes de consultar', () => {
    api.estado.and.returnValue(new Subject<EstadoAutomatizacion>());
    c.paso = { ...c.paso, ejecucionPasoId: 2, orden: 2 }; c.ngOnChanges();
    expect(c.solicitud).toBeNull(); expect(c.remitente).toBe(''); expect(c.actividad).toEqual([]);
    c.recibir(true); expect(api.simular).not.toHaveBeenCalled();
  });
  it('recibir fotografías no marca el paso ni emite avance', () => {
    const respuesta = new Subject<SolicitudEvidencia>(); api.simular.and.returnValue(respuesta); spyOn(c.actualizada, 'emit');
    c.recibir(true); expect(c.ocupado).toBeTrue(); expect(c.paso.cumplido).toBeFalse();
    estado.solicitudes[0].detalle = { ...solicitud, estado: 'EVIDENCIA_RECIBIDA', version: 3 };
    respuesta.next(estado.solicitudes[0].detalle);
    expect(c.actualizada.emit).toHaveBeenCalledWith({ avanzar: false, pasoId: 1 }); expect(c.paso.cumplido).toBeFalse();
  });
  it('aprobar es una única petición y solo solicita avance después de confirmar', () => {
    listaParaRevision();
    const respuesta = new Subject<SolicitudEvidencia>(); api.decidir.and.returnValue(respuesta); spyOn(c.actualizada, 'emit');
    c.decidir(true); c.decidir(true); expect(api.decidir).toHaveBeenCalledTimes(1); expect(c.actualizada.emit).not.toHaveBeenCalled();
    respuesta.next({ ...solicitud, estado: 'APROBADA', version: 4 }); expect(c.actualizada.emit).toHaveBeenCalledWith({ avanzar: true, pasoId: 1 });
  });
  it('rechazar requiere motivo y no solicita otra ronda automáticamente', () => {
    listaParaRevision();
    api.decidir.and.returnValue(of({ ...solicitud, estado: 'RECHAZADA' })); c.decidir(false); expect(api.decidir).not.toHaveBeenCalled();
    c.motivo = 'No se distingue la toma'; api.decidir.and.callFake(()=>{estado.solicitudes[0].detalle={...solicitud,estado:'RECHAZADA'};return of(estado.solicitudes[0].detalle);});
    c.decidir(false); expect(api.decidir.calls.mostRecent().args.slice(2)).toEqual([false, 'No se distingue la toma', false, true]); expect(c.solicitud?.detalle.ronda).toBe(1);expect(api.solicitarNuevamente).not.toHaveBeenCalled();
  });
  it('un fallo no presenta una aprobación como confirmada', () => { listaParaRevision(); api.decidir.and.returnValue(throwError(() => new Error('Conflicto de versión'))); c.decidir(true); expect(c.error).toContain('Conflicto'); expect(c.solicitud?.detalle.estado).toBe('EVIDENCIA_RECIBIDA'); expect(c.paso.cumplido).toBeFalse(); });
  it('no permite aprobar antes de recibir o visualizar las tres fotos', () => { c.decidir(true); expect(api.decidir).not.toHaveBeenCalled(); estado.solicitudes[0].detalle.estado = 'EVIDENCIA_RECIBIDA'; c.cargar(); c.decidir(true); expect(api.decidir).not.toHaveBeenCalled(); });
  it('la simulación de plazo solo invoca seguimiento, nunca completar un paso', () => { api.tiempo.and.returnValue(of(undefined)); c.simularTiempo(72); expect(api.tiempo).toHaveBeenCalledWith(20, 'ronda-1', 72); expect(c.paso.cumplido).toBeFalse(); });
  it('muestra tres fotografías obtenidas por HTTP autenticado', () => {
    estado.solicitudes[0].fotos = [{ id: 1, categoria: 'MUEBLE', sha256: 'a' }, { id: 2, categoria: 'RED', sha256: 'b' }, { id: 3, categoria: 'ELECTRICIDAD', sha256: 'c' }]; c.cargar();
    expect(api.foto).toHaveBeenCalledTimes(3); expect(Object.keys(c.fotos).length).toBe(3);
  });
  function pendiente(){estado.solicitudes=[];c.cargar();c.abrirCorreo();c.destinatarios=[{correo:"qa-no-enviar@dimarsa.cl",tipo:"PRINCIPAL"}];}
  it('abre evidencias con destinatarios directos sin consultar sucursales ni responsables',()=>{pendiente();expect(c.modalAbierto).toBeTrue();expect(c.solicitud).toBeNull();expect(c.destinatarios.length).toBe(1);expect(c.asunto).not.toContain('Calbuco');expect(api.sucursales).not.toHaveBeenCalled();expect(api.destinatario).not.toHaveBeenCalled();});
  it('no ofrece ni abre el envío en un paso manual',()=>{c.solicitud=null;c.paso={...c.paso,modoEjecucion:'MANUAL_GUIADO'};c.abrirCorreo();expect(c.modalAbierto).toBeFalse();});
  it('muestra confirmación simulada solo al recibir un envío confirmado',()=>{pendiente();api.enviar.and.returnValue(of(solicitud));c.enviarCorreo();expect(c.modalAbierto).toBeFalse();expect(c.aviso).toBe('Correo simulado correctamente. No se envió un mensaje real');expect(c.resultadoEnvio?.fechaEnvio).toBe(solicitud.fechaEnvio);});
  it('el error mantiene el modal y el mensaje para reintentar',()=>{pendiente();const mensaje=c.mensaje;api.enviar.and.returnValue(throwError(()=>new Error('Proveedor no disponible')));c.enviarCorreo();expect(c.modalAbierto).toBeTrue();expect(c.errorModal).toContain('Proveedor');expect(c.mensaje).toBe(mensaje);expect(c.resultadoEnvio).toBeNull();});
  it('bloquea doble clic y cierre mientras confirma el envío',()=>{pendiente();api.enviar.and.returnValue(new Subject());c.enviarCorreo();c.enviarCorreo();c.cerrarCorreo();expect(api.enviar).toHaveBeenCalledTimes(1);expect(c.modalAbierto).toBeTrue();});
  it('incluye todas las rondas del paso y excluye las comunicaciones ajenas',()=>{estado.solicitudes.push({detalle:{...solicitud,id:'ronda-2',ronda:2},fotos:[],simulado:true});estado.actividad=[{id:1,solicitudId:'ronda-1',tipo:'EVIDENCIA_RECHAZADA',detalle:'Rechazo',fecha:'2026-11-07T15:00:00Z'},{id:2,solicitudId:'ronda-2',tipo:'SOLICITUD_ENVIADA_SIMULADA',detalle:'Simulado',fecha:'2026-11-08T15:00:00Z'},{id:3,solicitudId:'otra',tipo:'SOLICITUD_CREADA',detalle:'Ajena',fecha:'2026-11-08T15:00:00Z'}];c.cargar();expect(c.actividad.map(a=>a.id)).toEqual([1,2]);});
  it('formatea el instante del servidor en hora de Chile',()=>{expect(c.fechaLocal('2026-11-08T13:45:00Z')).toContain('10:45');});

  it('una consulta sin respuesta termina en ERROR y permite recuperar Pendiente de solicitud sin enviar',fakeAsync(()=>{
    estado.solicitudes=[];api.estado.and.returnValue(new Subject<EstadoAutomatizacion>());c.solicitud=null;c.consultaRealizada=false;c.cargar();expect(c.estadoSolicitud).toBe('CARGANDO');tick(15000);expect(c.cargando).toBeFalse();expect(c.estadoSolicitud).toBe('ERROR');expect(c.errorConsulta).toContain('tardó demasiado');expect(api.enviar).not.toHaveBeenCalled();api.estado.and.returnValue(of(estado));c.cargar();expect(c.estadoSolicitud).toBe('PENDIENTE_SOLICITUD');expect(c.errorConsulta).toBe('');expect(api.enviar).not.toHaveBeenCalled();
  }));
  it('cambiar de paso cancela la lectura anterior y consulta el nuevo',()=>{
    const anterior=new Subject<EstadoAutomatizacion>();api.estado.and.returnValue(anterior);c.cargar();expect(anterior.observed).toBeTrue();api.estado.and.returnValue(of({...estado,solicitudes:[]}));c.paso={...c.paso,ejecucionPasoId:2};c.ngOnChanges();expect(anterior.observed).toBeFalse();expect(c.cargando).toBeFalse();expect(c.estadoSolicitud).toBe('PENDIENTE_SOLICITUD');
  });

  it('no vuelve a consultar por objetos equivalentes del padre ni sondea una solicitud inexistente',fakeAsync(()=>{estado.solicitudes=[];c.ngOnChanges();expect(c.consultaRealizada).toBeTrue();api.estado.calls.reset();for(let i=0;i<3;i++){c.paso={...c.paso};c.ngOnChanges();tick(20000);}expect(api.estado).not.toHaveBeenCalled();expect(c.estadoSolicitud).toBe('PENDIENTE_SOLICITUD');c.ngOnDestroy();}));
  it('un refresco no oculta Pendiente de solicitud ni lo convierte en una carga inicial',()=>{estado.solicitudes=[];c.cargar();api.estado.and.returnValue(new Subject<EstadoAutomatizacion>());c.cargar();expect(c.cargando).toBeTrue();expect(c.consultaRealizada).toBeTrue();expect(c.estadoSolicitud).toBe('PENDIENTE_SOLICITUD');});
  it('mantiene una sola consulta activa durante el sondeo de respuestas',fakeAsync(()=>{spyOnProperty(document,'visibilityState','get').and.returnValue('visible');c.ngOnChanges();api.estado.calls.reset();const lenta=new Subject<EstadoAutomatizacion>();api.estado.and.returnValue(lenta);tick(10000);expect(api.estado).toHaveBeenCalledTimes(1);expect(c.cargando).toBeTrue();c.ngOnDestroy();expect(lenta.observed).toBeFalse();}));

  function correoDirecto(){estado.solicitudes=[];c.paso={...c.paso,modoEjecucion:'ENVIAR_CORREO',esCritico:false};c.cargar();c.abrirCorreo();c.destinatarios=[{correo:'qa-directo@dimarsa.cl',tipo:'PRINCIPAL'},{correo:'qa-copia@dimarsa.cl',tipo:'COPIA'}];c.asunto='Prueba';c.mensaje='Mensaje de prueba';}
  it('correo directo permite editar sin sucursal ni consultar colaboradores',()=>{
    correoDirecto();expect(api.sucursales).not.toHaveBeenCalled();expect(api.destinatario).not.toHaveBeenCalled();
    api.enviar.and.returnValue(of({...solicitud,estado:'ENVIADA',mensajeEnviadoId:'DIMABUG-QA'}));c.enviarCorreo();
    const body=api.enviar.calls.mostRecent().args[2];expect(body.sucursalId).toBeUndefined();expect(body.destinatarios.length).toBe(2);expect(body.destinatarios[1].tipo).toBe('COPIA');
    expect(c.aviso).toContain('aceptó');expect(c.aviso).toContain('no está confirmada');
  });
  it('no confirma éxito al fallar SMTP y conserva un error comprensible',()=>{
    correoDirecto();api.enviar.and.returnValue(of({...solicitud,estado:'ERROR',mensajeEnviadoId:null,fechaEnvio:null}));c.enviarCorreo();expect(c.aviso).toBe('');expect(c.error).toContain('No se confirmó');expect(c.ocupado).toBeFalse();
  });
  it('un error HTTP mantiene el modal y no provoca un segundo envío',()=>{
    correoDirecto();api.enviar.and.returnValue(throwError(()=>new Error('Servicio SMTP no disponible')));c.enviarCorreo();expect(c.errorModal).toContain('SMTP');expect(c.modalAbierto).toBeTrue();expect(api.enviar).toHaveBeenCalledTimes(1);expect(c.ocupado).toBeFalse();
  });
  it('correo directo evita doble clic mientras espera',()=>{
    correoDirecto();api.enviar.and.returnValue(new Subject());c.enviarCorreo();c.enviarCorreo();expect(api.enviar).toHaveBeenCalledTimes(1);
  });
  it('solicita evidencias sin sucursal y conserva sus categorías y estado pendiente de revisión',()=>{
    pendiente();api.enviar.and.returnValue(of({...solicitud,contexto:{...solicitud.contexto,sucursalId:null,sucursalNombre:null,sucursalCodigo:null,responsableNombre:null}}));
    c.enviarCorreo();expect(api.enviar.calls.mostRecent().args[2].sucursalId).toBeUndefined();expect(c.categorias.length).toBe(3);expect(c.paso.cumplido).toBeFalse();
  });
});
