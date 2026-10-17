import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { AdminOrganizacionApiService } from '../../core/services/admin-organizacion-api.service';
import { RespaldoDepartamentosLocalService } from '../../core/services/respaldo-departamentos-local.service';
import { AuthService } from '../../core/services/auth.service';
import { DepartamentoAdmin, ContactoDepartamentoAdmin } from '../../core/models/organizacion-admin.model';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
export interface DepartamentoFila extends DepartamentoAdmin { contactos: ContactoDepartamentoAdmin[]; responsables: string[]; errorDetalle?: string; }
@Component({ selector: 'app-departamentos', standalone: true,
  imports: [ReactiveFormsModule, LoadingModalComponent, AyudaFranjaComponent],
  templateUrl: './departamentos.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './departamentos.component.css'] })
export class DepartamentosComponent implements OnInit {
  private readonly organizacion = inject(OrganizacionService);
  private readonly api = inject(AdminOrganizacionApiService);
  private readonly respaldo = inject(RespaldoDepartamentosLocalService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  readonly esAdministrador = inject(AuthService).isAdmin;
  items: DepartamentoFila[] = [];
  loading = false; saving = false; error = ''; formError = ''; mensaje = '';
  detalle: DepartamentoFila | null = null;
  editDepartamento: DepartamentoFila | null = null;
  formularioDepartamento = false;
  formularioContacto: { departamento: DepartamentoFila; contacto: ContactoDepartamentoAdmin | null } | null = null;
  pendienteEstado: { departamento: DepartamentoFila; contacto?: ContactoDepartamentoAdmin } | null = null;
  readonly tieneDatosLocales = this.respaldo.inspeccionar().length > 0;
  departamentoForm = this.fb.nonNullable.group({ nombre: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]] });
  contactoForm = this.fb.nonNullable.group({ tipo: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(50)]],
    valor: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]], activo: [true] });
  ngOnInit(): void { this.cargar(); }
  respaldarDatosLocales(): void { if (this.esAdministrador()) this.respaldo.descargar(); }
  ver(item: DepartamentoFila): void { this.detalle = item; }
  cerrarDetalle(): void { if (!this.saving) this.detalle = null; }
  cargar(): void {
    this.loading = true; this.error = '';
    const req: Observable<DepartamentoFila[]> = this.esAdministrador() ? this.cargarAdministracion()
      : this.organizacion.listarDepartamentosDetalle(true).pipe(map(deps => deps.map(d => ({
          id: d.id, nombre: d.nombre, activo: true, version: 0, responsables: d.responsables,
          contactos: d.contactos.map((c, i) => ({ id: c.id ?? i, departamentoId: d.id, tipo: c.tipo,
            valor: c.numero, activo: c.activo, departamentoActivo: true, disponible: c.activo, version: 0 })),
        }))));
    req.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => { this.items = items; this.loading = false;
        if (this.detalle) this.detalle = items.find(d => d.id === this.detalle!.id) ?? null; },
      error: (e: Error) => { this.error = e.message; this.loading = false; },
    });
  }
  private cargarAdministracion(): Observable<DepartamentoFila[]> {
    return this.api.listar().pipe(switchMap(deps => {
      if (!deps.length) return of([] as DepartamentoFila[]);
      return forkJoin(deps.map(d => {
        const contactos = this.api.contactos(d.id).pipe(map(items => ({ items, error: '' })),
          catchError((e: Error) => of({ items: [] as ContactoDepartamentoAdmin[], error: e.message })));
        const responsables = d.activo ? this.organizacion.responsablesDeDepartamento(d.id, true).pipe(
          map(items => ({ items, error: '' })), catchError((e: Error) => of({ items: [], error: e.message })))
          : of({ items: [], error: '' });
        return forkJoin({ contactos, responsables }).pipe(map(r => ({ ...d, contactos: r.contactos.items,
          responsables: r.responsables.items.map(persona => persona.nombre),
          errorDetalle: [r.contactos.error, r.responsables.error].filter(Boolean).join(' · '),
        })));
      }));
    }));
  }
  abrirDepartamento(item?: DepartamentoFila): void {
    if (!this.esAdministrador() || this.saving) return;
    this.editDepartamento = item ?? null; this.departamentoForm.reset({ nombre: item?.nombre ?? '' });
    this.formError = ''; this.formularioDepartamento = true;
  }
  guardarDepartamento(): void {
    if (!this.esAdministrador() || this.saving) return;
    if (this.departamentoForm.invalid) { this.departamentoForm.markAllAsTouched(); this.formError = 'Nombre obligatorio, máximo 255 caracteres.'; return; }
    this.saving = true; this.formError = '';
    this.api.guardar(this.editDepartamento?.id ?? null, this.departamentoForm.getRawValue().nombre.trim(), this.editDepartamento?.version)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.saving = false; this.formularioDepartamento = false; this.mensaje = 'Departamento guardado.'; this.cargar(); },
        error: (e: Error) => { this.saving = false; this.formError = e.message; } });
  }
  abrirContacto(d: DepartamentoFila, c?: ContactoDepartamentoAdmin): void {
    if (!this.esAdministrador() || this.saving) return;
    this.formularioContacto = { departamento: d, contacto: c ?? null };
    this.contactoForm.reset({ tipo: c?.tipo ?? '', valor: c?.valor ?? '', activo: c?.activo ?? d.activo }); this.formError = '';
  }
  guardarContacto(): void {
    const f = this.formularioContacto;
    if (!f || !this.esAdministrador() || this.saving) return;
    if (this.contactoForm.invalid) { this.contactoForm.markAllAsTouched(); this.formError = 'Tipo y valor obligatorios (máximo 50 y 100 caracteres).'; return; }
    const v = this.contactoForm.getRawValue(); this.saving = true; this.formError = '';
    this.api.guardarContacto(f.departamento.id, f.contacto?.id ?? null, { tipo: v.tipo.trim(), valor: v.valor.trim(), activo: v.activo, version: f.contacto?.version })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.saving = false; this.formularioContacto = null; this.mensaje = 'Contacto guardado.'; this.cargar(); },
        error: (e: Error) => { this.saving = false; this.formError = e.message; } });
  }
  pedirEstado(departamento: DepartamentoFila, contacto?: ContactoDepartamentoAdmin): void {
    if (!this.esAdministrador() || this.saving) return;
    this.pendienteEstado = { departamento, contacto }; this.formError = '';
  }
  confirmarEstado(): void {
    const p = this.pendienteEstado;
    if (!p || !this.esAdministrador() || this.saving) return;
    this.saving = true; this.formError = '';
    const req: Observable<unknown> = p.contacto ? this.api.estadoContacto(p.departamento.id, p.contacto.id, !p.contacto.activo, p.contacto.version)
      : this.api.estado(p.departamento.id, !p.departamento.activo, p.departamento.version);
    req.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.saving = false; this.pendienteEstado = null; this.mensaje = 'Estado guardado. Se conservan las referencias históricas.'; this.cargar(); },
      error: (e: Error) => { this.saving = false; this.formError = e.message; } });
  }
}
