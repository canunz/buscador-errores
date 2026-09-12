import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { CatalogoService } from './catalogo.service';

export interface FrecuenteItem {
  id: number;
  tipo: 'PROCEDIMIENTO' | 'HARDWARE' | 'CATEGORIA' | string;
  titulo: string;
  subtitulo: string;
  pasos: number | null;
  ruta: string;
}

export interface InicioDashboard {
  conocimientos: number;
  procedimientos: number;
  ultimaActualizacion: string | null;
  frecuentes: FrecuenteItem[];
}

@Injectable({ providedIn: 'root' })
export class InicioService {
  private readonly catalogo = inject(CatalogoService);

  dashboard(): Observable<InicioDashboard> {
    const procedimientos = this.catalogo.pruebas().filter((p) => p.activo);
    const conocimientos = this.catalogo.soluciones();
    const hardware = this.catalogo.hardware();

    const frecuentes: FrecuenteItem[] = procedimientos.length
      ? procedimientos.slice(0, 4).map((p) => ({
          id: p.id,
          tipo: 'PROCEDIMIENTO',
          titulo: p.descripcion,
          subtitulo: p.resultadoEsperado || 'Procedimiento',
          pasos: null,
          ruta: '/procedimientos',
        }))
      : hardware.slice(0, 4).map((h) => ({
          id: h.id,
          tipo: 'HARDWARE',
          titulo: h.nombre,
          subtitulo: h.so || 'Hardware',
          pasos: null,
          ruta: '/plataforma/hardware',
        }));

    return of({
      conocimientos: conocimientos.length,
      procedimientos: procedimientos.length,
      ultimaActualizacion: null,
      frecuentes,
    });
  }
}
