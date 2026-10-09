import { Injectable, inject } from '@angular/core';
import { Observable, catchError, concat, map, of, shareReplay, switchMap, take, tap } from 'rxjs';
import { ClasificacionService } from './clasificacion.service';
import { ConocimientoService } from './conocimiento.service';
import { OrganizacionService } from './organizacion.service';
import { CatalogoRef, Conocimiento, PruebaCatalogo } from '../models/conocimiento.model';

export interface FrecuenteItem {
  id: number;
  tipo: 'PROCEDIMIENTO' | 'HARDWARE' | 'CATEGORIA' | string;
  titulo: string;
  subtitulo: string;
  pasos: number | null;
  ruta: string;
}

export type ClaveFrecuencia = 'diario' | 'semanal' | 'frecuente' | 'otra';

export interface BarraFrecuencia {
  clave: ClaveFrecuencia;
  etiqueta: string;
  total: number;
  porcentaje: number;
  ancho: number;
}

export interface CasoRepetido {
  id: number;
  titulo: string;
  frecuencia: string;
  clave: Exclude<ClaveFrecuencia, 'otra'>;
  contexto: string;
}

export interface PuntoGrafico {
  etiqueta: string;
  total: number;
  reutilizables: number;
  x: number;
  pubX: number;
  pubY: number;
  pubH: number;
  pubW: number;
  reusoX: number;
  reusoY: number;
  reusoH: number;
  reusoW: number;
}

export interface GuiaGrafico {
  valor: number;
  y: number;
}

export interface GraficoEscala {
  modo: 'meses' | 'frecuencia';
  mostrarReuso: boolean;
  puntos: PuntoGrafico[];
  linea: string;
  lineaReuso: string;
  area: string;
  guias: GuiaGrafico[];
  ultimo: number;
  delta: number;
  ancho: number;
  alto: number;
  base: number;
  plotX: number;
  plotRight: number;
}

export interface EscalaConocimiento {
  casosUnicos: number;
  reutilizables: number;
  sinFrecuencia: number;
  diario: number;
  semanal: number;
  frecuente: number;
  barras: BarraFrecuencia[];
  repetidos: CasoRepetido[];
  grafico: GraficoEscala;
}

export interface InicioDashboard {
  conocimientos: number;
  procedimientos: number;
  ultimaActualizacion: string | null;
  frecuentes: FrecuenteItem[];
  escala: EscalaConocimiento | null;
}

@Injectable({ providedIn: 'root' })
export class InicioService {
  private readonly conocimientos = inject(ConocimientoService);
  private readonly organizacion = inject(OrganizacionService);
  private readonly clasificacion = inject(ClasificacionService);
  private readonly storageKey = 'dimabug.inicio.dashboard.v4';
  private dashboardCache$: Observable<InicioDashboard> | null = null;

  /**
   * Procedimientos frecuentes primero (GET /pruebas, con caché).
   * Conocimientos solo enriquecen contadores/fecha en segundo plano.
   */
  dashboardAhora(): InicioDashboard {
    const guardado = this.readSession();
    const conocimientos = this.conocimientos.listaEnSesion();
    const pruebas = this.organizacion.snapshotPruebas();
    const hardware = this.clasificacion.snapshotHardware();
    if (!conocimientos.length && !pruebas.length && !hardware.length && guardado) {
      return guardado;
    }
    return this.build(pruebas, conocimientos.length ? conocimientos : null, hardware, guardado);
  }

  dashboard(force = false): Observable<InicioDashboard> {
    if (!force && this.dashboardCache$) {
      return this.dashboardCache$;
    }

    const ahora = this.dashboardAhora();
    const stale = ahora;

    const network$ = this.organizacion.listarPruebas().pipe(
      catchError(() => of([] as PruebaCatalogo[])),
      switchMap((pruebas) => {
        if (pruebas.length) {
          const quick = this.build(pruebas, null, null, stale);
          return concat(
            of(quick),
            this.conocimientos.listar().pipe(
              take(1),
              map((conocimientos) => this.build(pruebas, conocimientos, null, stale)),
              catchError(() => of(this.build(pruebas, stale?.escala ? null : [], null, stale))),
            ),
          );
        }

        return this.clasificacion.listarHardware().pipe(
          catchError(() => of([] as CatalogoRef[])),
          switchMap((hardware) => {
            const quick = this.build([], null, hardware, stale);
            return concat(
              of(quick),
              this.conocimientos.listar().pipe(
                take(1),
                map((conocimientos) => this.build([], conocimientos, hardware, stale)),
                catchError(() => of(this.build([], stale?.escala ? null : [], hardware, stale))),
              ),
            );
          }),
        );
      }),
    );

    this.dashboardCache$ = concat(of(ahora), network$).pipe(
      tap((data) => this.writeSession(data)),
      shareReplay(1),
    );

    return this.dashboardCache$;
  }

  private build(
    pruebas: PruebaCatalogo[],
    conocimientos: Conocimiento[] | null,
    hardware: CatalogoRef[] | null,
    stale: InicioDashboard | null,
  ): InicioDashboard {
    const frecuentes: FrecuenteItem[] = pruebas.length
      ? pruebas.slice(0, 4).map((p) => ({
          id: p.id,
          tipo: 'PROCEDIMIENTO',
          titulo: p.descripcion,
          subtitulo: p.resultadoEsperado || 'Procedimiento',
          pasos: null,
          ruta: '/procedimientos',
        }))
      : (hardware ?? []).slice(0, 4).map((h) => ({
          id: h.id,
          tipo: 'HARDWARE',
          titulo: h.nombre,
          subtitulo: 'Hardware',
          pasos: null,
          ruta: '/plataforma/hardware',
        }));

    const fechas = (conocimientos ?? [])
      .map((c) => c.fechaModificacion || c.fechaCreacion)
      .filter(Boolean)
      .sort()
      .reverse();

    return {
      conocimientos: conocimientos?.length ?? stale?.conocimientos ?? 0,
      procedimientos: pruebas.length || stale?.procedimientos || 0,
      ultimaActualizacion: fechas[0] || stale?.ultimaActualizacion || null,
      frecuentes,
      escala: conocimientos ? this.escala(conocimientos) : (stale?.escala ?? null),
    };
  }

  /** Un conocimiento publicado por problema. La repetición vive en la frecuencia, no en un duplicado. */
  private escala(items: Conocimiento[]): EscalaConocimiento {
    const publicados = items.filter((item) => item.estado === 'PUBLICADO');
    const conteo = { diario: 0, semanal: 0, frecuente: 0, otra: 0, sin: 0 };
    const repetidos: CasoRepetido[] = [];

    for (const item of publicados) {
      const clave = this.claveFrecuencia(item.frecuenciaNombre);
      if (clave === 'sin') {
        conteo.sin++;
        continue;
      }
      conteo[clave]++;
      if (clave === 'diario' || clave === 'semanal' || clave === 'frecuente') {
        const partes = [item.sistemaNombre, item.hardwareNombre].map((parte) => parte?.trim()).filter(Boolean);
        repetidos.push({
          id: item.id,
          titulo: item.titulo,
          frecuencia: item.frecuenciaNombre.trim(),
          clave,
          contexto: partes.join(' · ') || 'Sin sistema ni hardware',
        });
      }
    }

    const orden: Record<Exclude<ClaveFrecuencia, 'otra'>, number> = { diario: 0, frecuente: 1, semanal: 2 };
    repetidos.sort(
      (a, b) => orden[a.clave] - orden[b.clave] || a.titulo.localeCompare(b.titulo, 'es'),
    );

    const total = publicados.length;
    const barrasBase: Array<Pick<BarraFrecuencia, 'clave' | 'etiqueta' | 'total'>> = [
      { clave: 'diario', etiqueta: 'Diario', total: conteo.diario },
      { clave: 'semanal', etiqueta: 'Semanal', total: conteo.semanal },
      { clave: 'frecuente', etiqueta: 'Frecuente', total: conteo.frecuente },
    ];
    if (conteo.otra > 0) {
      barrasBase.push({ clave: 'otra', etiqueta: 'Otras', total: conteo.otra });
    }
    const maximo = Math.max(...barrasBase.map((barra) => barra.total), 1);

    return {
      casosUnicos: total,
      reutilizables: conteo.diario + conteo.semanal + conteo.frecuente,
      sinFrecuencia: conteo.sin,
      diario: conteo.diario,
      semanal: conteo.semanal,
      frecuente: conteo.frecuente,
      barras: barrasBase.map((barra) => ({
        ...barra,
        porcentaje: total ? Math.round((barra.total / total) * 100) : 0,
        ancho: barra.total === 0 ? 0 : Math.round((barra.total / maximo) * 100),
      })),
      repetidos: repetidos.slice(0, 5),
      grafico: this.grafico(publicados),
    };
  }

  /** Curva real: publicaciones por mes. Si no hay fechas, la forma sale de la frecuencia. */
  private grafico(publicados: Conocimiento[]): GraficoEscala {
    const meses = this.mesesRecientes(6);
    const porMes = new Map(meses.map((mes) => [mes.clave, { total: 0, reutilizables: 0 }]));
    let conFecha = 0;

    for (const item of publicados) {
      const clave = this.mesDe(item.fechaCreacion);
      if (!clave) {
        continue;
      }
      conFecha++;
      const bucket = porMes.get(clave);
      if (!bucket) {
        continue;
      }
      bucket.total++;
      if (this.esReutilizable(item.frecuenciaNombre)) {
        bucket.reutilizables++;
      }
    }

    if (conFecha === 0 && publicados.length > 0) {
      return this.graficoFrecuencia(publicados);
    }

    const serie = meses.map((mes) => ({
      etiqueta: mes.etiqueta,
      ...porMes.get(mes.clave)!,
    }));
    const ultimo = serie[serie.length - 1]?.total ?? 0;
    const anterior = serie[serie.length - 2]?.total ?? 0;
    return this.trazar(serie, 'meses', ultimo - anterior, true);
  }

  private graficoFrecuencia(publicados: Conocimiento[]): GraficoEscala {
    const conteo = { diario: 0, frecuente: 0, semanal: 0 };
    for (const item of publicados) {
      const clave = this.claveFrecuencia(item.frecuenciaNombre);
      if (clave === 'diario' || clave === 'frecuente' || clave === 'semanal') {
        conteo[clave]++;
      }
    }
    const serie = [
      { etiqueta: 'Diario', total: conteo.diario, reutilizables: conteo.diario },
      { etiqueta: 'Frecuente', total: conteo.frecuente, reutilizables: conteo.frecuente },
      { etiqueta: 'Semanal', total: conteo.semanal, reutilizables: conteo.semanal },
    ];
    const trazado = this.trazar(serie, 'frecuencia', 0, false);
    return { ...trazado, ultimo: Math.max(...serie.map((punto) => punto.total)) };
  }

  private trazar(
    serie: Array<{ etiqueta: string; total: number; reutilizables: number }>,
    modo: GraficoEscala['modo'],
    delta: number,
    mostrarReuso: boolean,
  ): GraficoEscala {
    const ancho = 800;
    const alto = 320;
    const plotX = 56;
    const plotRight = ancho - 20;
    const padT = 28;
    const padB = 44;
    const base = this.redondear(alto - padB);
    const innerH = base - padT;
    const innerW = plotRight - plotX;
    const { tope, marcas } = this.eje(Math.max(...serie.map((punto) => punto.total), 0));
    const slot = innerW / Math.max(serie.length, 1);
    const barraAncho = 26;
    const separacion = 8;

    const puntos: PuntoGrafico[] = serie.map((punto, indice) => {
      const centro = plotX + slot * indice + slot / 2;
      const grupo = mostrarReuso ? barraAncho * 2 + separacion : barraAncho;
      const inicio = centro - grupo / 2;
      const altoPub = this.altoBarra(punto.total, tope, innerH);
      const altoReuso = this.altoBarra(punto.reutilizables, tope, innerH);
      return {
        etiqueta: punto.etiqueta,
        total: punto.total,
        reutilizables: punto.reutilizables,
        x: this.redondear(centro),
        pubX: this.redondear(inicio),
        pubY: this.redondear(base - altoPub),
        pubH: this.redondear(altoPub),
        pubW: barraAncho,
        reusoX: this.redondear(mostrarReuso ? inicio + barraAncho + separacion : inicio),
        reusoY: this.redondear(base - altoReuso),
        reusoH: this.redondear(altoReuso),
        reusoW: barraAncho,
      };
    });

    return {
      modo,
      mostrarReuso,
      puntos,
      linea: '',
      lineaReuso: '',
      area: '',
      guias: marcas.map((valor) => ({
        valor,
        y: this.redondear(base - (valor / tope) * innerH),
      })),
      ultimo: serie[serie.length - 1]?.total ?? 0,
      delta,
      ancho,
      alto,
      base,
      plotX,
      plotRight,
    };
  }

  private eje(maximo: number): { tope: number; marcas: number[] } {
    const pico = Math.max(maximo, 1);
    const pasos = [1, 2, 5, 10, 20, 25, 50, 100, 200, 500];
    let paso = pasos[pasos.length - 1];
    for (const candidato of pasos) {
      if (pico / candidato <= 4) {
        paso = candidato;
        break;
      }
    }
    const tope = Math.ceil(pico / paso) * paso;
    const marcas: number[] = [];
    for (let valor = 0; valor <= tope; valor += paso) {
      marcas.push(valor);
    }
    return { tope, marcas };
  }

  private altoBarra(valor: number, tope: number, innerH: number): number {
    if (valor <= 0 || tope <= 0) {
      return 0;
    }
    return Math.max(8, (valor / tope) * innerH);
  }

  private mesesRecientes(cantidad: number): Array<{ clave: string; etiqueta: string }> {
    const nombres = [
      'Enero',
      'Febrero',
      'Marzo',
      'Abril',
      'Mayo',
      'Junio',
      'Julio',
      'Agosto',
      'Septiembre',
      'Octubre',
      'Noviembre',
      'Diciembre',
    ];
    const ahora = new Date();
    const meses: Array<{ clave: string; etiqueta: string }> = [];
    for (let i = cantidad - 1; i >= 0; i--) {
      const fecha = new Date(ahora.getFullYear(), ahora.getMonth() - i, 1);
      const mes = fecha.getMonth();
      meses.push({
        clave: `${fecha.getFullYear()}-${String(mes + 1).padStart(2, '0')}`,
        etiqueta: nombres[mes],
      });
    }
    return meses;
  }

  private mesDe(fecha: string | null): string | null {
    if (!fecha) {
      return null;
    }
    const match = /^(\d{4})-(\d{2})/.exec(fecha.trim());
    return match ? `${match[1]}-${match[2]}` : null;
  }

  private esReutilizable(nombre: string | null | undefined): boolean {
    const clave = this.claveFrecuencia(nombre);
    return clave === 'diario' || clave === 'semanal' || clave === 'frecuente';
  }

  private redondear(valor: number): number {
    return Math.round(valor * 10) / 10;
  }

  private claveFrecuencia(nombre: string | null | undefined): ClaveFrecuencia | 'sin' {
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

  private readSession(): InicioDashboard | null {
    try {
      const raw = sessionStorage.getItem(this.storageKey) ?? localStorage.getItem(this.storageKey);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as InicioDashboard;
      return parsed && Array.isArray(parsed.frecuentes) ? parsed : null;
    } catch {
      return null;
    }
  }

  private writeSession(data: InicioDashboard): void {
    try {
      const raw = JSON.stringify(data);
      sessionStorage.setItem(this.storageKey, raw);
      localStorage.setItem(this.storageKey, raw);
    } catch {
      // ignore
    }
  }
}
