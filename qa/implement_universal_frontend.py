from pathlib import Path
R=Path(__file__).resolve().parents[1]
F=R/'frontend-dimabug/src/app'
def edit(p,a,b):
    f=F/p;s=f.read_text(encoding='utf-8');assert a in s,p;f.write_text(s.replace(a,b),encoding='utf-8')
edit('core/models/usuario.model.ts','export interface Usuario {','export interface Usuario {\n  accesoBasico?: boolean;')
edit('core/services/auth.service.ts','interface LoginResponse {','interface LoginResponse {\n  accesoBasico?: boolean;')
edit('core/services/auth.service.ts','  readonly isAdmin =','  readonly accesoBasico = computed(() => this.usuario()?.accesoBasico === true);\n  readonly isAdmin =')
edit('core/services/auth.service.ts','      usuarioId: Number(res.usuarioId),','      accesoBasico: res.accesoBasico === true,\n      usuarioId: Number(res.usuarioId),')
edit('features/auth/login/login.component.ts',"this.router.navigateByUrl('/inicio');","this.router.navigateByUrl(this.auth.accesoBasico() ? '/solicitudes' : '/inicio');")
edit('core/guards/auth.guard.ts',"return router.createUrlTree(['/inicio']);","return router.createUrlTree([auth.accesoBasico() ? '/solicitudes' : '/inicio']);")
edit('app.routes.ts','    canActivate: [authGuard],','''    canActivate: [authGuard],
    canActivateChild: [(_route, state) => !inject(AuthService).accesoBasico()
      || state.url.split('?')[0] === '/solicitudes'
      || inject(Router).createUrlTree(['/solicitudes'])],''')
edit('app.routes.ts',"      { path: 'inicio', component: InicioComponent },","""      { path: 'solicitudes', loadComponent: () => import('./features/tickets/solicitudes-basicas.component').then(m => m.SolicitudesBasicasComponent) },
      { path: 'inicio', component: InicioComponent },""")
# Public self-help remains public for anonymous/managed visitors; basic authenticated users stay in tickets.
edit('app.routes.ts',"{path:'',loadComponent:()=>import('./features/autoservicio/portal-layout.component')", "{path:'',canActivateChild:[() => !inject(AuthService).accesoBasico() || inject(Router).createUrlTree(['/solicitudes'])],loadComponent:()=>import('./features/autoservicio/portal-layout.component')")
edit('shared/layout/shell-layout.component.ts','  readonly usuario =','  readonly accesoBasico = this.auth.accesoBasico;\n  readonly usuario =')
edit('shared/layout/shell-layout.component.ts','  private precargarEnSegundoPlano(): void {','  private precargarEnSegundoPlano(): void {\n    if (this.accesoBasico()) return;')
edit('shared/layout/shell-layout.component.ts','  private cargarConteo(): void {','  private cargarConteo(): void {\n    if (this.accesoBasico()) return;')
f=F/'shared/layout/shell-layout.component.html';s=f.read_text(encoding='utf-8')
f.write_text('''@if (accesoBasico()) {
  <header class="topbar"><a routerLink="/solicitudes">DimaBug · Mis tickets</a><button type="button" (click)="cerrarSesion()">Cerrar sesión</button></header>
  <main><router-outlet /></main>
} @else {
'''+s+'\n}\n',encoding='utf-8')
# Scope is part of ticket cache keys; legacy cached internal projections cannot bleed into basic UI.
edit('core/services/ticket.service.ts',"`${this.auth.usuario()?.usuarioId ?? 0}|${resto}`", "`${this.auth.usuario()?.usuarioId ?? 0}|${this.auth.usuario()?.accesoBasico === true ? 'basico' : 'administrado'}|${resto}`")
p=F/'features/tickets/solicitudes-basicas.component.ts'
p.write_text('''import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TicketService } from '../../core/services/ticket.service';
import { TicketDetalle, TicketResumen, PrioridadTicket } from '../../core/models/ticket.model';

/** Minimal own-ticket portal: no directory, classification, knowledge or attachment requests. */
@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <section style="max-width:900px;margin:2rem auto;padding:1rem">
      <h1>Mis tickets</h1><p>Puede registrar un problema y consultar el estado de sus solicitudes.</p>
      @if (error) { <p role="alert">{{error}}</p> }
      <button type="button" (click)="cargar()" [disabled]="ocupado">Actualizar</button>
      <h2>Nuevo ticket</h2>
      <form (ngSubmit)="crear()">
        <label>Título <input name="titulo" [(ngModel)]="titulo" required maxlength="150" /></label>
        <label>Descripción <textarea name="descripcion" [(ngModel)]="descripcion" required maxlength="10000"></textarea></label>
        <label>Prioridad <select name="prioridad" [(ngModel)]="prioridad"><option>BAJA</option><option>MEDIA</option><option>ALTA</option><option>CRITICA</option></select></label>
        <button type="submit" [disabled]="ocupado || !titulo.trim() || !descripcion.trim()">Crear ticket</button>
      </form>
      <ul>@for (t of tickets; track t.id) {
        <li><button type="button" (click)="abrir(t.id)">{{t.codigo}} · {{t.titulo}}</button> — {{t.estado}}</li>
      }</ul>
      <button type="button" (click)="pagina=pagina-1;cargar()" [disabled]="pagina===0 || ocupado">Anterior</button>
      <button type="button" (click)="pagina=pagina+1;cargar()" [disabled]="!hayMas || ocupado">Siguiente</button>
      @if (detalle) { <article><h2>{{detalle.titulo}}</h2><p style="white-space:pre-wrap">{{detalle.descripcion}}</p><p>Estado: {{detalle.estado}}</p></article> }
    </section>`
})
export class SolicitudesBasicasComponent {
  private readonly api = inject(TicketService);
  tickets: TicketResumen[] = [];
  detalle: TicketDetalle | null = null;
  titulo = ''; descripcion = ''; prioridad: PrioridadTicket = 'MEDIA';
  pagina = 0; hayMas = false; ocupado = false; error = '';
  constructor() { this.cargar(); }
  cargar(): void {
    this.ocupado=true; this.error='';
    this.api.listar({page:this.pagina,size:10}).subscribe({next:p=>{
      this.tickets=p.contenido; this.hayMas=p.contenido.length===10; this.ocupado=false;
    },error:()=>{this.error='No fue posible consultar sus tickets.';this.ocupado=false;}});
  }
  abrir(id:number): void {
    this.error=''; this.detalle=null;
    this.api.detalle(id).subscribe({next:t=>this.detalle=t,error:()=>this.error='No fue posible consultar el ticket.'});
  }
  crear(): void {
    if(this.ocupado || !this.titulo.trim() || !this.descripcion.trim()) return;
    this.ocupado=true; this.error='';
    this.api.crear({titulo:this.titulo.trim(),descripcion:this.descripcion.trim(),prioridad:this.prioridad,
      hardwareId:null,sistemaId:null,moduloId:null}).subscribe({next:t=>{
        this.titulo='';this.descripcion='';this.detalle=t;this.pagina=0;this.cargar();
      },error:()=>{this.error='No fue posible crear el ticket.';this.ocupado=false;}});
  }
}
''',encoding='utf-8')
print('FRONTEND_LOCAL_PREPARED')
