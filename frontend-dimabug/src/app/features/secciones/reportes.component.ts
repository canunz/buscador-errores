import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, from, map, mergeMap, of, take, toArray } from 'rxjs';
import { Conocimiento } from '../../core/models/conocimiento.model';
import { Procedimiento } from '../../core/models/procedimiento.model';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';

type ClaveFrecuencia = 'diario' | 'semanal' | 'frecuente' | 'otra' | 'sin';

type MesReporte = {
  clave: string;
  label: string;
  publicados: number;
  reutilizables: number;
};

type RankingItem = {
  id: number;
  titulo: string;
  contexto: string;
  ancho: number;
};

type CategoriaItem = {
  nombre: string;
  valor: number;
  ancho: number;
};

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [DecimalPipe, RouterLink, AyudaFranjaComponent],
  templateUrl: './reportes.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './reportes.component.css'],
})
export class ReportesComponent implements OnInit {
  private readonly conocimientosApi = inject(ConocimientoService);
  private readonly procedimientosApi = inject(ProcedimientoService);

  error = '';
  publicados: Conocimiento[] = [];
  procedimientosPublicados = 0;
  solucionesActivas = 0;
  reutilizables = 0;
  utilidad = 0;
  meses: MesReporte[] = [];
  maxMes = 1;
  ranking: RankingItem[] = [];
  categorias: CategoriaItem[] = [];
  donut = this.calcularDonut(0);

  ngOnInit(): void {
    this.aplicar(this.conocimientosApi.listaEnSesion(), this.procedimientosApi.listaEnSesion());

    forkJoin({
      conocimientos: this.conocimientosApi.listar().pipe(
        take(1),
        catchError(() => of(this.conocimientosApi.listaEnSesion())),
      ),
      procedimientos: this.procedimientosApi.listar().pipe(
        take(1),
        catchError(() => of(this.procedimientosApi.listaEnSesion())),
      ),
    }).subscribe({
      next: ({ conocimientos, procedimientos }) => {
        this.error = '';
        this.aplicar(conocimientos, procedimientos);
        this.cargarSoluciones(conocimientos.filter((item) => item.estado === 'PUBLICADO'));
      },
      error: () => {
        if (!this.publicados.length) {
          this.error = 'No fue posible cargar los indicadores.';
        }
      },
    });
  }

  private cargarSoluciones(publicados: Conocimiento[]): void {
    if (!publicados.length) {
      this.solucionesActivas = 0;
      return;
    }
    from(publicados)
      .pipe(
        mergeMap(
          (item) => this.conocimientosApi.listarSoluciones(item.id).pipe(catchError(() => of([]))),
          4,
        ),
        toArray(),
        map((grupos) => grupos.flat().length),
      )
      .subscribe((total) => {
        this.solucionesActivas = total;
      });
  }

  private aplicar(conocimientos: Conocimiento[], procedimientos: Procedimiento[]): void {
    const publicados = conocimientos.filter((item) => item.estado === 'PUBLICADO');
    const reutilizables = publicados.filter((item) => this.esReutilizable(item.frecuenciaNombre)).length;
    this.publicados = publicados;
    this.procedimientosPublicados = procedimientos.filter((item) => item.estado === 'PUBLICADO').length;
    this.reutilizables = reutilizables;
    this.utilidad = publicados.length ? Math.round((reutilizables / publicados.length) * 100) : 0;
    this.meses = this.serieMensual(publicados);
    this.maxMes = Math.max(...this.meses.map((mes) => Math.max(mes.publicados, mes.reutilizables)), 1);
    this.ranking = this.armarRanking(publicados);
    this.categorias = this.armarCategorias(publicados);
    this.donut = this.calcularDonut(this.utilidad);
  }

  private serieMensual(publicados: Conocimiento[]): MesReporte[] {
    const meses = this.mesesRecientes(6);
    const porMes = new Map(meses.map((mes) => [mes.clave, { publicados: 0, reutilizables: 0 }]));
    const primerMes = meses[0]?.clave;

    for (const item of publicados) {
      const clave = this.mesDe(item.fechaCreacion);
      if (!clave) {
        continue;
      }
      const destino = porMes.has(clave) ? clave : primerMes && clave < primerMes ? primerMes : null;
      const bucket = destino ? porMes.get(destino) : undefined;
      if (!bucket) {
        continue;
      }
      bucket.publicados++;
      if (this.esReutilizable(item.frecuenciaNombre)) {
        bucket.reutilizables++;
      }
    }

    return meses.map((mes) => ({
      clave: mes.clave,
      label: mes.label,
      ...porMes.get(mes.clave)!,
    }));
  }

  private armarRanking(publicados: Conocimiento[]): RankingItem[] {
    const filas = publicados
      .map((item) => {
        const peso = this.pesoFrecuencia(item.frecuenciaNombre);
        const partes = [item.frecuenciaNombre, item.sistemaNombre].map((parte) => parte?.trim()).filter(Boolean);
        return {
          id: item.id,
          titulo: item.titulo,
          contexto: partes.join(' · ') || 'Sin frecuencia',
          peso,
        };
      })
      .filter((item) => item.peso > 0)
      .sort((a, b) => b.peso - a.peso || a.titulo.localeCompare(b.titulo, 'es'))
      .slice(0, 5);
    const max = Math.max(...filas.map((item) => item.peso), 1);
    return filas.map((item) => ({
      id: item.id,
      titulo: item.titulo,
      contexto: item.contexto,
      ancho: Math.round((item.peso / max) * 100),
    }));
  }

  private armarCategorias(publicados: Conocimiento[]): CategoriaItem[] {
    const map = new Map<string, number>();
    for (const item of publicados) {
      const nombre = item.sistemaNombre?.trim() || 'Sin sistema';
      map.set(nombre, (map.get(nombre) || 0) + 1);
    }
    const max = Math.max(...map.values(), 1);
    return [...map.entries()]
      .map(([nombre, valor]) => ({ nombre, valor, ancho: Math.round((valor / max) * 100) }))
      .sort((a, b) => b.valor - a.valor || a.nombre.localeCompare(b.nombre, 'es'));
  }

  private calcularDonut(utilidad: number): { radio: number; perimetro: number; utiles: number; resto: number } {
    const radio = 54;
    const perimetro = 2 * Math.PI * radio;
    return {
      radio,
      perimetro,
      utiles: (utilidad / 100) * perimetro,
      resto: perimetro,
    };
  }

  private mesesRecientes(cantidad: number): Array<{ clave: string; label: string }> {
    const nombres = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
    const ahora = new Date();
    const meses: Array<{ clave: string; label: string }> = [];
    for (let i = cantidad - 1; i >= 0; i--) {
      const fecha = new Date(ahora.getFullYear(), ahora.getMonth() - i, 1);
      const mes = fecha.getMonth();
      meses.push({
        clave: `${fecha.getFullYear()}-${String(mes + 1).padStart(2, '0')}`,
        label: nombres[mes],
      });
    }
    return meses;
  }

  private mesDe(fecha: string | null | unknown): string | null {
    if (fecha == null) {
      return null;
    }
    if (Array.isArray(fecha) && fecha.length >= 2) {
      const anio = Number(fecha[0]);
      const mes = Number(fecha[1]);
      if (anio > 0 && mes >= 1 && mes <= 12) {
        return `${anio}-${String(mes).padStart(2, '0')}`;
      }
    }
    const match = /^(\d{4})-(\d{2})/.exec(String(fecha).trim());
    return match ? `${match[1]}-${match[2]}` : null;
  }

  private esReutilizable(nombre: string | null | undefined): boolean {
    const clave = this.claveFrecuencia(nombre);
    return clave === 'diario' || clave === 'semanal' || clave === 'frecuente';
  }

  private pesoFrecuencia(nombre: string | null | undefined): number {
    const clave = this.claveFrecuencia(nombre);
    if (clave === 'diario') {
      return 3;
    }
    if (clave === 'frecuente') {
      return 2;
    }
    if (clave === 'semanal') {
      return 1;
    }
    return 0;
  }

  private claveFrecuencia(nombre: string | null | undefined): ClaveFrecuencia {
    const normalizado = (nombre ?? '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
    if (!normalizado) {
      return 'sin';
    }
    if (normalizado.includes('diario')) {
      return 'diario';
    }
    if (normalizado.includes('semanal')) {
      return 'semanal';
    }
    if (normalizado.includes('frecuente')) {
      return 'frecuente';
    }
    return 'otra';
  }
}
