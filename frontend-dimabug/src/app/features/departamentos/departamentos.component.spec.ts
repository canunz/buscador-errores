import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DepartamentosComponent } from './departamentos.component';
import { AdminOrganizacionApiService } from '../../core/services/admin-organizacion-api.service';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { AuthService } from '../../core/services/auth.service';
import { RespaldoDepartamentosLocalService } from '../../core/services/respaldo-departamentos-local.service';
describe('Departamentos persistentes QA012', () => {
  let fixture: ComponentFixture<DepartamentosComponent>; let c: DepartamentosComponent; let admin: boolean; let api: any; let org: any; let respaldo: any;
  const d = { id: 1, nombre: 'TI', activo: true, version: 2 };
  const contacto = { id: 4, departamentoId: 1, tipo: 'Anexo', valor: '619', activo: true, departamentoActivo: true, disponible: true, version: 3 };
  beforeEach(async () => {
    admin = true; api = jasmine.createSpyObj('api', ['listar','contactos','guardar','estado','guardarContacto','estadoContacto','responsables','guardarResponsable','estadoResponsable']);
    api.listar.and.returnValue(of([d])); api.contactos.and.returnValue(of([contacto])); api.guardar.and.returnValue(of(d)); api.estado.and.returnValue(of({ ...d, activo: false }));
    api.responsables.and.returnValue(of([])); api.guardarResponsable.and.returnValue(of({})); api.estadoResponsable.and.returnValue(of({}));
    api.guardarContacto.and.returnValue(of(contacto)); api.estadoContacto.and.returnValue(of(contacto));
    org = jasmine.createSpyObj('org', ['responsablesDeDepartamento','listarDepartamentosDetalle','listarDepartamentos','contactosDeDepartamento']); org.responsablesDeDepartamento.and.returnValue(of([]));
    org.listarDepartamentos.and.returnValue(of([d])); org.contactosDeDepartamento.and.returnValue(of([]));
    org.listarDepartamentosDetalle.and.returnValue(of([{ ...d, contactos: [], responsables: [] }]));
    respaldo = { inspeccionar: () => [{ clave: 'antiguo' }], descargar: jasmine.createSpy() };
    await TestBed.configureTestingModule({ imports: [DepartamentosComponent], providers: [
      { provide: AdminOrganizacionApiService, useValue: api }, { provide: OrganizacionService, useValue: org },
      { provide: AuthService, useValue: { isAdmin: () => admin } }, { provide: RespaldoDepartamentosLocalService, useValue: respaldo } ] }).compileComponents();
    fixture = TestBed.createComponent(DepartamentosComponent); c = fixture.componentInstance; fixture.detectChanges();
  });
  it('carga datos HTTP administrativos y muestra editar', () => { expect(c.items[0].contactos[0].id).toBe(4); expect(fixture.nativeElement.textContent).toContain('Editar'); expect(api.listar).toHaveBeenCalled(); });
  it('técnico consulta operacionalmente y no puede escribir', () => { admin = false; api.listar.calls.reset(); c.cargar(); c.abrirDepartamento(); c.guardarDepartamento(); fixture.detectChanges();
    expect(org.listarDepartamentos).toHaveBeenCalledWith(true); expect(api.listar).not.toHaveBeenCalled(); expect(api.guardar).not.toHaveBeenCalled(); expect(fixture.nativeElement.textContent).not.toContain('Añadir Departamento'); });
  it('rechaza nombre vacío sin HTTP', () => { c.abrirDepartamento(); c.departamentoForm.setValue({ nombre: '  ' }); c.guardarDepartamento(); expect(api.guardar).not.toHaveBeenCalled(); expect(c.formError).toContain('obligatorio'); });
  it('edita con versión y recarga desde servidor', () => { c.abrirDepartamento(c.items[0]); c.departamentoForm.setValue({ nombre: ' Soporte ' }); c.guardarDepartamento();
    expect(api.guardar).toHaveBeenCalledWith(1, 'Soporte', 2); expect(api.listar.calls.count()).toBe(2); expect(c.formularioDepartamento).toBeFalse(); });
  it('conserva formulario y listado si el servidor rechaza', () => { api.guardar.and.returnValue(throwError(() => new Error('409: nombre duplicado'))); c.abrirDepartamento(); c.departamentoForm.setValue({ nombre: 'TI' }); c.guardarDepartamento();
    expect(c.formularioDepartamento).toBeTrue(); expect(c.formError).toContain('duplicado'); expect(c.items.length).toBe(1); });
  it('desactiva persistentemente y refresca también detalle', () => { c.ver(c.items[0]); api.listar.and.returnValue(of([{ ...d, activo: false, version: 3 }])); c.pedirEstado(c.items[0]); c.confirmarEstado();
    expect(api.estado).toHaveBeenCalledWith(1, false, 2); expect(c.detalle?.activo).toBeFalse(); expect(c.items.length).toBe(1); });
  it('no oculta filas ante un conflicto de estado', () => { api.estado.and.returnValue(throwError(() => new Error('Registro cambió'))); c.pedirEstado(c.items[0]); c.confirmarEstado();
    expect(c.items[0].activo).toBeTrue(); expect(c.pendienteEstado).not.toBeNull(); expect(c.formError).toContain('cambió'); });
  it('reactiva departamento conservando listado', () => { c.items[0].activo = false; c.pedirEstado(c.items[0]); c.confirmarEstado(); expect(api.estado).toHaveBeenCalledWith(1, true, 2); });
  it('nuevo contacto bajo departamento inactivo parte inactivo', () => { c.items[0].activo = false; c.abrirContacto(c.items[0]); expect(c.contactoForm.controls.activo.value).toBeFalse();
    c.contactoForm.patchValue({ tipo: 'Correo', valor: 'a@b.cl' }); c.guardarContacto(); expect(api.guardarContacto).toHaveBeenCalledWith(1, null, jasmine.objectContaining({ activo: false })); });
  it('edita contacto con ID y versión y recarga', () => { c.abrirContacto(c.items[0], contacto); c.contactoForm.controls.valor.setValue('620'); c.guardarContacto();
    expect(api.guardarContacto).toHaveBeenCalledWith(1, 4, jasmine.objectContaining({ valor: '620', version: 3 })); expect(api.listar.calls.count()).toBe(2); });
  it('estado de contacto usa endpoint persistente', () => { c.pedirEstado(c.items[0], contacto); c.confirmarEstado(); expect(api.estadoContacto).toHaveBeenCalledWith(1, 4, false, 3); });
  it('errores de contactos se muestran como error y no catálogo vacío válido', () => { api.contactos.and.returnValue(throwError(() => new Error('Consulta falló'))); c.cargar(); fixture.detectChanges();
    expect(c.items[0].errorDetalle).toContain('falló'); expect(fixture.nativeElement.textContent).toContain('Consulta falló'); });
  it('permite respaldo sin importación automática', () => { expect(respaldo.descargar).not.toHaveBeenCalled(); c.respaldarDatosLocales(); expect(respaldo.descargar).toHaveBeenCalled(); });

  it('muestra acciones de contactos y responsables dentro del detalle', () => {
    c.ver(c.items[0]); fixture.detectChanges(); const texto=fixture.nativeElement.textContent;
    expect(texto).toContain('Añadir Contacto'); expect(texto).toContain('Editar Contacto'); expect(texto).toContain('Desactivar Contacto'); expect(texto).toContain('Añadir responsable');
  });
  it('ANEXO conserva ceros iniciales como texto', () => {
    c.abrirContacto(c.items[0]); c.contactoForm.patchValue({ tipo: 'ANEXO', valor: '00619' }); c.guardarContacto();
    expect(api.guardarContacto).toHaveBeenCalledWith(1,null,jasmine.objectContaining({ tipo: 'ANEXO',valor: '00619' }));
  });
  it('rechaza anexo no numérico', () => { c.abrirContacto(c.items[0]); c.contactoForm.patchValue({ tipo: 'ANEXO', valor: 'abc' }); c.guardarContacto(); expect(api.guardarContacto).not.toHaveBeenCalled(); });
  it('valida correo y cambia etiqueta y ejemplo', () => {
    c.abrirContacto(c.items[0]); c.contactoForm.patchValue({ tipo: 'CORREO', valor: 'sin-correo' }); c.guardarContacto();
    expect(api.guardarContacto).not.toHaveBeenCalled(); expect(c.etiquetaValor).toContain('Correo'); expect(c.ejemploValor).toContain('@');
    c.contactoForm.controls.valor.setValue('qa@example.invalid'); c.guardarContacto(); expect(api.guardarContacto).toHaveBeenCalled();
  });
  it('teléfono permite formato internacional sin convertir a número', () => {
    c.abrirContacto(c.items[0]); c.contactoForm.patchValue({ tipo: 'TELEFONO', valor: '+56 2 2345 6789' }); c.guardarContacto();
    expect(api.guardarContacto).toHaveBeenCalledWith(1,null,jasmine.objectContaining({ valor: '+56 2 2345 6789' }));
  });
  it('rechaza teléfono corto y letras', () => {
    c.abrirContacto(c.items[0]); c.contactoForm.patchValue({ tipo: 'TELEFONO', valor: '123' }); c.guardarContacto();
    c.contactoForm.controls.valor.setValue('abcdef123456789'); c.guardarContacto(); expect(api.guardarContacto).not.toHaveBeenCalled();
  });
  it('OTRO acepta texto de contacto', () => { c.abrirContacto(c.items[0]); c.contactoForm.patchValue({ tipo: 'OTRO', valor: 'Mesa central' }); expect(c.contactoForm.valid).toBeTrue(); });
  it('conserva literalmente el tipo histórico y su opción', () => {
    const historico={ ...contacto, tipo: ' Anexo antiguo ', valor: '61-A' }; c.abrirContacto(c.items[0],historico); fixture.detectChanges();
    expect(c.tipoHistorico).toBe(' Anexo antiguo '); expect(fixture.nativeElement.textContent).toContain('(histórico)');
    c.guardarContacto(); expect(api.guardarContacto).toHaveBeenCalledWith(1,4,jasmine.objectContaining({ tipo: ' Anexo antiguo ' }));
  });
  it('preserva formulario de contacto ante conflicto sin repetir solicitud', () => {
    api.guardarContacto.and.returnValue(throwError(() => new Error('409: versión obsoleta'))); c.abrirContacto(c.items[0],contacto); c.guardarContacto();
    expect(c.formularioContacto).not.toBeNull(); expect(c.formError).toContain('obsoleta'); expect(api.guardarContacto.calls.count()).toBe(1);
  });
  it('consulta responsables también en departamentos inactivos', () => {
    api.listar.and.returnValue(of([{ ...d, activo: false }])); const r={ id: 8, departamentoId:1,nombre:'Ana',cargo:'TI',contacto:'00123',activo:false,revision:'a'.repeat(64) };
    api.responsables.and.returnValue(of([r])); c.cargar(); c.ver(c.items[0]); fixture.detectChanges();
    expect(api.responsables).toHaveBeenCalledWith(1); expect(fixture.nativeElement.textContent).toContain('Ana'); expect(fixture.nativeElement.textContent).toContain('00123');
  });
  it('crea responsable y refresca el detalle', () => {
    c.ver(c.items[0]); c.abrirResponsable(c.items[0]); c.responsableForm.setValue({ nombre:' Ana ',cargo:'TI',contacto:'00123' }); c.guardarResponsable();
    expect(api.guardarResponsable).toHaveBeenCalledWith(1,null,{nombre:'Ana',cargo:'TI',contacto:'00123'}); expect(c.formularioResponsable).toBeNull(); expect(api.listar.calls.count()).toBe(2);
  });
  it('edita responsable con su huella original y no cambia su departamento', () => {
    const r={id:8,departamentoId:1,nombre:'Ana',cargo:'TI',contacto:'00123',activo:true,revision:'a'.repeat(64)};
    c.abrirResponsable(c.items[0],r); c.responsableForm.controls.nombre.setValue('Bea'); c.guardarResponsable();
    expect(api.guardarResponsable).toHaveBeenCalledWith(1,r,{nombre:'Bea',cargo:'TI',contacto:'00123'});
  });
  it('conflicto de responsable conserva formulario y permite recarga explícita', () => {
    api.guardarResponsable.and.returnValue(throwError(() => new Error('409: cambió en otra sesión'))); c.abrirResponsable(c.items[0]); c.responsableForm.controls.nombre.setValue('Ana'); c.guardarResponsable();
    expect(c.formularioResponsable).not.toBeNull(); expect(c.formError).toContain('otra sesión'); expect(api.guardarResponsable.calls.count()).toBe(1);
    c.descartarYRecargar(); expect(c.formularioResponsable).toBeNull(); expect(api.listar.calls.count()).toBe(2);
  });
  it('estado de responsable requiere confirmación y conserva huella', () => {
    const r={id:8,departamentoId:1,nombre:'Ana',activo:true,revision:'a'.repeat(64)};
    c.pedirEstado(c.items[0],undefined,r); expect(api.estadoResponsable).not.toHaveBeenCalled(); fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Desactivar Responsable');
    c.confirmarEstado(); expect(api.estadoResponsable).toHaveBeenCalledWith(r,false);
  });
  it('no ofrece crear responsable en departamento inactivo', () => { c.items[0].activo=false; c.abrirResponsable(c.items[0]); expect(c.formularioResponsable).toBeNull(); });
  it('técnico no puede administrar responsables ni contactos mediante métodos', () => {
    admin=false; c.abrirResponsable(c.items[0]); c.guardarResponsable(); c.abrirContacto(c.items[0]); c.guardarContacto(); c.pedirEstado(c.items[0]);
    expect(c.formularioResponsable).toBeNull(); expect(c.formularioContacto).toBeNull(); expect(api.guardarResponsable).not.toHaveBeenCalled(); expect(api.guardarContacto).not.toHaveBeenCalled(); expect(c.pendienteEstado).toBeNull();
  });
  it('consulta operacional conserva cargo y contacto para técnico', () => {
    admin=false; org.responsablesDeDepartamento.and.returnValue(of([{id:8,nombre:'Ana',cargo:'TI',contacto:'00123'}])); c.cargar(); c.ver(c.items[0]); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('TI'); expect(fixture.nativeElement.textContent).toContain('00123'); expect(fixture.nativeElement.textContent).not.toContain('Añadir responsable');
  });
  it('limita el departamento a 100 caracteres antes de HTTP', () => {
    c.abrirDepartamento(); c.departamentoForm.controls.nombre.setValue('D'.repeat(100));
    expect(c.departamentoForm.controls.nombre.valid).toBeTrue();
    c.departamentoForm.controls.nombre.setValue('D'.repeat(101)); c.guardarDepartamento();
    expect(c.departamentoForm.controls.nombre.hasError('maxlength')).toBeTrue();
    expect(api.guardar).not.toHaveBeenCalled();
  });

});
