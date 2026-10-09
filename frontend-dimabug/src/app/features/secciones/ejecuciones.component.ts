import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Ejecucion } from '../../core/models/ejecucion.model';
import { Procedimiento } from '../../core/models/procedimiento.model';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';

@Component({
  selector: 'app-ejecuciones',
  standalone: true,
  imports: [DatePipe, RouterLink, LoadingModalComponent, AyudaFranjaComponent],
  templateUrl: './ejecuciones.component.html',
  styleUrl: '../../shared/ui/catalogo-page.css',
})
export class EjecucionesComponent implements OnInit {
  private readonly ejecuciones = inject(EjecucionService);
  private readonly procedimientos = inject(ProcedimientoService);

  items: Ejecucion[] = [];
  nombres: Record<number, string> = {};
  loading = false;
  error = '';

  ngOnInit(): void {
    this.items = this.ejecuciones.listaEnSesion();
    this.pintarNombres(this.procedimientos.listaEnSesion());
    this.items.slice(0, 8).forEach((item) => this.ejecuciones.preparar(item.id));
    this.loading = this.items.length === 0;
    this.ejecuciones.listar().subscribe({
      next: (items) => {
        this.items = items;
        this.loading = false;
        this.items.slice(0, 8).forEach((item) => this.ejecuciones.preparar(item.id));
      },
      error: (err: Error) => {
        this.error = err.message;
        this.loading = false;
      },
    });
    this.procedimientos.listar().subscribe({
      next: (items) => this.pintarNombres(items),
      error: () => undefined,
    });
  }

  private pintarNombres(items: Procedimiento[]): void {
    this.nombres = Object.fromEntries(items.map((item) => [item.id, item.nombre]));
  }

  preparar(id: number, ya = false): void {
    this.ejecuciones.preparar(id, ya);
  }

  nombre(procedimientoId: number): string {
    return this.nombres[procedimientoId] || `Procedimiento ${procedimientoId}`;
  }

  etiqueta(estado: string): string {
    if (estado === 'COMPLETADA') {
      return 'Completada';
    }
    if (estado === 'CANCELADA') {
      return 'Cancelada';
    }
    return 'En curso';
  }
}
