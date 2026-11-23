import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { ConocimientoComponent } from './conocimiento.component';
import { ConocimientoDetalleComponent } from './conocimiento-detalle.component';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { AuthService } from '../../core/services/auth.service';
import { FavoritosService } from '../../core/services/favoritos.service';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { Conocimiento, Solucion } from '../../core/models/conocimiento.model';

describe('Consulta de soluciones desde la base de conocimiento', () => {
  let api: jasmine.SpyObj<ConocimientoService>, h: RouterTestingHarness, c: ConocimientoComponent;
  const conocimiento = { id: 4, titulo: 'Error conexión POS', descripcion: 'Descripción del conocimiento', estado: 'PUBLICADO', hardwareId: 1 } as Conocimiento;
  const soluciones: Solucion[] = [
    { id: 81, titulo: 'Reiniciar POS', descripcion: 'Instrucciones de reinicio', tipo: 'PASOS', orden: 1, activo: true, autoservicio: false },
    { id: 82, titulo: 'Revisar conexión', descripcion: 'Instrucciones de conexión', tipo: 'PASOS', orden: 2, activo: true, autoservicio: true },
  ];
  beforeEach(async () => {
    api = jasmine.createSpyObj('api', ['listaEnSesion', 'listar', 'parecidos', 'buscar', 'precargarGuia', 'listarSoluciones',
      'peekDetalle', 'leerSeccion', 'obtenerPorId', 'listarSintomas', 'listarCausas', 'listarPruebas', 'listarMateriales', 'listarAsignaciones']);
    api.listaEnSesion.and.returnValue([conocimiento]); api.listar.and.returnValue(of([conocimiento]));
    api.parecidos.and.returnValue([conocimiento]); api.buscar.and.returnValue(of([]));
    api.listarSoluciones.and.returnValue(of(soluciones)); api.peekDetalle.and.returnValue(conocimiento);
    api.obtenerPorId.and.returnValue(of(conocimiento)); api.leerSeccion.and.returnValue(null);
    api.listarSintomas.and.returnValue(of([])); api.listarCausas.and.returnValue(of([]));
    api.listarPruebas.and.returnValue(of([])); api.listarMateriales.and.returnValue(of([])); api.listarAsignaciones.and.returnValue(of([]));
    TestBed.configureTestingModule({ providers: [provideRouter([
      { path: 'conocimiento', component: ConocimientoComponent },
      { path: 'conocimiento/:id', component: ConocimientoDetalleComponent },
    ]), { provide: ConocimientoService, useValue: api },
      { provide: AuthService, useValue: { isAdmin: () => false, isStaff: () => false } },
      { provide: FavoritosService, useValue: { tiene: () => false } },
      { provide: OrganizacionService, useValue: {} },
      { provide: ClasificacionService, useValue: { listarHardware: () => of([]), listarFrecuencias: () => of({ items: [], disponible: true }), listarSistemas: () => of([]), sistemasDeHardware: () => of([]), modulosDeSistema: () => of([]) } },
    ] });
    h = await RouterTestingHarness.create(); c = await h.navigateByUrl('/conocimiento', ConocimientoComponent);
    await h.fixture.whenStable(); h.detectChanges();
  });
  function boton(texto: string): HTMLButtonElement {
    return Array.from(h.routeNativeElement!.querySelectorAll('button')).find(b => b.textContent!.trim() === texto)!;
  }
  function abrir() { boton('Ver soluciones').click(); h.detectChanges(); }
  function modal(): HTMLDialogElement { return h.routeNativeElement!.querySelector('dialog')!; }
  it('separa detalle y soluciones; lista solo títulos, incluso soluciones no de autoservicio', () => {
    expect(boton('Ver detalle')).toBeTruthy(); abrir(); expect(modal().open).toBeTrue();
    expect(modal().querySelector('h2')!.textContent).toContain(conocimiento.titulo);
    expect(modal().querySelectorAll('.soluciones-listado button').length).toBe(2);
    expect(modal().textContent).not.toContain('Instrucciones de reinicio');
    expect(api.listarSoluciones).toHaveBeenCalledWith(4, true);
  });
  it('mantiene el foco dentro del modal y lo devuelve al botón de apertura', async () => {
    const disparador = boton('Ver soluciones'); disparador.focus(); abrir();
    expect(modal().contains(document.activeElement)).toBeTrue();
    boton('Cerrar').click(); await h.fixture.whenStable(); h.detectChanges();
    expect(document.activeElement).toBe(disparador);
  });
  it('limpia la solicitud cuando el diálogo se cierra de forma nativa', async () => {
    const pendiente = new Subject<Solucion[]>(); api.listarSoluciones.and.returnValue(pendiente); abrir();
    const cerrado = new Promise<void>(resolve => modal().addEventListener('close', () => resolve(), { once: true }));
    modal().close(); await cerrado; h.detectChanges();
    expect(pendiente.observed).toBeFalse(); expect(c.conocimientoSoluciones).toBeNull();
  });
  it('una única solución igualmente requiere elegir su título', () => {
    api.listarSoluciones.and.returnValue(of([soluciones[0]])); abrir();
    expect(modal().open).toBeTrue(); expect(modal().querySelectorAll('.soluciones-listado button').length).toBe(1);
  });
  it('informa cuando no hay soluciones y permite cerrar', () => {
    api.listarSoluciones.and.returnValue(of([])); abrir(); expect(modal().textContent).toContain('no tiene soluciones disponibles');
    boton('Cerrar').click(); h.detectChanges(); expect(modal().open).toBeFalse();
  });
  it('controla errores y permite reintentar', () => {
    api.listarSoluciones.and.returnValue(throwError(() => new Error('HTTP'))); abrir();
    expect(modal().querySelector('[role=alert]')).not.toBeNull();
    api.listarSoluciones.and.returnValue(of(soluciones)); boton('Reintentar').click(); h.detectChanges();
    expect(modal().querySelectorAll('.soluciones-listado button').length).toBe(2);
  });
  it('cancela solicitudes al cerrar y descarta resultados anteriores', () => {
    const anterior = new Subject<Solucion[]>(); api.listarSoluciones.and.returnValue(anterior); abrir();
    expect(modal().querySelector('[role=status]')).not.toBeNull(); c.cerrarSoluciones(); expect(anterior.observed).toBeFalse();
    api.listarSoluciones.and.returnValue(of([soluciones[1]])); c.abrirSoluciones({ id: 5, titulo: 'Otro' });
    anterior.next([soluciones[0]]); modal().dispatchEvent(new Event('close')); h.detectChanges();
    expect(c.solucionesModalItems.map(s => s.id)).toEqual([82]);
  });
  for (const s of soluciones) it('navega exclusivamente al detalle de la solución ' + s.id, async () => {
    abrir(); const enlaces = modal().querySelectorAll<HTMLButtonElement>('.soluciones-listado button');
    enlaces[soluciones.indexOf(s)].click(); await h.fixture.whenStable(); h.detectChanges();
    const root = h.routeNativeElement!; expect(root.querySelectorAll('article.solution').length).toBe(1);
    expect(root.querySelector('h1')!.textContent).toBe(s.titulo);
    expect(root.querySelector('.solution')!.textContent).toContain(s.descripcion);
    expect(root.querySelector('.solution')!.textContent).not.toContain(soluciones.find(otra => otra.id !== s.id)!.descripcion);
    expect(root.querySelector('.pasos')).toBeNull(); expect(root.querySelector('.paso-controles')).toBeNull();
  });
  it('Ver detalle conserva el conocimiento completo y todas sus soluciones', async () => {
    boton('Ver detalle').click(); await h.fixture.whenStable(); h.detectChanges();
    const detalle = h.routeDebugElement!.componentInstance as ConocimientoDetalleComponent;
    expect(h.routeNativeElement!.querySelector('h1')!.textContent).toContain(conocimiento.titulo);
    expect(h.routeNativeElement!.querySelector('.pasos')).not.toBeNull(); detalle.irPaso(4); h.detectChanges();
    expect(h.routeNativeElement!.querySelectorAll('.solution').length).toBe(2);
  });
  for (const valor of ['999', '0', 'no-valido']) it('no muestra otras soluciones con selección inexistente o inválida: ' + valor, async () => {
    await h.navigateByUrl('/conocimiento/4?solucion=' + valor, ConocimientoDetalleComponent); h.detectChanges();
    expect(h.routeNativeElement!.querySelectorAll('.solution').length).toBe(0);
    expect(h.routeNativeElement!.textContent).toContain('La solución seleccionada no está disponible');
  });
  it('permite cambiar la selección y regresar al conocimiento sin dejar la anterior visible', async () => {
    await h.navigateByUrl('/conocimiento/4?solucion=81', ConocimientoDetalleComponent);
    await h.navigateByUrl('/conocimiento/4?solucion=82', ConocimientoDetalleComponent); h.detectChanges();
    expect(h.routeNativeElement!.querySelector('.solution')!.textContent).toContain('Instrucciones de conexión');
    await h.navigateByUrl('/conocimiento/4', ConocimientoDetalleComponent); h.detectChanges();
    expect(h.routeNativeElement!.querySelector('.pasos')).not.toBeNull();
  });
  it('mantiene la búsqueda y los filtros al cerrar el modal', () => {
    c.texto = 'POS'; c.hardwareId = 1; c.buscar(); h.detectChanges();
    const lista = c.resultados; abrir(); c.cerrarSoluciones();
    expect(c.texto).toBe('POS'); expect(c.hardwareId).toBe(1); expect(c.resultados).toBe(lista);
    expect(api.buscar).toHaveBeenCalledWith(jasmine.objectContaining({ texto: 'POS', hardwareId: 1 }));
  });
  for (const ancho of [360, 1280]) it('modal sin desbordamiento horizontal a ' + ancho + ' px', async () => {
    api.listarSoluciones.and.returnValue(of([{ ...soluciones[0], titulo: 'Título'.repeat(90) }])); abrir();
    const frame = document.createElement('iframe'); frame.style.width = ancho + 'px'; frame.style.height = '700px';
    const loaded = new Promise<void>(resolve => frame.onload = () => resolve());
    frame.srcdoc = '<!doctype html><html><head>' + Array.from(document.querySelectorAll('style')).map(s => s.outerHTML).join('') + '</head><body>' + h.routeNativeElement!.outerHTML + '</body></html>';
    document.body.appendChild(frame);
    try { await loaded; const dialog = frame.contentDocument!.querySelector('dialog')!; dialog.removeAttribute('open'); dialog.showModal();
      expect(dialog.getBoundingClientRect().width).toBeLessThanOrEqual(ancho);
      expect(dialog.scrollWidth).toBeLessThanOrEqual(dialog.clientWidth);
    } finally { frame.remove(); }
  });
});
