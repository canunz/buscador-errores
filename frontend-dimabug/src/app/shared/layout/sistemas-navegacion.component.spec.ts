import { signal } from '@angular/core';
import { TestBed, fakeAsync, tick, flush, discardPeriodicTasks } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ShellLayoutComponent } from './shell-layout.component';
import { routes } from '../../app.routes';
import { AdminCatalogoComponent } from '../../features/admin/admin-catalogo.component';
import { adminGuard } from '../../core/guards/auth.guard';
import { Usuario } from '../../core/models/usuario.model';
import { AuthService } from '../../core/services/auth.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { InicioService } from '../../core/services/inicio.service';
import { NotificacionService } from '../../core/services/notificacion.service';
import { AdminCatalogosApiService } from '../../core/services/admin-catalogos-api.service';

describe('QA-010 navegación Sistemas', () => {
  const usuario = signal<Usuario>({ usuarioId: 1, usuarioNombre: 'Admin', usuarioEmail: 'a@x.cl', usuarioEstado: true, rolNombre: 'ADMINISTRADOR' });
  const ruta = routes.find(r => r.component === ShellLayoutComponent)!.children!.find(r => r.path === 'admin/catalogo/:tipo')!;
  beforeEach(() => {
    usuario.set({ ...usuario(), rolNombre: 'ADMINISTRADOR' });
    TestBed.configureTestingModule({ providers: [provideRouter([ruta]),
      { provide: AuthService, useValue: { usuario, isStaff: () => true, isAdmin: () => usuario().rolNombre === 'ADMINISTRADOR', isAuthenticated: () => true } },
      { provide: ProcedimientoService, useValue: { listar: () => of([]) } },
      { provide: ConocimientoService, useValue: { listar: () => of([]) } },
      { provide: EjecucionService, useValue: { listar: () => of([]) } },
      { provide: ClasificacionService, useValue: { listarHardwareDetalle: () => of([]) } },
      { provide: OrganizacionService, useValue: { listarDepartamentosDetalle: () => of([]) } },
      { provide: UsuarioService, useValue: { listar: () => of([]), listarRoles: () => of([]) } },
      { provide: InicioService, useValue: { dashboard: () => of({}) } },
      { provide: NotificacionService, useValue: { propias: () => of([]) } },
      { provide: AdminCatalogosApiService, useValue: { listar: () => of([]) } },
    ] });
  });
  it('reutiliza ruta y guard administrativo existentes', () => {
    expect(ruta.component).toBe(AdminCatalogoComponent); expect(ruta.canActivate).toContain(adminGuard);
  });
  it('administrador ve Sistemas dentro de Catálogos', () => {
    const f = TestBed.createComponent(ShellLayoutComponent); f.detectChanges();
    const enlace = f.nativeElement.querySelector('a[href="/admin/catalogo/sistemas"]');
    expect(enlace).not.toBeNull(); expect(enlace.textContent).toContain('Sistemas');
    const etiqueta = Array.from(f.nativeElement.querySelectorAll('.nav-label')).find((e: any) => e.textContent.includes('Catálogos')) as HTMLElement;
    expect(etiqueta.nextElementSibling).toBe(enlace);
  });
  it('técnico no ve el acceso administrativo de Sistemas', () => {
    usuario.set({ ...usuario(), rolNombre: 'TECNICO' });
    const f = TestBed.createComponent(ShellLayoutComponent); f.detectChanges();
    expect(f.nativeElement.querySelector('a[href="/admin/catalogo/sistemas"]')).toBeNull();
    expect(f.nativeElement.querySelector('a[href="/plataforma/hardware"]')).not.toBeNull();
  });
  it('al navegar cierra el menú y abre la administración existente', fakeAsync(() => {
    const f = TestBed.createComponent(ShellLayoutComponent); f.detectChanges();
    f.componentInstance.menuOpen = true;
    f.nativeElement.querySelector('a[href="/admin/catalogo/sistemas"]').click();
    tick(); f.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/admin/catalogo/sistemas');
    expect(f.componentInstance.menuOpen).toBeFalse();
    expect(f.nativeElement.querySelector('app-admin-catalogo h1').textContent).toContain('Sistemas');
    f.destroy(); flush(); discardPeriodicTasks();
  }));
  it('QA-011 administrador ve Módulos y conserva enlace Sistemas', () => {
    const f = TestBed.createComponent(ShellLayoutComponent); f.detectChanges();
    expect(f.nativeElement.querySelector('a[href="/admin/catalogo/modulos"]').textContent).toContain('Módulos');
    expect(f.nativeElement.querySelector('a[href="/admin/catalogo/sistemas"]')).not.toBeNull();
  });
  it('QA-011 técnico no ve Módulos administrativo', () => {
    usuario.set({ ...usuario(), rolNombre: 'TECNICO' });
    const f = TestBed.createComponent(ShellLayoutComponent); f.detectChanges();
    expect(f.nativeElement.querySelector('a[href="/admin/catalogo/modulos"]')).toBeNull();
  });
  it('QA-011 navega al CRUD existente de Módulos y cierra menú', fakeAsync(() => {
    const f = TestBed.createComponent(ShellLayoutComponent); f.detectChanges(); f.componentInstance.menuOpen = true;
    f.nativeElement.querySelector('a[href="/admin/catalogo/modulos"]').click(); tick(); f.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/admin/catalogo/modulos');
    expect(f.componentInstance.menuOpen).toBeFalse();
    expect(f.nativeElement.querySelector('app-admin-catalogo h1').textContent).toContain('Módulos');
    f.destroy(); flush(); discardPeriodicTasks();
  }));
});
