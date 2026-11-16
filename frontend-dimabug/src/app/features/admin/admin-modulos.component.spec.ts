import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AdminCatalogoComponent } from './admin-catalogo.component';
import { AdminCatalogosApiService, CatalogoAdmin } from '../../core/services/admin-catalogos-api.service';

describe('QA-011 Módulos administrativos', () => {
  let api: jasmine.SpyObj<AdminCatalogosApiService>;
  const registro = (id: number, activo: boolean, sistemaId: number | null): CatalogoAdmin => ({
    id, nombre: `Registro ${id}`, activo, sistemaId, sistemaIds: [], descripcion: null, sistemaOperativo: null, orden: null,
  });
  const sistemas = [registro(2, true, null), registro(3, false, null), registro(4, false, null)];
  const modulos = [registro(10, true, 2), registro(11, false, 2), registro(12, true, 3), registro(13, false, 3)];
  beforeEach(() => {
    api = jasmine.createSpyObj('api', ['listar', 'guardar', 'cambiarEstado']);
    api.listar.and.callFake(tipo => of(tipo === 'sistemas' ? sistemas : modulos));
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ tipo: 'modulos' })) } },
      { provide: AdminCatalogosApiService, useValue: api }] });
  });
  function vista() { const f = TestBed.createComponent(AdminCatalogoComponent); f.detectChanges(); return f; }
  it('lista inicialmente todos y muestra contador', () => {
    const f = vista(); expect(f.componentInstance.itemsVisibles.length).toBe(4);
    expect(f.nativeElement.querySelector('.modulos-resultados').textContent).toContain('4 de 4');
  });
  it('filtra por Sistema sin nuevas peticiones HTTP', () => {
    const f = vista(); f.componentInstance.filtrosModulos.controls.sistemaId.setValue(2); f.detectChanges();
    expect(f.componentInstance.itemsVisibles.map(m => m.id)).toEqual([10, 11]);
    expect(api.listar.calls.count()).toBe(2);
    expect(f.nativeElement.querySelector('.modulos-resultados').textContent).toContain('2 de 4');
  });
  it('filtra activos e inactivos individualmente', () => {
    const c = vista().componentInstance;
    c.filtrosModulos.controls.estado.setValue('activos'); expect(c.itemsVisibles.map(m => m.id)).toEqual([10, 12]);
    c.filtrosModulos.controls.estado.setValue('inactivos'); expect(c.itemsVisibles.map(m => m.id)).toEqual([11, 13]);
  });
  it('combina filtros y permite Sistemas padres inactivos en el filtro', () => {
    const c = vista().componentInstance; c.filtrosModulos.setValue({ sistemaId: 3, estado: 'inactivos' });
    expect(c.itemsVisibles.map(m => m.id)).toEqual([13]); expect(c.items.length).toBe(4);
  });
  it('limpia ambos filtros mediante botón', () => {
    const f = vista(); f.componentInstance.filtrosModulos.setValue({ sistemaId: 3, estado: 'activos' }); f.detectChanges();
    f.nativeElement.querySelector('.modulos-filtros button').click(); f.detectChanges();
    expect(f.componentInstance.filtrosModulos.getRawValue()).toEqual({ sistemaId: null, estado: 'todos' });
    expect(f.componentInstance.itemsVisibles.length).toBe(4);
  });
  it('informa cero coincidencias sin confundirlo con catálogo vacío', () => {
    const f = vista(); f.componentInstance.filtrosModulos.controls.sistemaId.setValue(4); f.detectChanges();
    expect(f.nativeElement.querySelector('.vacio').textContent).toContain('coincidan con los filtros');
    expect(f.nativeElement.querySelector('.modulos-resultados').textContent).toContain('0 de 4');
  });
  it('precarga Sistema asociado y permite volver al inactivo original', () => {
    const f = vista(); const c = f.componentInstance; c.abrir(modulos[2]); f.detectChanges();
    const select = f.nativeElement.querySelector('select[formControlName="sistemaId"][required]') as HTMLSelectElement;
    expect(c.form.controls.sistemaId.value).toBe(3); expect(select.selectedOptions[0].textContent).toContain('Registro 3');
    c.form.controls.sistemaId.setValue(2); f.detectChanges();
    expect(select.options[2].disabled).toBeFalse(); select.selectedIndex = 2; select.dispatchEvent(new Event('change')); f.detectChanges();
    expect(c.form.controls.sistemaId.value).toBe(3); expect(select.options[3].disabled).toBeTrue();
  });
  it('conserva Sistema inactivo original al guardar', () => {
    const f = vista(); const c = f.componentInstance; c.abrir(modulos[2]);
    api.guardar.and.returnValue(of(modulos[2])); c.guardar();
    expect(api.guardar.calls.mostRecent().args[2].sistemaId).toBe(3);
  });
  it('bloquea otros inactivos incluso con modificación programática', () => {
    const c = vista().componentInstance; c.abrir(modulos[2]); c.form.controls.sistemaId.setValue(4); c.guardar();
    expect(api.guardar).not.toHaveBeenCalled(); expect(c.formError).toBeTruthy();
  });
  it('crear nuevo reinicia excepción del Sistema original', () => {
    const f = vista(); const c = f.componentInstance; c.abrir(modulos[2]); c.abrir(); f.detectChanges();
    expect(c.puedeElegirSistemaModulo(sistemas[1])).toBeFalse(); expect(c.form.controls.sistemaId.value).toBeNull();
    const select = f.nativeElement.querySelector('select[formControlName="sistemaId"][required]') as HTMLSelectElement;
    expect(select.options[2].disabled).toBeTrue(); expect(select.options[3].disabled).toBeTrue();
  });
  it('mantiene validación de nombre y Sistema obligatorio', () => {
    const c = vista().componentInstance; c.abrir(); c.guardar(); expect(api.guardar).not.toHaveBeenCalled();
    c.form.patchValue({ nombre: ' '.repeat(2), sistemaId: 2 }); c.guardar(); expect(api.guardar).not.toHaveBeenCalled();
    c.form.patchValue({ nombre: 'x'.repeat(121) }); expect(c.form.invalid).toBeTrue();
  });
  it('filtros no afectan Hardware ni otros catálogos', () => {
    const f = vista(); const c = f.componentInstance; c.filtrosModulos.setValue({ sistemaId: 4, estado: 'inactivos' });
    for (const tipo of ['hardware', 'sistemas', 'frecuencias'] as const) {
      c.tipo = tipo; f.detectChanges(); expect(c.itemsVisibles).toBe(c.items);
      expect(f.nativeElement.querySelector('.modulos-filtros')).toBeNull();
    }
  });
});
