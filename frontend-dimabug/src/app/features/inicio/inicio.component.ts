import { DatePipe } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Conocimiento } from '../../core/models/conocimiento.model';
import { AuthService } from '../../core/services/auth.service';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { FavoritosService } from '../../core/services/favoritos.service';
import { EscalaConocimiento, FrecuenteItem, InicioDashboard, InicioService } from '../../core/services/inicio.service';
import { primerNombre } from '../../core/models/usuario.model';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
import { TendenciaLineaComponent } from './tendencia-linea.component';

interface Kpi {
  clave: 'unicos' | 'diario' | 'semanal' | 'frecuente';
  etiqueta: string;
  detalle: string;
  valor: number;
  mostrado: number;
  porcentaje: number;
}

const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, TendenciaLineaComponent, LoadingModalComponent],
  templateUrl: './inicio.component.html',
  styleUrl: './inicio.component.css',
})
export class InicioComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly inicioApi = inject(InicioService);
  private readonly conocimientos = inject(ConocimientoService);
  private readonly favoritosApi = inject(FavoritosService);
  private animacion = 0;

  readonly primerNombre = computed(() => primerNombre(this.auth.usuario()?.usuarioNombre));
  readonly isAdmin = this.auth.isAdmin;
  readonly isSoporte = this.auth.isSoporte;
  readonly isStaff = this.auth.isStaff;
  readonly rolVista = computed(() => {
    if (this.isAdmin()) {
      return 'admin';
    }
    if (this.isSoporte()) {
      return 'soporte';
    }
    return 'usuario';
  });
  readonly favoritosCount = computed(() => this.favoritosApi.items().length);
  readonly kpis = signal<Kpi[]>([]);

  readonly saludoHora = InicioComponent.saludoSegunHora(new Date().getHours());
  readonly hoyTexto = InicioComponent.fechaLarga(new Date());
  readonly sugerencias = ['Impresora fuera de línea', 'Jadima', 'POS no imprime', 'Sin red en sucursal'];

  dashboard: InicioDashboard | null = this.inicioApi.dashboardAhora();
  error = '';
  coincidencias: Conocimiento[] = [];
  panelAbierto = false;

  searchForm = this.fb.nonNullable.group({
    q: [''],
  });

  ngOnInit(): void {
    this.conocimientos.listar().subscribe({
      next: () => this.actualizarCoincidencias(),
      error: () => undefined,
    });
    if (!this.isStaff()) {
      this.dashboard = {
        conocimientos: 0,
        procedimientos: 0,
        ultimaActualizacion: null,
        frecuentes: [],
        escala: null,
      };
      return;
    }
    if (this.dashboard?.escala) {
      this.mostrarKpis(this.dashboard.escala, false);
    }
    this.inicioApi.dashboard().subscribe({
      next: (data) => {
        this.dashboard = data;
        if (data.escala) {
          this.mostrarKpis(data.escala, false);
        }
      },
      error: () => {
        if (!this.dashboard) {
          this.error = 'No se pudo cargar el panel operativo.';
        }
      },
    });
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.animacion);
  }

  buscar(): void {
    this.panelAbierto = false;
    const q = this.searchForm.controls.q.value.trim();
    void this.router.navigate(['/conocimiento'], {
      queryParams: q ? { q } : {},
    });
  }

  get consulta(): string {
    return this.searchForm.controls.q.value.trim();
  }

  actualizarCoincidencias(): void {
    const q = this.consulta;
    this.coincidencias = q.length >= 1 ? this.conocimientos.parecidos(q, 6) : [];
    this.panelAbierto = q.length >= 1;
  }

  partes(titulo: string): { texto: string; marca: boolean }[] {
    const q = this.consulta;
    const seguro = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const hallazgo = titulo.match(new RegExp(seguro, 'i'));
    if (!hallazgo || hallazgo.index == null) {
      return [{ texto: titulo, marca: false }];
    }
    const inicio = hallazgo.index;
    const fin = inicio + hallazgo[0].length;
    return [
      { texto: titulo.slice(0, inicio), marca: false },
      { texto: titulo.slice(inicio, fin), marca: true },
      { texto: titulo.slice(fin), marca: false },
    ].filter((parte) => parte.texto.length > 0);
  }

  abrirCoincidencia(item: Conocimiento): void {
    this.panelAbierto = false;
    this.conocimientos.precargarGuia(item.id, true);
    void this.router.navigate(['/conocimiento', item.id]);
  }

  @HostListener('document:click', ['$event'])
  cerrarCoincidencias(event: Event): void {
    const destino = event.target;
    if (!(destino instanceof HTMLElement) || !destino.closest('.buscador-caja')) {
      this.panelAbierto = false;
    }
  }

  buscarSugerencia(texto: string): void {
    this.searchForm.controls.q.setValue(texto);
    this.buscar();
  }

  ir(url: string): void {
    this.router.navigateByUrl(url);
  }

  irFrecuente(item: FrecuenteItem): void {
    if (item.tipo === 'HARDWARE') {
      this.router.navigateByUrl('/plataforma/hardware');
      return;
    }
    if (item.tipo === 'PROCEDIMIENTO') {
      this.router.navigate(['/procedimientos'], { queryParams: { q: item.titulo } });
      return;
    }
    this.router.navigate(['/conocimiento'], { queryParams: { q: item.titulo } });
  }

  irCaso(id: number): void {
    void this.router.navigate(['/conocimiento', id]);
  }

  private mostrarKpis(escala: EscalaConocimiento, animar: boolean): void {
    const base = Math.max(escala.casosUnicos, 1);
    const porcentaje = (valor: number) => Math.min(100, Math.round((valor / base) * 100));
    const kpis: Kpi[] = [
      {
        clave: 'unicos',
        etiqueta: 'Casos únicos',
        detalle: 'Un conocimiento publicado por cada problema.',
        valor: escala.casosUnicos,
        mostrado: 0,
        porcentaje: 100,
      },
      {
        clave: 'diario',
        etiqueta: 'Diario',
        detalle: 'Casos que pueden fallar todos los días.',
        valor: escala.diario,
        mostrado: 0,
        porcentaje: porcentaje(escala.diario),
      },
      {
        clave: 'semanal',
        etiqueta: 'Semanal',
        detalle: 'Se repiten alguna vez en la semana.',
        valor: escala.semanal,
        mostrado: 0,
        porcentaje: porcentaje(escala.semanal),
      },
      {
        clave: 'frecuente',
        etiqueta: 'Frecuente',
        detalle: 'Ocurren seguido; se reutiliza la misma guía.',
        valor: escala.frecuente,
        mostrado: 0,
        porcentaje: porcentaje(escala.frecuente),
      },
    ];

    const sinMovimiento = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (!animar || sinMovimiento) {
      this.kpis.set(kpis.map((kpi) => ({ ...kpi, mostrado: kpi.valor })));
      return;
    }

    cancelAnimationFrame(this.animacion);
    const inicio = performance.now();
    const duracion = 900;
    const paso = (ahora: number) => {
      const avance = Math.min(1, (ahora - inicio) / duracion);
      const suave = 1 - Math.pow(1 - avance, 3);
      this.kpis.set(kpis.map((kpi) => ({ ...kpi, mostrado: Math.round(kpi.valor * suave) })));
      if (avance < 1) {
        this.animacion = requestAnimationFrame(paso);
      }
    };
    this.animacion = requestAnimationFrame(paso);
  }

  private static saludoSegunHora(hora: number): string {
    if (hora < 12) {
      return 'Buenos días';
    }
    if (hora < 20) {
      return 'Buenas tardes';
    }
    return 'Buenas noches';
  }

  private static fechaLarga(fecha: Date): string {
    const dia = DIAS[fecha.getDay()];
    return `${dia.charAt(0).toUpperCase()}${dia.slice(1)} ${fecha.getDate()} de ${MESES[fecha.getMonth()]}`;
  }
}
