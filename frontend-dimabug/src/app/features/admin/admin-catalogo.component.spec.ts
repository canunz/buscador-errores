import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AdminCatalogoComponent } from './admin-catalogo.component';
import { CatalogoAdmin, AdminCatalogosApiService } from '../../core/services/admin-catalogos-api.service';
describe('Administración persistente', () => {
  let api: jasmine.SpyObj<AdminCatalogosApiService>;
  beforeEach(() => {
    api = jasmine.createSpyObj('api', ['listar', 'guardar', 'cambiarEstado']);
    api.listar.and.returnValue(of([]));
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AdminCatalogosApiService, useValue: api },
      { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ tipo: 'hardware' })) } }] });
  });
  it('consulta API al entrar y después de guardar, sin maestros locales', () => {
    const c = TestBed.createComponent(AdminCatalogoComponent).componentInstance; c.ngOnInit();
    c.abrir(); c.form.controls.nombre.setValue('POS');
    api.guardar.and.returnValue(of({ id: 1, nombre: 'POS', activo: true, sistemaIds: [], sistemaId: null, descripcion: null, sistemaOperativo: null, orden: null }));
    c.guardar(); expect(api.guardar).toHaveBeenCalled(); expect(api.listar.calls.count()).toBe(4); expect(c.modalOpen).toBeFalse();
  });
  it('conserva formulario y muestra el error del servidor', () => {
    const c = TestBed.createComponent(AdminCatalogoComponent).componentInstance; c.abrir(); c.form.controls.nombre.setValue('POS');
    api.guardar.and.returnValue(throwError(() => new Error('Duplicado'))); c.guardar();
    expect(c.formError).toBe('Duplicado'); expect(c.modalOpen).toBeTrue(); expect(c.saving).toBeFalse();
  });
  const sistema = (id: number, activo: boolean): CatalogoAdmin => ({ id, activo, nombre: `Sistema ${id}`, sistemaIds: [], sistemaId: null, descripcion: null, sistemaOperativo: null, orden: null });
  const hardware: CatalogoAdmin = { id: 1, nombre: 'POS', activo: true, sistemaIds: [2, 3], sistemaId: null, descripcion: null, sistemaOperativo: 'Windows', orden: null };
  function formulario() {
    api.listar.and.callFake(tipo => of(tipo === 'sistemas' ? [sistema(2, true), sistema(3, false), sistema(4, false), sistema(5, true)] : [hardware]));
    const fixture = TestBed.createComponent(AdminCatalogoComponent);
    fixture.detectChanges(); fixture.componentInstance.abrir(hardware); fixture.detectChanges(); return fixture;
  }
  const checks = (fixture: ReturnType<typeof formulario>) => Array.from(fixture.nativeElement.querySelectorAll('.hw-admin-systems input[type="checkbox"]')) as HTMLInputElement[];
  it('QA-010 muestra selecciones existentes y sección identificable', () => {
    const f = formulario(); const inputs = checks(f);
    expect(inputs[0].checked).toBeTrue(); expect(inputs[1].checked).toBeTrue();
    expect(f.nativeElement.querySelector('legend').textContent).toContain('Sistemas asociados');
    expect(f.nativeElement.querySelector('#hw-sistemas-ayuda').textContent).toContain('uno o varios');
    expect(f.componentInstance.form.controls.sistemaIds.value).toEqual([2, 3]);
  });
  it('QA-010 permite desmarcar y remarcar inactivo originalmente asociado', () => {
    const f = formulario(); const input = checks(f)[1];
    expect(input.disabled).toBeFalse(); input.click(); f.detectChanges();
    expect(input.checked).toBeFalse(); expect(input.disabled).toBeFalse();
    input.click(); f.detectChanges(); expect(input.checked).toBeTrue();
    expect(f.componentInstance.form.controls.sistemaIds.value).toContain(3);
    expect(hardware.sistemaIds).toEqual([2, 3]);
  });
  it('QA-010 bloquea inactivo nuevo incluso mediante llamada directa', () => {
    const f = formulario(); expect(checks(f)[2].disabled).toBeTrue();
    f.componentInstance.asociar(4, { target: { checked: true } } as unknown as Event);
    expect(f.componentInstance.form.controls.sistemaIds.value).not.toContain(4);
  });
  it('QA-010 conserva selección múltiple y transmite lista completa', () => {
    const f = formulario(); const c = f.componentInstance; checks(f)[3].click(); f.detectChanges();
    expect(c.form.controls.sistemaIds.value).toEqual([2, 3, 5]);
    api.guardar.and.returnValue(of({ ...hardware, sistemaIds: [2, 3, 5] })); c.guardar();
    expect(api.guardar.calls.mostRecent().args[2].sistemaIds).toEqual([2, 3, 5]);
    expect(api.guardar.calls.mostRecent().args[0]).toBe('hardware');
  });
  it('QA-010 lista vacía se envía explícitamente y eventos repetidos no duplican', () => {
    const f = formulario(); const c = f.componentInstance;
    c.asociar(2, { target: { checked: true } } as unknown as Event);
    expect(c.form.controls.sistemaIds.value.filter(id => id === 2).length).toBe(1);
    checks(f)[0].click(); checks(f)[1].click(); f.detectChanges();
    api.guardar.and.returnValue(of({ ...hardware, sistemaIds: [] })); c.guardar();
    expect(api.guardar.calls.mostRecent().args[2].sistemaIds).toEqual([]);
  });
  it('QA-010 abrir nuevo formulario no hereda permiso sobre inactivos', () => {
    const f = formulario(); f.componentInstance.abrir(); f.detectChanges();
    expect(checks(f)[1].disabled).toBeTrue(); expect(checks(f)[2].disabled).toBeTrue();
    expect(f.componentInstance.form.controls.sistemaIds.value).toEqual([]);
  });
  it('frecuencias acepta 50 y bloquea guardar con 51 caracteres', () => {
    const c = TestBed.createComponent(AdminCatalogoComponent).componentInstance;
    c.tipo = 'frecuencias'; c.abrir();
    c.form.controls.nombre.setValue('F'.repeat(50)); expect(c.form.controls.nombre.valid).toBeTrue();
    c.form.controls.nombre.setValue('F'.repeat(51)); expect(c.form.controls.nombre.hasError('maxlength')).toBeTrue();
    c.guardar(); expect(api.guardar).not.toHaveBeenCalled();
  });
});
