import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { ADJUNTO_ACEPTA, ADJUNTO_MAX_BYTES } from '../../../core/models/ticket.model';

interface Vista {
  archivo: File;
  url: string | null;
  tipo: 'imagen' | 'video' | 'pdf';
}

const TIPOS = ADJUNTO_ACEPTA.split(',');

/** Botón para elegir imágenes, videos o PDF, con vista previa antes de enviar. */
@Component({
  selector: 'app-selector-adjuntos',
  standalone: true,
  template: `
    <div class="sa" [class.arrastrando]="arrastrando"
      (dragover)="$event.preventDefault(); arrastrando = true"
      (dragleave)="arrastrando = false"
      (drop)="soltar($event)">
      <label class="sa-boton" [class.deshabilitado]="deshabilitado">
        <input type="file" multiple [accept]="acepta" [disabled]="deshabilitado" (change)="elegir($event)" />
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path d="M21.4 11.1l-8.5 8.5a5.5 5.5 0 01-7.8-7.8l8.5-8.5a3.7 3.7 0 015.2 5.2l-8.5 8.5a1.8 1.8 0 01-2.6-2.6l7.8-7.8" />
        </svg>
        {{ texto }}
      </label>
      <span class="sa-ayuda">Imágenes, videos o PDF · hasta 25 MB c/u</span>

      @if (vistas.length) {
        <ul class="sa-lista">
          @for (v of vistas; track v.archivo; let i = $index) {
            <li>
              @if (v.tipo === 'imagen' && v.url) {
                <img [src]="v.url" alt="" />
              } @else {
                <span class="sa-icono" [class.video]="v.tipo === 'video'">
                  @if (v.tipo === 'video') {
                    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z" /></svg>
                  } @else {
                    PDF
                  }
                </span>
              }
              <span class="sa-nombre">
                <strong>{{ v.archivo.name }}</strong>
                <small>{{ peso(v.archivo.size) }}</small>
              </span>
              <button type="button" (click)="quitar(i)" [disabled]="deshabilitado" [attr.aria-label]="'Quitar ' + v.archivo.name">×</button>
            </li>
          }
        </ul>
      }
      @if (error) {
        <p class="sa-error" role="alert">{{ error }}</p>
      }
    </div>
  `,
  styles: `
    .sa { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 12px; border-radius: 12px; }
    .sa.arrastrando { outline: 2px dashed #1e4fd6; outline-offset: 4px; }
    .sa-boton {
      display: inline-flex; align-items: center; gap: 7px; height: 36px; padding: 0 12px;
      border: 1px dashed #b6c6f5; border-radius: 10px; background: #f5f8ff; color: #1e4fd6;
      font-size: 13.5px; font-weight: 600; cursor: pointer; transition: background .15s ease, border-color .15s ease;
    }
    .sa-boton:hover { background: #eaf0fe; border-color: #1e4fd6; }
    .sa-boton.deshabilitado { opacity: .55; cursor: not-allowed; }
    .sa-boton input { display: none; }
    .sa-boton svg { width: 17px; height: 17px; }
    .sa-ayuda { color: #667085; font-size: 12px; }
    .sa-lista { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 8px; width: 100%; margin: 4px 0 0; padding: 0; list-style: none; }
    .sa-lista li { display: flex; align-items: center; gap: 10px; padding: 6px 8px 6px 6px; border: 1px solid #e7e9f0; border-radius: 12px; background: #fff; }
    .sa-lista img, .sa-icono { flex-shrink: 0; width: 42px; height: 42px; border-radius: 9px; object-fit: cover; }
    .sa-icono { display: grid; place-items: center; background: #fee2e2; color: #b91c1c; font-size: 11px; font-weight: 800; }
    .sa-icono.video { background: #0b1b4d; color: #fff; }
    .sa-icono svg { width: 18px; height: 18px; }
    .sa-nombre { display: flex; flex-direction: column; min-width: 0; flex: 1; }
    .sa-nombre strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12.5px; font-weight: 600; color: #1d2433; }
    .sa-nombre small { color: #667085; font-size: 11.5px; }
    .sa-lista button { border: 0; background: none; color: #94a3b8; font-size: 20px; line-height: 1; cursor: pointer; }
    .sa-lista button:hover { color: #b91c1c; }
    .sa-error { width: 100%; margin: 2px 0 0; color: #b91c1c; font-size: 12.5px; font-weight: 600; }
    :host-context(.tema-oscuro) .sa-boton { background: #16264d; border-color: #2c4a99; color: #93c5fd; }
    :host-context(.tema-oscuro) .sa-lista li { background: #111a2e; border-color: #1f2a3d; }
    :host-context(.tema-oscuro) .sa-nombre strong { color: #e5e7eb; }
  `,
})
export class SelectorAdjuntosComponent implements OnDestroy {
  @Input() texto = 'Adjuntar archivos';
  @Input() deshabilitado = false;
  @Input() set archivos(lista: File[]) {
    if (lista === this.actuales) return;
    this.liberar();
    this.actuales = lista ?? [];
    this.vistas = this.actuales.map((archivo) => this.crearVista(archivo));
  }
  @Output() archivosChange = new EventEmitter<File[]>();

  readonly acepta = ADJUNTO_ACEPTA;
  vistas: Vista[] = [];
  error = '';
  arrastrando = false;
  private actuales: File[] = [];

  elegir(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    this.agregar(Array.from(input.files ?? []));
    input.value = '';
  }

  soltar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrando = false;
    if (!this.deshabilitado) this.agregar(Array.from(evento.dataTransfer?.files ?? []));
  }

  quitar(indice: number): void {
    const v = this.vistas[indice];
    if (v?.url) URL.revokeObjectURL(v.url);
    this.vistas = this.vistas.filter((_, i) => i !== indice);
    this.emitir();
  }

  peso(bytes: number): string {
    return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }

  ngOnDestroy(): void {
    this.liberar();
  }

  private agregar(nuevos: File[]): void {
    this.error = '';
    const rechazados: string[] = [];
    for (const archivo of nuevos) {
      if (!TIPOS.includes(archivo.type)) {
        rechazados.push(`«${archivo.name}» no es imagen, video ni PDF`);
      } else if (archivo.size > ADJUNTO_MAX_BYTES) {
        rechazados.push(`«${archivo.name}» pesa más de 25 MB`);
      } else if (this.vistas.length < 10) {
        this.vistas = [...this.vistas, this.crearVista(archivo)];
      } else {
        rechazados.push('Puedes adjuntar hasta 10 archivos a la vez');
        break;
      }
    }
    this.error = rechazados.join('. ');
    this.emitir();
  }

  private crearVista(archivo: File): Vista {
    const tipo = archivo.type.startsWith('image/') ? 'imagen' : archivo.type.startsWith('video/') ? 'video' : 'pdf';
    return { archivo, tipo, url: tipo === 'imagen' ? URL.createObjectURL(archivo) : null };
  }

  private emitir(): void {
    this.actuales = this.vistas.map((v) => v.archivo);
    this.archivosChange.emit(this.actuales);
  }

  private liberar(): void {
    this.vistas.forEach((v) => v.url && URL.revokeObjectURL(v.url));
  }
}
