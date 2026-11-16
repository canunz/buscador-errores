import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { AdminPruebasApiService } from '../../core/services/admin-pruebas-api.service';
import { RespaldoPruebasLocalService } from '../../core/services/respaldo-pruebas-local.service';
import { AuthService } from '../../core/services/auth.service';
import { PruebaItem } from '../../core/models/catalogo.model';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
@Component({ selector: 'app-pruebas', standalone: true, imports: [ReactiveFormsModule, LoadingModalComponent, AyudaFranjaComponent],
 templateUrl: './pruebas.component.html', styleUrls: ['../../shared/ui/catalogo-page.css', './pruebas.component.css'] })
export class PruebasComponent implements OnInit {
 private readonly organizacion = inject(OrganizacionService);
 private readonly api = inject(AdminPruebasApiService);
 private readonly respaldo = inject(RespaldoPruebasLocalService);
 private readonly destroyRef = inject(DestroyRef);
 private readonly fb = inject(FormBuilder);
 readonly esAdministrador = inject(AuthService).isAdmin;
 readonly tieneDatosLocales = this.respaldo.inspeccionar().length > 0;
 items: PruebaItem[] = []; detalle: PruebaItem | null = null; pendienteEstado: PruebaItem | null = null;
 loading = false; saving = false; error = ''; formError = ''; mensaje = '';
 formulario = false; editItem: PruebaItem | null = null;
 form = this.fb.nonNullable.group({ descripcion: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]], resultadoEsperado: ['', Validators.maxLength(500)] });
 ngOnInit(): void { this.cargar(); }
 ver(item: PruebaItem): void { this.detalle = item; }
 cerrarDetalle(): void { if (!this.saving) this.detalle = null; }
 cargar(): void {
  this.loading = true; this.error = '';
  const req = this.esAdministrador() ? this.api.listar() : this.organizacion.listarPruebasDetalle(true);
  req.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: items => { this.items = items; this.loading = false;
    if (this.detalle) this.detalle = items.find(p => p.id === this.detalle!.id) ?? null; }, error: (e: Error) => { this.loading = false; this.error = e.message; } });
 }
 abrirFormulario(item?: PruebaItem): void {
  if (!this.esAdministrador() || this.saving) return;
  this.editItem = item ?? null; this.form.reset({ descripcion: item?.descripcion ?? '', resultadoEsperado: item?.resultadoEsperado ?? '' }); this.formError = ''; this.formulario = true;
 }
 cerrarFormulario(): void { if (!this.saving) this.formulario = false; }
 guardar(): void {
  if (!this.esAdministrador() || this.saving) return;
  if (this.form.invalid) { this.form.markAllAsTouched(); this.formError = 'Descripción obligatoria. Máximo 500 caracteres por campo.'; return; }
  const v = this.form.getRawValue(); this.saving = true; this.formError = ''; this.mensaje = '';
  this.api.guardar(this.editItem?.id ?? null, v.descripcion, v.resultadoEsperado).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
   next: () => { this.saving = false; this.formulario = false; this.mensaje = 'Prueba guardada.'; this.cargar(); }, error: (e: Error) => { this.saving = false; this.formError = e.message; } });
 }
 pedirCambioEstado(item: PruebaItem): void { if (this.esAdministrador() && !this.saving) { this.pendienteEstado = item; this.formError = ''; } }
 cancelarEstado(): void { if (!this.saving) this.pendienteEstado = null; }
 confirmarEstado(): void {
  const p = this.pendienteEstado; if (!p || !this.esAdministrador() || this.saving) return;
  this.saving = true; this.formError = ''; this.mensaje = '';
  this.api.estado(p.id, !p.activo).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => {
    this.saving = false; this.pendienteEstado = null; this.mensaje = 'Estado guardado. Se conservan las asociaciones históricas.'; this.cargar();
   }, error: (e: Error) => { this.saving = false; this.formError = e.message; } });
 }
 respaldar(): void { if (this.esAdministrador()) this.respaldo.descargar(); }
}