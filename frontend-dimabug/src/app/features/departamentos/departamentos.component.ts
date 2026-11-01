import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators, ValidatorFn } from '@angular/forms';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { AdminOrganizacionApiService } from '../../core/services/admin-organizacion-api.service';
import { RespaldoDepartamentosLocalService } from '../../core/services/respaldo-departamentos-local.service';
import { AuthService } from '../../core/services/auth.service';
import { DepartamentoAdmin, ContactoDepartamentoAdmin, ResponsableAdmin } from '../../core/models/organizacion-admin.model';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
export interface DepartamentoFila extends DepartamentoAdmin { contactos: ContactoDepartamentoAdmin[]; responsables: ResponsableAdmin[]; errorDetalle?: string; }
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
  pendienteEstado: { departamento: DepartamentoFila; contacto?: ContactoDepartamentoAdmin; responsable?: ResponsableAdmin } | null = null;
  formularioResponsable: { departamento: DepartamentoFila; responsable: ResponsableAdmin | null } | null = null;
  readonly tiposContacto = ['ANEXO', 'TELEFONO', 'CORREO', 'OTRO'];
  responsableForm = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(160)]],
    cargo: ['', Validators.maxLength(120)], contacto: ['', Validators.maxLength(160)],
  });
  get tipoHistorico(): string | null {
    const tipo = this.formularioContacto?.contacto?.tipo;
    return tipo && !this.tiposContacto.includes(tipo) ? tipo : null;
  }
  get etiquetaValor(): string {
    return ({ ANEXO: 'Anexo', TELEFONO: 'Teléfono', CORREO: 'Correo electrónico', OTRO: 'Valor del contacto' } as Record<string,string>)[this.contactoForm.controls.tipo.value] ?? 'Valor del contacto histórico';
  }
  get ejemploValor(): string {
    return ({ ANEXO: '619', TELEFONO: '+56 2 2345 6789', CORREO: 'soporte@ejemplo.cl', OTRO: 'Información de contacto' } as Record<string,string>)[this.contactoForm.controls.tipo.value] ?? 'Conservar el formato histórico';
  }
  readonly tieneDatosLocales = this.respaldo.inspeccionar().length > 0;
  departamentoForm = this.fb.nonNullable.group({ nombre: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]] });
  contactoForm = this.fb.nonNullable.group({ tipo: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(50)]],
    valor: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]], activo: [true] });
  ngOnInit(): void {
    this.contactoForm.controls.tipo.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.validarValorContacto());
    this.cargar();
  }
  private validarValorContacto(): void {
    const tipo = this.contactoForm.controls.tipo.value;
    const reglas: ValidatorFn[] = [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)];
    if (tipo === 'ANEXO') reglas.push(Validators.pattern(/^\d{1,10}$/));
    if (tipo === 'CORREO') reglas.push(Validators.email);
    if (tipo === 'TELEFONO') reglas.push(control => {
      const valor = String(control.value ?? '').trim();
      const digitos = valor.replace(/\D/g, '');
      return /^\+?[\d ()-]+$/.test(valor) && digitos.length >= 7 && digitos.length <= 15 ? null : { telefono: true };
    });
    this.contactoForm.controls.valor.setValidators(reglas);
    this.contactoForm.controls.valor.updateValueAndValidity({ emitEvent: false });
  }
  respaldarDatosLocales(): void { if (this.esAdministrador()) this.respaldo.descargar(); }
  ver(item: DepartamentoFila): void { this.detalle = item; }
  cerrarDetalle(): void { if (!this.saving) this.detalle = null; }
  cargar(): void {
    this.loading = true; this.error = '';
    const req: Observable<DepartamentoFila[]> = this.esAdministrador() ? this.cargarAdministracion()
      : this.organizacion.listarDepartamentos(true).pipe(switchMap(deps => deps.length ? forkJoin(deps.map(d =>
          forkJoin({ contactos: this.organizacion.contactosDeDepartamento(d.id), responsables: this.organizacion.responsablesDeDepartamento(d.id, true) })
            .pipe(map(r => ({ id: d.id, nombre: d.nombre, activo: true, version: 0,
              responsables: r.responsables.map(persona => ({ ...persona, departamentoId: d.id, activo: true, revision: '' })),
              contactos: r.contactos.map(c => ({ id: c.id!, departamentoId: d.id, tipo: c.tipo, valor: c.numero,
                activo: c.activo, departamentoActivo: true, disponible: c.activo, version: 0 })),
            })))
        )) : of([])));
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
        const responsables = this.api.responsables(d.id).pipe(
          map(items => ({ items, error: '' })), catchError((e: Error) => of({ items: [] as ResponsableAdmin[], error: e.message })));
        return forkJoin({ contactos, responsables }).pipe(map(r => ({ ...d, contactos: r.contactos.items,
          responsables: r.responsables.items,
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
    if (this.departamentoForm.invalid) { this.departamentoForm.markAllAsTouched(); this.formError = 'Nombre obligatorio, máximo 100 caracteres.'; return; }
    this.saving = true; this.formError = '';
    this.api.guardar(this.editDepartamento?.id ?? null, this.departamentoForm.getRawValue().nombre.trim(), this.editDepartamento?.version)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.saving = false; this.formularioDepartamento = false; this.mensaje = 'Departamento guardado.'; this.cargar(); },
        error: (e: Error) => { this.saving = false; this.formError = e.message; } });
  }
  abrirContacto(d: DepartamentoFila, c?: ContactoDepartamentoAdmin): void {
    if (!this.esAdministrador() || this.saving) return;
    this.formularioContacto = { departamento: d, contacto: c ?? null };
    this.contactoForm.reset({ tipo: c?.tipo ?? 'ANEXO', valor: c?.valor ?? '', activo: c?.activo ?? d.activo }); this.formError = '';
    this.validarValorContacto();
    if (!d.activo && !c) this.contactoForm.controls.activo.disable(); else this.contactoForm.controls.activo.enable();
  }
  guardarContacto(): void {
    const f = this.formularioContacto;
    if (!f || !this.esAdministrador() || this.saving) return;
    if (this.contactoForm.invalid) { this.contactoForm.markAllAsTouched(); this.formError = `Revisa tipo y ${this.etiquetaValor.toLowerCase()}. Ejemplo: ${this.ejemploValor}.`; return; }
    const v = this.contactoForm.getRawValue(); this.saving = true; this.formError = '';
    this.api.guardarContacto(f.departamento.id, f.contacto?.id ?? null, { tipo: v.tipo, valor: v.valor.trim(), activo: v.activo, version: f.contacto?.version })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.saving = false; this.formularioContacto = null; this.mensaje = 'Contacto guardado.'; this.cargar(); },
        error: (e: Error) => { this.saving = false; this.formError = e.message; } });
  }
  abrirResponsable(departamento: DepartamentoFila, responsable?: ResponsableAdmin): void {
    if (!this.esAdministrador() || this.saving || (!responsable && !departamento.activo)) return;
    this.formularioResponsable = { departamento, responsable: responsable ?? null }; this.formError = '';
    this.responsableForm.reset({ nombre: responsable?.nombre ?? '', cargo: responsable?.cargo ?? '', contacto: responsable?.contacto ?? '' });
  }
  descartarYRecargar(): void {
    if (this.saving) return;
    this.formularioDepartamento = false; this.formularioContacto = null; this.formularioResponsable = null;
    this.pendienteEstado = null; this.formError = ''; this.cargar();
  }
  guardarResponsable(): void {
    const f = this.formularioResponsable;
    if (!f || !this.esAdministrador() || this.saving) return;
    if (this.responsableForm.invalid) { this.responsableForm.markAllAsTouched(); this.formError = 'Revisa nombre (máximo 160), cargo (120) y contacto (160).'; return; }
    this.saving = true; this.formError = '';
    const v = this.responsableForm.getRawValue();
    this.api.guardarResponsable(f.departamento.id, f.responsable, { nombre: v.nombre.trim(), cargo: v.cargo.trim(), contacto: v.contacto.trim() })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => {
        this.saving = false; this.formularioResponsable = null; this.mensaje = 'Responsable guardado.'; this.cargar();
      }, error: (e: Error) => { this.saving = false; this.formError = e.message; } });
  }
  pedirEstado(departamento: DepartamentoFila, contacto?: ContactoDepartamentoAdmin, responsable?: ResponsableAdmin): void {
    if (!this.esAdministrador() || this.saving) return;
    this.pendienteEstado = { departamento, contacto, responsable }; this.formError = '';
  }
  confirmarEstado(): void {
    const p = this.pendienteEstado;
    if (!p || !this.esAdministrador() || this.saving) return;
    this.saving = true; this.formError = '';
    const req: Observable<unknown> = p.responsable ? this.api.estadoResponsable(p.responsable, !p.responsable.activo) : p.contacto ? this.api.estadoContacto(p.departamento.id, p.contacto.id, !p.contacto.activo, p.contacto.version)
      : this.api.estado(p.departamento.id, !p.departamento.activo, p.departamento.version);
    req.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.saving = false; this.pendienteEstado = null; this.mensaje = 'Estado guardado. Se conservan las referencias históricas.'; this.cargar(); },
      error: (e: Error) => { this.saving = false; this.formError = e.message; } });
  }
}
