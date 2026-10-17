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
    admin = true; api = jasmine.createSpyObj('api', ['listar','contactos','guardar','estado','guardarContacto','estadoContacto']);
    api.listar.and.returnValue(of([d])); api.contactos.and.returnValue(of([contacto])); api.guardar.and.returnValue(of(d)); api.estado.and.returnValue(of({ ...d, activo: false }));
    api.guardarContacto.and.returnValue(of(contacto)); api.estadoContacto.and.returnValue(of(contacto));
    org = jasmine.createSpyObj('org', ['responsablesDeDepartamento','listarDepartamentosDetalle']); org.responsablesDeDepartamento.and.returnValue(of([]));
    org.listarDepartamentosDetalle.and.returnValue(of([{ ...d, contactos: [], responsables: [] }]));
    respaldo = { inspeccionar: () => [{ clave: 'antiguo' }], descargar: jasmine.createSpy() };
    await TestBed.configureTestingModule({ imports: [DepartamentosComponent], providers: [
      { provide: AdminOrganizacionApiService, useValue: api }, { provide: OrganizacionService, useValue: org },
      { provide: AuthService, useValue: { isAdmin: () => admin } }, { provide: RespaldoDepartamentosLocalService, useValue: respaldo } ] }).compileComponents();
    fixture = TestBed.createComponent(DepartamentosComponent); c = fixture.componentInstance; fixture.detectChanges();
  });
  it('carga datos HTTP administrativos y muestra editar', () => { expect(c.items[0].contactos[0].id).toBe(4); expect(fixture.nativeElement.textContent).toContain('Editar'); expect(api.listar).toHaveBeenCalled(); });
  it('técnico consulta operacionalmente y no puede escribir', () => { admin = false; api.listar.calls.reset(); c.cargar(); c.abrirDepartamento(); c.guardarDepartamento(); fixture.detectChanges();
    expect(org.listarDepartamentosDetalle).toHaveBeenCalledWith(true); expect(api.listar).not.toHaveBeenCalled(); expect(api.guardar).not.toHaveBeenCalled(); expect(fixture.nativeElement.textContent).not.toContain('Añadir Departamento'); });
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
});
