import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { HardwareItem } from '../../core/models/catalogo.model';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, map, Subject, switchMap, of, catchError } from 'rxjs';
import { CatalogoRef } from '../../core/models/conocimiento.model';
import { AdminCatalogosApiService } from '../../core/services/admin-catalogos-api.service';
import { AuthService } from '../../core/services/auth.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';

@Component({
  selector: 'app-hardware',
  standalone: true,
  imports: [RouterLink, LoadingModalComponent, AyudaFranjaComponent],
  templateUrl: './hardware.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './hardware.component.css'],
})
export class HardwareComponent implements OnInit {
  private readonly clasificacion = inject(ClasificacionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly adminApi = inject(AdminCatalogosApiService);
  private readonly seleccion = new Subject<number | null>();

  readonly esAdministrador = inject(AuthService).isAdmin;

  items: HardwareItem[] = this.clasificacion.hardwareEnSesion();
  loading = false;
  error = '';
  detalle: HardwareItem | null = null;
  sistemasDetalle: (CatalogoRef & { activo?: boolean })[] = [];
  cargandoSistemas = false;
  errorSistemas = '';
  private hardwareRetorno: number | null = null;

  ngOnInit(): void {
    this.seleccion.pipe(switchMap(id => {
      if (id === null) return of([]);
      const sistemas$ = this.esAdministrador()
        ? forkJoin({ hardware: this.adminApi.obtener('hardware', id), sistemas: this.adminApi.listar('sistemas') }).pipe(
            map(({ hardware, sistemas }) => sistemas.filter(s => hardware.sistemaIds.includes(s.id))))
        : this.clasificacion.sistemasDeHardware(id, true);
      return sistemas$.pipe(catchError((err: Error) => { this.errorSistemas = err.message; return of([]); }));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(sistemas => {
      this.sistemasDetalle = sistemas; this.cargandoSistemas = false;
    });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const value = Number(params.get('hardwareId'));
      this.hardwareRetorno = Number.isSafeInteger(value) && value > 0 ? value : null;
      const item = this.items.find(h => h.id === this.hardwareRetorno);
      if (item) { this.ver(item); this.hardwareRetorno = null; }
    });
    const guardados = this.clasificacion.hardwareEnSesion();
    if (guardados.length) {
      this.items = guardados;
    }
    this.cargar(true);
  }

  ver(item: HardwareItem): void {
    this.detalle = item;
    this.sistemasDetalle = []; this.errorSistemas = ''; this.cargandoSistemas = true;
    this.seleccion.next(item.id);
  }

  cerrarDetalle(): void {
    this.detalle = null;
    this.seleccion.next(null);
  }

  cargar(force = false): void {
    this.loading = !this.items.length;
    this.error = '';
    let first = true;
    this.clasificacion
      .listarHardwareDetalle(force)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => {
          this.items = items;
          if (this.detalle) this.detalle = items.find(h => h.id === this.detalle?.id) ?? null;
          const retorno = items.find(h => h.id === this.hardwareRetorno);
          if (retorno) { this.ver(retorno); this.hardwareRetorno = null; }
          if (first) {
            first = false;
            this.loading = false;
          }
        },
        error: (err: Error) => {
          this.error = err.message;
          if (!this.items.length) {
            this.items = [];
          }
          this.loading = false;
        },
      });
  }
}
