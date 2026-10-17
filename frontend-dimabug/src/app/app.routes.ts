import { cambiosProcedimientoGuard } from './features/procedimiento/cambios-procedimiento.guard';
import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { adminGuard, authGuard, guestGuard, staffGuard } from './core/guards/auth.guard';
import { ShellLayoutComponent } from './shared/layout/shell-layout.component';
import { LoginComponent } from './features/auth/login/login.component';
import { RecuperarComponent } from './features/auth/recuperar/recuperar.component';
import { InicioComponent } from './features/inicio/inicio.component';
import { UsuariosComponent } from './features/usuarios/usuarios.component';
import { HardwareComponent } from './features/hardware/hardware.component';
import { DepartamentosComponent } from './features/departamentos/departamentos.component';
import { PruebasComponent } from './features/pruebas/pruebas.component';
import { SolucionesComponent } from './features/soluciones/soluciones.component';
import { ConocimientoComponent } from './features/conocimiento/conocimiento.component';
import { ConocimientoFormComponent } from './features/conocimiento/conocimiento-form.component';
import { ConocimientoDetalleComponent } from './features/conocimiento/conocimiento-detalle.component';
import { ErrorNuevoComponent } from './features/error-nuevo/error-nuevo.component';
import { AdminComponent } from './features/admin/admin.component';
import { AdminCatalogoComponent } from './features/admin/admin-catalogo.component';
import { EjecucionesComponent } from './features/secciones/ejecuciones.component';
import { EjecucionChecklistComponent } from './features/ejecucion/ejecucion-checklist.component';
import { ProcedimientoListaComponent } from './features/procedimiento/procedimiento-lista.component';
import { ProcedimientoDetalleComponent } from './features/procedimiento/procedimiento-detalle.component';
import { ProcedimientoFormComponent } from './features/procedimiento/procedimiento-form.component';
import { NotificacionesComponent } from './features/notificaciones/notificaciones.component';
import { FavoritosComponent } from './features/secciones/favoritos.component';
import { ReportesComponent } from './features/secciones/reportes.component';
import { AuthService } from './core/services/auth.service';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'inicio' },
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'recuperar', component: RecuperarComponent, canActivate: [guestGuard] },
  {
    path: '',
    component: ShellLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'inicio', component: InicioComponent },
      { path: 'plataforma/hardware', component: HardwareComponent, canActivate: [staffGuard] },
      { path: 'departamentos', component: DepartamentosComponent, canActivate: [staffGuard] },
      { path: 'pruebas', component: PruebasComponent, canActivate: [staffGuard] },
      { path: 'procedimientos', component: ProcedimientoListaComponent },
      { path: 'procedimientos/nuevo', component: ProcedimientoFormComponent, canActivate: [staffGuard], canDeactivate: [cambiosProcedimientoGuard] },
      { path: 'procedimientos/:id/editar', component: ProcedimientoFormComponent, canActivate: [staffGuard], canDeactivate: [cambiosProcedimientoGuard] },
      { path: 'procedimientos/:id', component: ProcedimientoDetalleComponent, canDeactivate: [cambiosProcedimientoGuard] },
      { path: 'soluciones', component: SolucionesComponent, canActivate: [staffGuard] },
      { path: 'conocimiento', component: ConocimientoComponent },
      { path: 'conocimiento/nuevo', component: ConocimientoFormComponent, canActivate: [staffGuard] },
      { path: 'conocimiento/:id/editar', component: ConocimientoFormComponent, canActivate: [staffGuard] },
      { path: 'conocimiento/:id', component: ConocimientoDetalleComponent },
      { path: 'notificaciones', component: NotificacionesComponent },
      { path: 'ejecuciones', component: EjecucionesComponent, canActivate: [staffGuard] },
      { path: 'ejecuciones/:id', component: EjecucionChecklistComponent, canActivate: [staffGuard] },
      { path: 'favoritos', component: FavoritosComponent },
      { path: 'reportes', component: ReportesComponent },
      { path: 'error/nuevo', component: ErrorNuevoComponent, canActivate: [staffGuard] },
      { path: 'admin', component: AdminComponent, canActivate: [adminGuard] },
      { path: 'admin/catalogo/:tipo', component: AdminCatalogoComponent, canActivate: [adminGuard] },
      { path: 'usuarios', component: UsuariosComponent, canActivate: [adminGuard] },
      {
        path: 'tickets',
        loadComponent: () => import('./features/tickets/mis-tickets.component').then((m) => m.MisTicketsComponent),
        // Soporte y administración trabajan desde la bandeja.
        canActivate: [() => (inject(AuthService).isStaff() ? inject(Router).createUrlTree(['/tickets/bandeja']) : true)],
      },
      {
        path: 'tickets/nuevo',
        loadComponent: () => import('./features/tickets/crear-ticket.component').then((m) => m.CrearTicketComponent),
        // El administrador gestiona tickets, no los reporta.
        canActivate: [() => (inject(AuthService).isAdmin() ? inject(Router).createUrlTree(['/tickets/bandeja']) : true)],
      },
      {
        path: 'tickets/bandeja',
        loadComponent: () => import('./features/tickets/bandeja-soporte.component').then((m) => m.BandejaSoporteComponent),
        canActivate: [staffGuard],
      },
      {
        path: 'tickets/:id',
        loadComponent: () => import('./features/tickets/detalle-ticket.component').then((m) => m.DetalleTicketComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'inicio' },
];
