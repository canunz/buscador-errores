import { Routes } from '@angular/router';
import { adminGuard, authGuard, guestGuard } from './core/guards/auth.guard';
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
import { ConocimientoDetalleComponent } from './features/conocimiento/conocimiento-detalle.component';
import { ErrorNuevoComponent } from './features/error-nuevo/error-nuevo.component';
import { AdminComponent } from './features/admin/admin.component';
import { AdminCatalogoComponent } from './features/admin/admin-catalogo.component';
import { EjecucionesComponent } from './features/secciones/ejecuciones.component';
import { FavoritosComponent } from './features/secciones/favoritos.component';
import { ReportesComponent } from './features/secciones/reportes.component';

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
      { path: 'plataforma/hardware', component: HardwareComponent },
      { path: 'departamentos', component: DepartamentosComponent },
      { path: 'pruebas', component: PruebasComponent },
      { path: 'procedimientos', component: PruebasComponent },
      { path: 'soluciones', component: SolucionesComponent },
      { path: 'conocimiento', component: ConocimientoComponent },
      { path: 'conocimiento/:id', component: ConocimientoDetalleComponent },
      { path: 'ejecuciones', component: EjecucionesComponent },
      { path: 'favoritos', component: FavoritosComponent },
      { path: 'reportes', component: ReportesComponent },
      { path: 'error/nuevo', component: ErrorNuevoComponent },
      { path: 'admin', component: AdminComponent, canActivate: [adminGuard] },
      { path: 'admin/catalogo/:tipo', component: AdminCatalogoComponent, canActivate: [adminGuard] },
      { path: 'usuarios', component: UsuariosComponent, canActivate: [adminGuard] },
    ],
  },
  { path: '**', redirectTo: 'inicio' },
];
