import { DestinatariosEditorComponent } from '../../shared/ui/destinatarios-editor.component';
import { Component, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminSucursalesApiService, DatosSucursal, SucursalAdmin } from '../../core/services/admin-sucursales-api.service';

@Component({
  selector: 'app-admin-sucursales', standalone: true, imports: [FormsModule, RouterLink, DestinatariosEditorComponent],
  templateUrl: './admin-sucursales.component.html',
  styleUrls: ['../tickets/tickets-comun.css', './admin-sucursales.component.css'],
})
export class AdminSucursalesComponent implements OnInit {
  private readonly api = inject(AdminSucursalesApiService);
  sucursales: SucursalAdmin[] = [];
  datos: DatosSucursal = this.vacio();
  editando: SucursalAdmin | null = null;
  loading = false;
  guardando = false;
  error = '';
  aviso = '';
  private original = JSON.stringify(this.datos);

  ngOnInit(): void { this.cargar(); }
  cargar(): void {
    if (this.loading || this.guardando) return;
    this.loading = true; this.error = '';
    this.api.listar().subscribe({
      next: (items) => { this.sucursales = items; this.loading = false; },
      error: (err: Error) => { this.error = err.message; this.loading = false; },
    });
  }
  editar(s: SucursalAdmin): void {
    if (this.guardando || !this.descartar()) return;
    this.editando = s;
    this.datos = { codigo: s.codigo, nombre: s.nombre, responsableNombre: s.responsableNombre, responsableEmail: s.responsableEmail, destinatarios: s.destinatarios ?? [], ciudad:s.ciudad??'',responsableCargo:s.responsableCargo??'' };
    this.original = JSON.stringify(this.datos); this.error = ''; this.aviso = '';
  }
  nuevo(): void {
    if (this.guardando || !this.descartar()) return;
    this.reiniciar();
  }
  guardar(): void {
    if (this.guardando || this.loading || !this.valido()) return;
    this.guardando = true; this.error = ''; this.aviso = '';
    const datos: DatosSucursal = {
      codigo: this.datos.codigo.trim(), nombre: this.datos.nombre.trim(),
      responsableNombre: this.datos.responsableNombre.trim(), responsableEmail: this.datos.responsableEmail.trim(), destinatarios: this.datos.destinatarios ?? [], ciudad:this.datos.ciudad??'',responsableCargo:this.datos.responsableCargo??'',
    };
    const operacion = this.editando ? this.api.editar(this.editando.id, datos, this.editando.version) : this.api.crear(datos);
    operacion.subscribe({
      next: (sucursal) => {
        this.sucursales = [...this.sucursales.filter((s) => s.id !== sucursal.id), sucursal].sort((a, b) => a.codigo.localeCompare(b.codigo));
        this.guardando = false; this.reiniciar(); this.aviso = 'Sucursal guardada.';
      },
      error: (err: Error) => { this.guardando = false; this.error = err.message; },
    });
  }
  alternar(s: SucursalAdmin): void {
    if (this.guardando || this.loading || this.tieneCambiosPendientes()) return;
    this.guardando = true; this.error = ''; this.aviso = '';
    this.api.estado(s, !s.activa).subscribe({
      next: (actualizada) => {
        this.sucursales = this.sucursales.map((fila) => fila.id === actualizada.id ? actualizada : fila);
        if (this.editando?.id === actualizada.id) this.editando = actualizada;
        this.guardando = false; this.aviso = actualizada.activa ? 'Sucursal activada.' : 'Sucursal desactivada.';
      },
      error: (err: Error) => { this.guardando = false; this.error = err.message; },
    });
  }
  valido(): boolean {
    return !!this.datos.codigo.trim() && !!this.datos.nombre.trim() && !!this.datos.responsableNombre.trim() &&
      /^[^\s@]+@dimarsa\.cl$/i.test(this.datos.responsableEmail.trim());
  }
  tieneCambiosPendientes(): boolean { return JSON.stringify(this.datos) !== this.original; }
  operacionEnCurso(): boolean { return this.guardando; }
  @HostListener('window:beforeunload', ['$event'])
  antesDeSalir(event: BeforeUnloadEvent): void { if (this.tieneCambiosPendientes() || this.guardando) event.preventDefault(); }
  private descartar(): boolean { return !this.tieneCambiosPendientes() || window.confirm('Hay cambios de sucursal sin guardar. ¿Deseas descartarlos?'); }
  private reiniciar(): void { this.editando = null; this.datos = this.vacio(); this.original = JSON.stringify(this.datos); }
  private vacio(): DatosSucursal { return { codigo: '', nombre: '', responsableNombre: '', responsableEmail: '', destinatarios: [], ciudad:'',responsableCargo:'' }; }
}
