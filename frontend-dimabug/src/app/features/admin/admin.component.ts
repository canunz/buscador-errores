import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AdminCatalogosApiService, TIPOS_CATALOGO_ADMIN } from '../../core/services/admin-catalogos-api.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
@Component({ selector: 'app-admin', standalone: true, imports: [RouterLink, AyudaFranjaComponent],
  templateUrl: './admin.component.html', styleUrl: './admin.component.css' })
export class AdminComponent implements OnInit {
  private readonly api = inject(AdminCatalogosApiService);
  readonly tipos = TIPOS_CATALOGO_ADMIN;
  readonly nombres = { hardware: 'Hardware', sistemas: 'Sistemas', modulos: 'Módulos', frecuencias: 'Frecuencias' };
  readonly cantidades = signal<number[]>([]);
  loading = false;
  error = '';
  ngOnInit(): void { this.cargar(); }
  cargar(): void {
    this.loading = true; this.error = '';
    forkJoin(this.tipos.map(tipo => this.api.listar(tipo))).subscribe({
      next: listas => { this.cantidades.set(listas.map(lista => lista.length)); this.loading = false; },
      error: (err: Error) => { this.error = err.message; this.loading = false; },
    });
  }
}
