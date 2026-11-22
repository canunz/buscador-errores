import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TicketsPorUsuario } from '../../core/models/ticket.model';
import { TicketService } from '../../core/services/ticket.service';

/** Colores de los avatares; se repiten si hay más personas que colores. */
const COLORES = ['#1e4fd6', '#7c5cf0', '#12a089', '#f2761f', '#e0457b', '#0ea5e9', '#64748b'];

/**
 * Panel del administrador: cuántos tickets ha creado cada persona.
 * A la izquierda va el ranking con una barra por usuario (resueltos en verde, activos en naranjo).
 * A la derecha, un resumen con el porcentaje de tickets resueltos y quién reporta más.
 */
@Component({
  selector: 'app-tickets-por-usuario',
  standalone: true,
  template: `
    <section class="tpu" aria-labelledby="tpu-titulo">
      <header class="tpu-cabecera">
        <div>
          <p class="tpu-eyebrow">Actividad</p>
          <h2 id="tpu-titulo">Tickets por usuario</h2>
          <p class="tpu-bajada">Quién está reportando problemas y cómo van sus casos.</p>
        </div>
        @if (filas().length) {
          <div class="tpu-pills">
            <span class="tpu-pill">{{ totalTickets() }} tickets</span>
            <span class="tpu-pill suave">{{ filas().length }} usuarios</span>
          </div>
        }
      </header>

      @if (cargando()) {
        <div class="tpu-esqueleto" aria-hidden="true">
          @for (i of [1, 2, 3, 4]; track i) { <span></span> }
        </div>
      } @else if (error()) {
        <p class="tpu-estado error" role="alert">
          {{ error() }}
          <button type="button" (click)="cargar()">Reintentar</button>
        </p>
      } @else if (!filas().length) {
        <p class="tpu-estado">Todavía nadie ha creado tickets.</p>
      } @else {
        <div class="tpu-cuerpo">
          <div class="tpu-ranking">
            <div class="tpu-columnas" aria-hidden="true">
              <span>Usuario</span>
              <span>Tickets</span>
            </div>
            <ol class="tpu-lista">
              @for (fila of visibles(); track fila.usuarioId; let i = $index) {
                <li [style.animation-delay.ms]="i * 50">
                  <span class="tpu-puesto" [class.top]="i < 3">{{ i + 1 }}</span>
                  <span class="tpu-avatar" [style.background]="color(i)" aria-hidden="true">{{ iniciales(fila.nombre) }}</span>
                  <div class="tpu-info">
                    <div class="tpu-linea">
                      <strong [title]="fila.email">{{ fila.nombre }}</strong>
                      <span class="tpu-total">{{ fila.total }}</span>
                    </div>
                    <span class="tpu-pista" [attr.aria-label]="fila.activos + ' activos y ' + fila.resueltos + ' resueltos'">
                      <span class="tpu-tramo resuelto" [style.width.%]="ancho(fila.resueltos)"></span>
                      <span class="tpu-tramo activo" [style.width.%]="ancho(fila.activos)"></span>
                    </span>
                    <span class="tpu-detalle">
                      <span><i class="punto activo"></i>{{ fila.activos }} activos</span>
                      <span><i class="punto resuelto"></i>{{ fila.resueltos }} resueltos</span>
                    </span>
                  </div>
                </li>
              }
            </ol>

            @if (filas().length > limite) {
              <button type="button" class="tpu-ver-mas" (click)="verTodos.set(!verTodos())">
                {{ verTodos() ? 'Ver menos' : 'Ver los ' + filas().length + ' usuarios' }}
              </button>
            }
          </div>

          <aside class="tpu-resumen" aria-label="Resumen de tickets">
            <div class="tpu-anillo" [style.--avance]="porcentajeResueltos()">
              <div class="tpu-anillo-centro">
                <strong>{{ porcentajeResueltos() }}%</strong>
                <span>resueltos</span>
              </div>
            </div>

            <dl class="tpu-cifras">
              <div>
                <dt><i class="punto resuelto"></i>Resueltos o cerrados</dt>
                <dd>{{ totalResueltos() }}</dd>
              </div>
              <div>
                <dt><i class="punto activo"></i>Activos</dt>
                <dd>{{ totalActivos() }}</dd>
              </div>
              <div>
                <dt>Promedio por usuario</dt>
                <dd>{{ promedio() }}</dd>
              </div>
            </dl>

            @if (primero(); as top) {
              <div class="tpu-destacado">
                <span class="tpu-avatar grande" [style.background]="color(0)" aria-hidden="true">{{ iniciales(top.nombre) }}</span>
                <div>
                  <small>Quien más reporta</small>
                  <strong>{{ top.nombre }}</strong>
                  <span>{{ top.total }} tickets · {{ participacion(top.total) }}% del total</span>
                </div>
              </div>
            }
          </aside>
        </div>
      }
    </section>
  `,
  styles: [`
    :host {
      display: block;
      margin-bottom: 28px;
    }

    .tpu {
      padding: 24px;
      border: 1px solid #e3e8f2;
      border-radius: 22px;
      background: #fff;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 12px 32px rgba(15, 23, 42, 0.06);
    }

    /* ---------- Cabecera ---------- */

    .tpu-cabecera {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px 20px;
      margin-bottom: 20px;
    }

    .tpu-eyebrow {
      margin: 0 0 4px;
      color: #1e4fd6;
      font-size: 11.5px;
      font-weight: 700;
      letter-spacing: 0.1em;
      text-transform: uppercase;
    }

    h2 {
      margin: 0;
      color: #0b1b4d;
      font-size: 19px;
      font-weight: 750;
      letter-spacing: -0.02em;
    }

    .tpu-bajada {
      margin: 4px 0 0;
      color: #64748b;
      font-size: 13.5px;
    }

    .tpu-pills {
      display: flex;
      gap: 8px;
    }

    .tpu-pill {
      padding: 6px 12px;
      border-radius: 999px;
      background: #1e4fd6;
      color: #fff;
      font-size: 12.5px;
      font-weight: 650;
      white-space: nowrap;
    }

    .tpu-pill.suave {
      background: #eef3ff;
      color: #1e4fd6;
    }

    /* ---------- Cuerpo en dos columnas ---------- */

    .tpu-cuerpo {
      display: grid;
      grid-template-columns: minmax(0, 1.7fr) minmax(260px, 1fr);
      gap: 24px;
      align-items: start;
    }

    .tpu-columnas {
      display: flex;
      justify-content: space-between;
      padding: 0 4px 8px 48px;
      border-bottom: 1px solid #eef2f7;
      color: #94a3b8;
      font-size: 11.5px;
      font-weight: 650;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    .tpu-lista {
      list-style: none;
      margin: 0;
      padding: 0;
    }

    .tpu-lista li {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 4px;
      border-bottom: 1px solid #f1f4f9;
      animation: aparecer 0.4s ease both;
    }

    .tpu-lista li:last-child {
      border-bottom: 0;
    }

    .tpu-puesto {
      flex-shrink: 0;
      width: 24px;
      color: #94a3b8;
      font-size: 13px;
      font-weight: 700;
      text-align: center;
    }

    .tpu-puesto.top {
      color: #1e4fd6;
    }

    .tpu-avatar {
      flex-shrink: 0;
      display: grid;
      place-items: center;
      width: 38px;
      height: 38px;
      border-radius: 12px;
      color: #fff;
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 0.02em;
    }

    .tpu-avatar.grande {
      width: 46px;
      height: 46px;
      border-radius: 14px;
      font-size: 15px;
    }

    .tpu-info {
      flex: 1;
      min-width: 0;
    }

    .tpu-linea {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 10px;
      margin-bottom: 7px;
    }

    .tpu-linea strong {
      overflow: hidden;
      color: #0b1b4d;
      font-size: 14.5px;
      font-weight: 650;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .tpu-total {
      color: #0b1b4d;
      font-size: 17px;
      font-weight: 800;
      font-variant-numeric: tabular-nums;
    }

    .tpu-pista {
      display: flex;
      gap: 2px;
      height: 8px;
      border-radius: 999px;
      background: #eef2f7;
      overflow: hidden;
    }

    .tpu-tramo {
      height: 100%;
      animation: crecer 0.9s cubic-bezier(0.22, 1, 0.36, 1) both;
      transform-origin: left;
    }

    .tpu-tramo.resuelto {
      border-radius: 999px 0 0 999px;
    }

    .tpu-tramo.activo {
      border-radius: 0 999px 999px 0;
    }

    .resuelto {
      background: #12a089;
    }

    .activo {
      background: #f59e0b;
    }

    .tpu-detalle {
      display: flex;
      gap: 14px;
      margin-top: 6px;
      color: #64748b;
      font-size: 12px;
    }

    .punto {
      display: inline-block;
      width: 8px;
      height: 8px;
      margin-right: 6px;
      border-radius: 50%;
      vertical-align: 1px;
    }

    .tpu-ver-mas {
      width: 100%;
      margin-top: 10px;
      padding: 10px 14px;
      border: 1px dashed #c9d5ee;
      border-radius: 12px;
      background: #f8faff;
      color: #1e4fd6;
      font-weight: 650;
      cursor: pointer;
      transition: background 0.15s ease;
    }

    .tpu-ver-mas:hover {
      background: #eef3ff;
    }

    /* ---------- Resumen con anillo ---------- */

    .tpu-resumen {
      display: grid;
      gap: 18px;
      padding: 22px;
      border-radius: 18px;
      background: linear-gradient(160deg, #f6f9ff 0%, #eef3ff 100%);
      border: 1px solid #e3eafb;
    }

    .tpu-anillo {
      --avance: 0;
      justify-self: center;
      display: grid;
      place-items: center;
      width: 150px;
      height: 150px;
      border-radius: 50%;
      background: conic-gradient(#12a089 calc(var(--avance) * 1%), #f59e0b 0);
    }

    .tpu-anillo-centro {
      display: grid;
      place-items: center;
      align-content: center;
      width: 112px;
      height: 112px;
      border-radius: 50%;
      background: #fff;
      box-shadow: inset 0 0 0 1px #eef2f7;
    }

    .tpu-anillo-centro strong {
      color: #0b1b4d;
      font-size: 28px;
      font-weight: 800;
      line-height: 1;
    }

    .tpu-anillo-centro span {
      margin-top: 4px;
      color: #64748b;
      font-size: 12.5px;
    }

    .tpu-cifras {
      display: grid;
      gap: 10px;
      margin: 0;
    }

    .tpu-cifras div {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-bottom: 10px;
      border-bottom: 1px solid #dfe7f8;
    }

    .tpu-cifras div:last-child {
      padding-bottom: 0;
      border-bottom: 0;
    }

    .tpu-cifras dt {
      color: #475569;
      font-size: 13.5px;
    }

    .tpu-cifras dd {
      margin: 0;
      color: #0b1b4d;
      font-size: 16px;
      font-weight: 750;
      font-variant-numeric: tabular-nums;
    }

    .tpu-destacado {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px;
      border-radius: 14px;
      background: #fff;
      box-shadow: 0 6px 18px rgba(30, 79, 214, 0.08);
    }

    .tpu-destacado div {
      display: grid;
      min-width: 0;
    }

    .tpu-destacado small {
      color: #1e4fd6;
      font-size: 11.5px;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    .tpu-destacado strong {
      overflow: hidden;
      color: #0b1b4d;
      font-size: 15px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .tpu-destacado div > span {
      color: #64748b;
      font-size: 12.5px;
    }

    /* ---------- Estados ---------- */

    .tpu-estado {
      margin: 0;
      padding: 28px 16px;
      border: 1px dashed #d6deec;
      border-radius: 14px;
      color: #64748b;
      text-align: center;
    }

    .tpu-estado.error {
      color: #b42318;
    }

    .tpu-estado button {
      margin-left: 8px;
    }

    .tpu-esqueleto {
      display: grid;
      gap: 12px;
    }

    .tpu-esqueleto span {
      height: 46px;
      border-radius: 12px;
      background: linear-gradient(90deg, #f1f4f9 25%, #e8edf5 50%, #f1f4f9 75%);
      background-size: 200% 100%;
      animation: brillo 1.2s linear infinite;
    }

    @keyframes crecer {
      from { transform: scaleX(0); }
      to { transform: scaleX(1); }
    }

    @keyframes aparecer {
      from { opacity: 0; transform: translateY(6px); }
      to { opacity: 1; transform: none; }
    }

    @keyframes brillo {
      from { background-position: 200% 0; }
      to { background-position: -200% 0; }
    }

    /* En pantallas chicas el resumen pasa abajo del ranking. */
    @media (max-width: 900px) {
      .tpu-cuerpo {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 480px) {
      .tpu {
        padding: 18px;
      }

      .tpu-columnas {
        padding-left: 4px;
      }

      .tpu-puesto {
        display: none;
      }
    }
  `],
})
export class TicketsPorUsuarioComponent implements OnInit {
  private readonly tickets = inject(TicketService);

  /** Cuántas personas se muestran antes de apretar "Ver todos". */
  readonly limite = 6;

  readonly filas = signal<TicketsPorUsuario[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly verTodos = signal(false);

  readonly visibles = computed(() => (this.verTodos() ? this.filas() : this.filas().slice(0, this.limite)));
  readonly totalTickets = computed(() => this.filas().reduce((suma, fila) => suma + fila.total, 0));
  readonly totalActivos = computed(() => this.filas().reduce((suma, fila) => suma + fila.activos, 0));
  readonly totalResueltos = computed(() => this.filas().reduce((suma, fila) => suma + fila.resueltos, 0));
  readonly primero = computed(() => this.filas()[0] ?? null);

  /** Porcentaje de tickets resueltos o cerrados sobre el total. */
  readonly porcentajeResueltos = computed(() =>
    this.totalTickets() ? Math.round((this.totalResueltos() / this.totalTickets()) * 100) : 0,
  );

  /** Tickets promedio por persona, con un decimal. */
  readonly promedio = computed(() =>
    this.filas().length ? (this.totalTickets() / this.filas().length).toFixed(1).replace('.', ',') : '0',
  );

  /** La barra más larga es la de quien tiene más tickets; las demás se miden contra ella. */
  private readonly maximo = computed(() => Math.max(1, ...this.filas().map((fila) => fila.total)));

  ngOnInit(): void {
    this.cargar();
  }

  // Pide los datos al backend.
  cargar(): void {
    this.cargando.set(true);
    this.error.set('');
    this.tickets.porUsuario().subscribe({
      next: (filas) => {
        this.filas.set(filas);
        this.cargando.set(false);
      },
      error: (e: Error) => {
        this.error.set(e.message);
        this.cargando.set(false);
      },
    });
  }

  // Porcentaje de la barra respecto a quien tiene más tickets.
  ancho(cantidad: number): number {
    return (cantidad / this.maximo()) * 100;
  }

  // Parte del total que corresponde a una persona.
  participacion(cantidad: number): number {
    return this.totalTickets() ? Math.round((cantidad / this.totalTickets()) * 100) : 0;
  }

  // Color del avatar según la posición en el ranking.
  color(posicion: number): string {
    return COLORES[posicion % COLORES.length];
  }

  // Iniciales del nombre y apellido para el avatar (ej. "Catalina Núñez" → "CN").
  iniciales(nombre: string): string {
    const partes = (nombre ?? '').trim().split(/\s+/).filter(Boolean);
    const letras = partes.length > 1 ? partes[0][0] + partes[1][0] : (partes[0] ?? '?').slice(0, 2);
    return letras.toUpperCase();
  }
}
