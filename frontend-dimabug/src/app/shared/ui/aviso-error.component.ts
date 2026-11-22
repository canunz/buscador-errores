import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Componente reutilizable de aviso error.
 * Se usa dentro de otras pantallas; recibe datos por @Input y avisa cambios por @Output.
 */
@Component({
  selector: 'app-aviso-error',
  standalone: true,
  template: `
    <div class="aviso" role="alert">
      <span class="icono" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 8v5" />
          <path d="M12 16.5h.01" />
        </svg>
      </span>
      <div class="texto">
        <strong>{{ titulo }}</strong>
        <p>{{ mensaje }}</p>
      </div>
      @if (conReintento) {
        <button type="button" class="reintentar" (click)="reintentar.emit()" [disabled]="ocupado">
          {{ ocupado ? 'Reintentando…' : etiqueta }}
        </button>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .aviso {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 14px;
      margin: 0 0 16px;
      padding: 16px 18px;
      border: 1px solid #fecaca;
      border-radius: 16px;
      background: #fff7f7;
      box-shadow: 0 8px 20px rgba(185, 28, 28, 0.06);
    }
    .icono {
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      flex-shrink: 0;
      border-radius: 12px;
      background: #fee2e2;
      color: #b91c1c;
    }
    .icono svg { width: 22px; height: 22px; }
    .texto { flex: 1; min-width: 180px; }
    .texto strong {
      display: block;
      margin: 0 0 2px;
      color: #7f1d1d;
      font-size: 14px;
      font-weight: 800;
    }
    .texto p {
      margin: 0;
      color: #991b1b;
      font-size: 14px;
      line-height: 1.45;
    }
    .reintentar {
      flex-shrink: 0;
      height: 40px;
      padding: 0 16px;
      border: 0;
      border-radius: 12px;
      background: #1e4fd6;
      color: #fff;
      font: inherit;
      font-size: 14px;
      font-weight: 750;
      cursor: pointer;
    }
    .reintentar:hover:not(:disabled) { background: #1a43b8; }
    .reintentar:disabled { opacity: 0.65; cursor: default; }
  `],
})
export class AvisoErrorComponent {
  @Input() titulo = 'No se pudo completar la consulta';
  @Input({ required: true }) mensaje = '';
  @Input() etiqueta = 'Reintentar';
  @Input() conReintento = true;
  @Input() ocupado = false;
  @Output() readonly reintentar = new EventEmitter<void>();
}
