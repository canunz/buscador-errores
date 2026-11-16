import { Component, DestroyRef, Input, OnChanges, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { AdjuntoTicket } from '../../../core/models/ticket.model';
import { TicketService } from '../../../core/services/ticket.service';

/** Muestra los archivos de un ticket: miniaturas, video y PDF, con vista ampliada. */
@Component({
  selector: 'app-galeria-adjuntos',
  standalone: true,
  template: `
    @if (adjuntos.length) {
      <ul class="ga" [class.compacta]="compacta">
        @for (a of adjuntos; track a.id) {
          <li>
            <button type="button" class="ga-item" (click)="abrir(a)" [title]="a.nombre">
              @if (a.tipo === 'IMAGEN') {
                @if (urls[a.id]) {
                  <img [src]="urls[a.id]" [alt]="a.nombre" loading="lazy" />
                } @else {
                  <span class="ga-cargando"></span>
                }
              } @else if (a.tipo === 'VIDEO') {
                <span class="ga-video">
                  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z" /></svg>
                </span>
              } @else {
                <span class="ga-pdf">PDF</span>
              }
              <span class="ga-pie">
                <strong>{{ a.nombre }}</strong>
                @if (a.tamano) {
                  <small>{{ peso(a.tamano) }}</small>
                }
              </span>
            </button>
          </li>
        }
      </ul>
    }

    @if (abierto) {
      <div class="ga-capa" (click)="cerrar()" role="dialog" aria-modal="true" [attr.aria-label]="abierto.nombre">
        <div class="ga-visor" (click)="$event.stopPropagation()">
          <header>
            <strong>{{ abierto.nombre }}</strong>
            <div>
              @if (urlAbierta) {
                <a [href]="urlAbierta" [attr.download]="abierto.nombre">Descargar</a>
              }
              <button type="button" (click)="cerrar()" aria-label="Cerrar">×</button>
            </div>
          </header>
          <div class="ga-contenido">
            @if (!urlAbierta) {
              <span class="ga-cargando grande"></span>
            } @else if (abierto.tipo === 'IMAGEN') {
              <img [src]="urlAbierta" [alt]="abierto.nombre" />
            } @else if (abierto.tipo === 'VIDEO') {
              <video [src]="urlAbierta" controls autoplay playsinline></video>
            } @else {
              <iframe [src]="pdfSeguro" title="Documento PDF"></iframe>
            }
          </div>
        </div>
      </div>
    }
    @if (error) {
      <p class="ga-error">{{ error }}</p>
    }
  `,
  styles: `
    .ga { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; margin: 0; padding: 0; list-style: none; }
    .ga.compacta { grid-template-columns: repeat(auto-fill, minmax(110px, 140px)); margin-top: 8px; }
    .ga-item {
      display: flex; flex-direction: column; width: 100%; padding: 0; overflow: hidden;
      border: 1px solid #e7e9f0; border-radius: 12px; background: #fff; font: inherit; text-align: left; cursor: pointer;
      transition: transform .12s ease, box-shadow .15s ease;
    }
    .ga-item:hover { transform: translateY(-2px); box-shadow: 0 10px 22px rgba(15, 23, 42, .12); }
    .ga-item img, .ga-video, .ga-pdf, .ga-item .ga-cargando { width: 100%; height: 96px; object-fit: cover; }
    .ga.compacta .ga-item img, .ga.compacta .ga-video, .ga.compacta .ga-pdf, .ga.compacta .ga-cargando { height: 78px; }
    .ga-video { display: grid; place-items: center; background: linear-gradient(135deg, #0b1b4d, #1e4fd6); color: #fff; }
    .ga-video svg { width: 30px; height: 30px; padding: 7px; border-radius: 50%; background: rgba(255,255,255,.18); }
    .ga-pdf { display: grid; place-items: center; background: #fee2e2; color: #b91c1c; font-weight: 800; letter-spacing: .05em; }
    .ga-pie { display: flex; flex-direction: column; padding: 7px 9px; min-width: 0; }
    .ga-pie strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; font-weight: 600; color: #1d2433; }
    .ga-pie small { color: #667085; font-size: 11px; }
    .ga-cargando { display: block; background: linear-gradient(90deg, #eef1f7, #f8fafc, #eef1f7); background-size: 200% 100%; animation: ga-brillo 1.2s linear infinite; }
    .ga-cargando.grande { width: 120px; height: 120px; border-radius: 16px; }
    @keyframes ga-brillo { from { background-position: 200% 0; } to { background-position: -200% 0; } }
    .ga-capa { position: fixed; inset: 0; z-index: 2000; display: grid; place-items: center; padding: 20px; background: rgba(7, 14, 38, .78); backdrop-filter: blur(4px); }
    .ga-visor { display: flex; flex-direction: column; width: min(1000px, 100%); max-height: calc(100vh - 40px); border-radius: 18px; overflow: hidden; background: #0b1324; }
    .ga-visor header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; color: #fff; }
    .ga-visor header strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
    .ga-visor header div { display: flex; align-items: center; gap: 12px; }
    .ga-visor header a { color: #93c5fd; font-size: 13px; font-weight: 700; text-decoration: none; }
    .ga-visor header button { border: 0; background: rgba(255,255,255,.12); color: #fff; width: 32px; height: 32px; border-radius: 50%; font-size: 20px; cursor: pointer; }
    .ga-contenido { display: grid; place-items: center; min-height: 240px; padding: 0 16px 16px; overflow: auto; }
    .ga-contenido img, .ga-contenido video { max-width: 100%; max-height: calc(100vh - 140px); border-radius: 10px; }
    .ga-contenido iframe { width: 100%; height: calc(100vh - 140px); border: 0; border-radius: 10px; background: #fff; }
    .ga-error { margin: 6px 0 0; color: #b91c1c; font-size: 12.5px; }
    :host-context(.tema-oscuro) .ga-item { background: #111a2e; border-color: #1f2a3d; }
    :host-context(.tema-oscuro) .ga-pie strong { color: #e5e7eb; }
  `,
})
export class GaleriaAdjuntosComponent implements OnChanges {
  private readonly tickets = inject(TicketService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) ticketId!: number;
  @Input() adjuntos: AdjuntoTicket[] = [];
  @Input() compacta = false;

  urls: Record<number, string> = {};
  abierto: AdjuntoTicket | null = null;
  urlAbierta: string | null = null;
  pdfSeguro: SafeUrl | null = null;
  error = '';
  private readonly pendientes: AdjuntoTicket[] = [];
  private enCurso = 0;

  ngOnChanges(): void {
    for (const a of this.adjuntos.filter((x) => x.tipo === 'IMAGEN' && !this.urls[x.id])) {
      if (!this.pendientes.some((p) => p.id === a.id)) this.pendientes.push(a);
    }
    this.bombear();
  }

  private bombear(): void {
    while (this.enCurso < 2 && this.pendientes.length) {
      const a = this.pendientes.shift();
      if (!a || this.urls[a.id]) continue;
      this.enCurso++;
      this.cargar(a, (url) => (this.urls[a.id] = url), true);
    }
  }

  abrir(a: AdjuntoTicket): void {
    this.abierto = a;
    this.urlAbierta = this.urls[a.id] ?? null;
    this.pdfSeguro = null;
    if (!this.urlAbierta) {
      this.cargar(a, (url) => {
        if (this.abierto?.id !== a.id) return;
        this.urlAbierta = url;
        this.pdfSeguro = this.sanitizer.bypassSecurityTrustResourceUrl(url);
      }, false);
    } else {
      this.pdfSeguro = this.sanitizer.bypassSecurityTrustResourceUrl(this.urlAbierta);
    }
  }

  cerrar(): void {
    this.abierto = null;
    this.urlAbierta = null;
  }

  peso(bytes: number): string {
    return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }

  private cargar(a: AdjuntoTicket, listo: (url: string) => void, deCola: boolean): void {
    this.tickets
      .archivo(this.ticketId, a.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob.type ? blob : new Blob([blob], { type: a.mime }));
          this.urls[a.id] = url;
          listo(url);
          if (deCola) this.liberar();
        },
        error: () => {
          this.error = `No se pudo abrir «${a.nombre}».`;
          if (deCola) this.liberar();
        },
      });
  }

  private liberar(): void {
    this.enCurso = Math.max(0, this.enCurso - 1);
    this.bombear();
  }
}
