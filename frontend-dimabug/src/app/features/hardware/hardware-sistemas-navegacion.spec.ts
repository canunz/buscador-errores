import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of, throwError } from 'rxjs';
import { HardwareComponent } from './hardware.component';
import { AdminCatalogoComponent } from '../admin/admin-catalogo.component';
import { AdminCatalogosApiService, CatalogoAdmin } from '../../core/services/admin-catalogos-api.service';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { AuthService } from '../../core/services/auth.service';
import { adminGuard, staffGuard } from '../../core/guards/auth.guard';

@Component({ standalone: true, template: 'Inicio' })
class InicioPrueba {}

describe('Navegación integrada Hardware → Sistemas → Módulos', () => {
  let api: jasmine.SpyObj<AdminCatalogosApiService>;
  let clasificacion: jasmine.SpyObj<ClasificacionService>;
  let admin: boolean;
  let sistemas: CatalogoAdmin[];
  let hardwares: CatalogoAdmin[];
  let modulos: CatalogoAdmin[];
  const registro = (id: number, nombre: string, extra: Partial<CatalogoAdmin> = {}): CatalogoAdmin => ({
    id, nombre, activo: true, sistemaOperativo: null, descripcion: null,
    sistemaId: null, orden: null, sistemaIds: [], ...extra,
  });
  beforeEach(() => {
    admin = true;
    sistemas = [registro(2, 'Mismo nombre'), registro(3, 'Inactivo', { activo: false }), registro(4, 'Mismo nombre'), registro(5, 'Sin Hardware')];
    hardwares = [registro(10, 'POS', { sistemaOperativo: 'Linux', sistemaIds: [2, 3, 4] }), registro(11, 'Servidor', { activo: false, sistemaIds: [2] })];
    modulos = [registro(20, 'Caja', { sistemaId: 2 }), registro(21, 'Otro', { sistemaId: 4 }), registro(22, 'Antiguo', { sistemaId: 2, activo: false })];
    api = jasmine.createSpyObj('api', ['listar', 'obtener', 'guardar', 'cambiarEstado']);
    api.listar.and.callFake(tipo => of(tipo === 'hardware' ? hardwares : tipo === 'sistemas' ? sistemas : tipo === 'modulos' ? modulos : []));
    api.obtener.and.callFake((_, id) => of(hardwares.find(h => h.id === id)!));
    api.guardar.and.callFake((tipo, id, data) => of({ id: id ?? 30, ...data }));
    api.cambiarEstado.and.callFake((_, id, activo) => of(registro(id, 'Modificado', { activo })));
    clasificacion = jasmine.createSpyObj('clasificacion', ['hardwareEnSesion', 'listarHardwareDetalle', 'sistemasDeHardware']);
    clasificacion.hardwareEnSesion.and.returnValue([]);
    clasificacion.listarHardwareDetalle.and.callFake(() => of(hardwares.filter(h => h.activo).map(h => ({ id: h.id, nombre: h.nombre, so: h.sistemaOperativo ?? '', sistemas: ['nombre antiguo sin ID'] }))));
    clasificacion.sistemasDeHardware.and.returnValue(of([{ id: 2, nombre: 'Consulta técnica' }]));
    TestBed.configureTestingModule({ providers: [
      provideRouter([
        { path: 'plataforma/hardware', component: HardwareComponent, canActivate: [staffGuard] },
        { path: 'admin/catalogo/:tipo', component: AdminCatalogoComponent, canActivate: [adminGuard] },
        { path: 'inicio', component: InicioPrueba },
      ]),
      { provide: AdminCatalogosApiService, useValue: api },
      { provide: ClasificacionService, useValue: clasificacion },
      { provide: AuthService, useValue: { isAdmin: () => admin, isStaff: () => true, isAuthenticated: () => true } },
    ] });
  });
  function link(h: RouterTestingHarness, text: string): HTMLAnchorElement {
    const visible = h.routeNativeElement!.querySelector('.modal') ?? h.routeNativeElement!;
    return Array.from(visible.querySelectorAll<HTMLAnchorElement>('a')).find(a => a.textContent!.includes(text))!;
  }
  async function seguir(h: RouterTestingHarness, a: HTMLAnchorElement) {
    a.click(); await h.fixture.whenStable(); h.detectChanges();
  }
  async function formulario(url: string) {
    const h = await RouterTestingHarness.create();
    const c = await h.navigateByUrl(url, AdminCatalogoComponent); h.detectChanges();
    return { h, c };
  }
  it('navega por ID aunque dos sistemas tengan igual nombre y retorna al Hardware actualizado', async () => {
    const h = await RouterTestingHarness.create();
    const hw = await h.navigateByUrl('/plataforma/hardware', HardwareComponent);
    hw.ver(hw.items[0]); h.detectChanges();
    const acciones = h.routeNativeElement!.querySelectorAll<HTMLAnchorElement>('.sistema-accion');
    expect(acciones.length).toBe(3); expect(acciones[2].getAttribute('href')).toContain('editarId=4');
    expect(h.routeNativeElement!.querySelector('.hw-so')!.textContent).toContain('Linux');
    await seguir(h, acciones[2]);
    const sistema = h.routeDebugElement!.componentInstance as AdminCatalogoComponent;
    expect(sistema.editId).toBe(4); expect(sistema.form.controls.nombre.value).toBe('Mismo nombre');
    await seguir(h, link(h, 'Módulos de este Sistema'));
    const mods = h.routeDebugElement!.componentInstance as AdminCatalogoComponent;
    expect(mods.itemsVisibles.map(m => m.id)).toEqual([21]); expect(mods.hardwareOrigenId).toBe(10);
    mods.abrir(); expect(mods.form.controls.sistemaId.value).toBe(4);
    hardwares[0] = { ...hardwares[0], nombre: 'POS actualizado' };
    await seguir(h, link(h, 'Volver al Hardware'));
    const regreso = h.routeDebugElement!.componentInstance as HardwareComponent;
    expect(regreso.detalle?.id).toBe(10); expect(regreso.detalle?.nombre).toBe('POS actualizado');
    expect(clasificacion.listarHardwareDetalle).toHaveBeenCalledWith(true);
  });
  it('TECNICO consulta sin acciones administrativas ni peticiones al API de administración', async () => {
    admin = false;
    const h = await RouterTestingHarness.create(); const hw = await h.navigateByUrl('/plataforma/hardware', HardwareComponent);
    hw.ver(hw.items[0]); h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain('Consulta técnica');
    expect(h.routeNativeElement!.querySelector('a')).toBeNull();
    expect(api.obtener).not.toHaveBeenCalled(); expect(api.listar).not.toHaveBeenCalled();
    await h.navigateByUrl('/admin/catalogo/sistemas?editarId=2&hardwareId=10');
    expect(TestBed.inject(Router).url).toBe('/inicio');
  });
  it('advierte al editar y desactivar un sistema compartido, contando Hardware inactivo', async () => {
    const { h, c } = await formulario('/admin/catalogo/sistemas?editarId=2&hardwareId=10');
    expect(h.routeNativeElement!.querySelector('.modal .catalogo-advertencia')!.textContent).toContain('2 Hardware');
    c.modalOpen = false; c.pendienteEstado = sistemas[0]; h.detectChanges();
    expect(h.routeNativeElement!.querySelector('.confirm-dialog .catalogo-advertencia')!.textContent).toContain('2 Hardware');
    c.confirmarEstado(); expect(api.cambiarEstado).toHaveBeenCalledWith('sistemas', 2, false);
  });
  it('mantiene el listado global y permite abrir un sistema sin Hardware', async () => {
    const { h, c } = await formulario('/admin/catalogo/sistemas?editarId=5');
    expect(c.items.map(i => i.id)).toContain(5); expect(c.editId).toBe(5);
    expect(c.hardwareDelSistema(5)).toEqual([]);
    expect(h.routeNativeElement!.querySelector('.modal .catalogo-advertencia')).toBeNull();
    c.modalOpen = false; h.detectChanges(); await seguir(h, link(h, 'Todos los Sistemas'));
    expect((h.routeDebugElement!.componentInstance as AdminCatalogoComponent).items.length).toBe(4);
  });
  it('un ID inexistente no abre un sistema por coincidencia de nombre', async () => {
    const { c } = await formulario('/admin/catalogo/sistemas?editarId=999&hardwareId=10');
    expect(c.modalOpen).toBeFalse(); expect(c.error).toContain('no está disponible');
    expect(api.guardar).not.toHaveBeenCalled();
  });
  it('conserva filtro contextual, preselecciona nuevos y rechaza traslado programático de un módulo existente', async () => {
    const { c } = await formulario('/admin/catalogo/modulos?sistemaId=2&hardwareId=10');
    c.limpiarFiltrosModulos(); expect(c.itemsVisibles.map(m => m.id)).toEqual([20, 22]);
    c.abrir(); expect(c.form.controls.sistemaId.value).toBe(2); expect(c.form.controls.sistemaId.disabled).toBeTrue();
    c.abrir(modulos[0]); c.form.controls.sistemaId.setValue(4); c.guardar();
    expect(api.guardar).not.toHaveBeenCalled(); expect(c.formError).toContain('trasladar');
    c.form.controls.sistemaId.setValue(2); c.form.controls.nombre.setValue('Caja editada'); c.guardar();
    expect(api.guardar.calls.mostRecent().args[2].sistemaId).toBe(2);
    expect(c.itemsVisibles.map(m => m.id)).toEqual([20, 22]);
    c.pendienteEstado = modulos[0]; c.confirmarEstado();
    expect(api.cambiarEstado).toHaveBeenCalledWith('modulos', 20, false);
  });
  it('salir a Todos los Módulos restaura el filtro global', async () => {
    const { h } = await formulario('/admin/catalogo/modulos?sistemaId=2&hardwareId=10');
    await seguir(h, link(h, 'Todos los Módulos'));
    const c = h.routeDebugElement!.componentInstance as AdminCatalogoComponent;
    expect(c.itemsVisibles.length).toBe(3); expect(c.filtrosModulos.controls.sistemaId.enabled).toBeTrue();
  });
  it('crea módulos en el sistema contextual y rechaza otros padres incluso con manipulación del formulario', async () => {
    const { c } = await formulario('/admin/catalogo/modulos?sistemaId=2&hardwareId=10');
    c.abrir(); c.form.controls.nombre.setValue('Módulo nuevo');
    c.form.controls.sistemaId.setValue(4); c.guardar(); expect(api.guardar).not.toHaveBeenCalled();
    c.form.controls.sistemaId.setValue(2); c.guardar();
    expect(api.guardar.calls.mostRecent().args).toEqual(['modulos', null, jasmine.objectContaining({ nombre: 'Módulo nuevo', sistemaId: 2 })]);
    expect(c.itemsVisibles.map(m => m.sistemaId)).toEqual([2, 2]);
  });
  it('la edición contextual de Hardware guarda asociaciones activas e inactivas completas y permite desasociar', async () => {
    const { c } = await formulario('/admin/catalogo/hardware?editarId=10&hardwareId=10');
    c.form.controls.nombre.setValue('POS editado'); c.guardar();
    expect(api.guardar.calls.mostRecent().args[2].sistemaIds).toEqual([2, 3, 4]);
    c.abrir(hardwares[0]); c.asociar(2, { target: { checked: false } } as unknown as Event); c.guardar();
    expect(api.guardar.calls.mostRecent().args[2].sistemaIds).toEqual([3, 4]);
    expect(api.cambiarEstado).not.toHaveBeenCalled();
  });
  it('crear y asociar usa Hardware recién leído, conserva inactivos y no hace POST duplicado con doble envío', async () => {
    const { c } = await formulario('/admin/catalogo/sistemas?crear=1&hardwareId=10');
    const post = new Subject<CatalogoAdmin>(); api.guardar.and.callFake((tipo, id, data) => tipo === 'sistemas' ? post : of({ id: id!, ...data }));
    api.obtener.and.returnValue(of({ ...hardwares[0], sistemaIds: [2, 3, 4, 8] }));
    c.form.controls.nombre.setValue('Sistema nuevo'); c.guardar(); c.guardar();
    expect(api.guardar.calls.count()).toBe(1);
    post.next(registro(30, 'Sistema nuevo')); post.complete();
    expect(api.obtener).toHaveBeenCalledWith('hardware', 10);
    expect(api.guardar.calls.mostRecent().args).toEqual(['hardware', 10, jasmine.objectContaining({ nombre: 'POS', sistemaOperativo: 'Linux', sistemaIds: [2, 3, 4, 8, 30] })]);
    expect(api.guardar.calls.allArgs().filter(a => a[0] === 'sistemas').length).toBe(1);
    expect(c.modalOpen).toBeFalse(); expect(c.mensaje).toContain('creado y asociado');
  });
  it('no crea un sistema inactivo que el backend rechazaría al asociar', async () => {
    const { c } = await formulario('/admin/catalogo/sistemas?crear=1&hardwareId=10');
    c.form.controls.nombre.setValue('Nuevo'); c.form.controls.activo.setValue(false); c.guardar();
    expect(api.guardar).not.toHaveBeenCalled(); expect(c.formError).toContain('debe estar activo');
  });
  it('fallo parcial conserva ID, muestra recuperación y reintenta solo asociación mediante acción explícita', async () => {
    const { h, c } = await formulario('/admin/catalogo/sistemas?crear=1&hardwareId=10');
    api.guardar.and.callFake((tipo, id, data) => tipo === 'sistemas' ? of(registro(30, 'Ya creado')) : throwError(() => new Error('Sin conexión')));
    c.form.controls.nombre.setValue('Ya creado'); c.guardar(); c.guardar(); h.detectChanges();
    expect(c.sistemaCreado?.id).toBe(30); expect(c.formError).toContain('ya existe (ID 30)');
    expect(link(h, 'Recuperar desde Todos los Sistemas')).toBeTruthy();
    expect(api.guardar.calls.allArgs().filter(a => a[0] === 'sistemas').length).toBe(1);
    api.guardar.and.callFake((_, id, data) => of({ id: id!, ...data }));
    c.reintentarAsociacion();
    expect(api.guardar.calls.allArgs().filter(a => a[0] === 'sistemas').length).toBe(1);
    expect(api.guardar.calls.mostRecent().args[2].sistemaIds).toContain(30);
    expect(c.modalOpen).toBeFalse();
  });
  it('si falla la lectura de Hardware tras crear, tampoco repite el alta', async () => {
    const { c } = await formulario('/admin/catalogo/sistemas?crear=1&hardwareId=10');
    api.obtener.and.returnValue(throwError(() => new Error('Lectura no disponible')));
    c.form.controls.nombre.setValue('Nuevo'); c.guardar(); c.guardar();
    expect(api.guardar.calls.count()).toBe(1); expect(c.sistemaCreado?.id).toBe(30);
    expect(c.modalOpen).toBeTrue(); expect(c.saving).toBeFalse();
  });
  it('una respuesta de creación incierta deriva al listado y bloquea nuevos POST en el mismo flujo', async () => {
    const { h, c } = await formulario('/admin/catalogo/sistemas?crear=1&hardwareId=10');
    api.guardar.and.returnValue(throwError(() => new Error('Respuesta perdida')));
    c.form.controls.nombre.setValue('Alta incierta'); c.guardar(); c.guardar(); c.abrir(); c.guardar(); h.detectChanges();
    expect(api.guardar.calls.count()).toBe(1); expect(c.formError).toContain('No se pudo confirmar');
    expect(link(h, 'Recuperar desde Todos los Sistemas')).toBeTruthy();
    expect(api.obtener).not.toHaveBeenCalled();
  });
  it('cambiar de contexto con un alta pendiente no inicia asociaciones en el Hardware nuevo', async () => {
    const { h, c } = await formulario('/admin/catalogo/sistemas?crear=1&hardwareId=10');
    const pendiente = new Subject<CatalogoAdmin>(); api.guardar.and.returnValue(pendiente);
    c.form.controls.nombre.setValue('Alta pendiente'); c.guardar();
    await h.navigateByUrl('/admin/catalogo/sistemas?editarId=2&hardwareId=11', AdminCatalogoComponent);
    pendiente.next(registro(30, 'Alta pendiente')); pendiente.complete();
    expect(api.obtener).not.toHaveBeenCalled(); expect(api.guardar.calls.count()).toBe(1);
    expect(c.hardwareOrigenId).toBe(11); expect(c.editId).toBe(2); expect(c.saving).toBeFalse();
  });
});
