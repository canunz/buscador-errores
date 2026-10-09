import { Component, ElementRef, HostListener, Input, ViewChild } from '@angular/core';

@Component({
  selector: 'app-ayuda-franja',
  standalone: true,
  host: {
    '[class.claro]': 'tono === "claro"',
  },
  template: `
    <button
      type="button"
      class="que"
      [attr.aria-expanded]="abierta"
      [attr.aria-label]="'Qué es ' + titulo"
      (click)="alternar($event)"
    >
      ?
    </button>
    <div #panel class="panel" popover="auto" role="note" (click)="$event.stopPropagation()" (toggle)="alCambiar($event)">
      <strong>{{ titulo }}</strong>
      <p>{{ detalle }}</p>
    </div>
  `,
  styles: `
    :host {
      position: absolute;
      top: 16px;
      right: 16px;
      z-index: 6;
      display: block;
      width: 30px;
      height: 30px;
      margin: 0;
      line-height: 0;
    }

    .que {
      width: 30px;
      height: 30px;
      padding: 0;
      border: 1px solid rgba(255, 255, 255, 0.7);
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.18);
      color: #fff;
      font-family: inherit;
      font-size: 15px;
      font-weight: 700;
      letter-spacing: 0;
      text-transform: none;
      line-height: 30px;
      text-align: center;
      cursor: pointer;
      box-shadow: 0 6px 16px rgba(7, 19, 54, 0.18);
    }

    .que:hover,
    .que[aria-expanded='true'] {
      background: #fff;
      color: #163070;
    }

    .panel {
      position: fixed;
      inset: auto;
      z-index: 10000;
      width: min(320px, calc(100vw - 24px));
      margin: 0;
      padding: 14px 16px;
      border: 0;
      border-radius: 14px;
      background: #fff;
      color: #334155;
      box-shadow: 0 18px 40px rgba(11, 27, 77, 0.28);
    }

    .panel strong {
      display: block;
      margin-bottom: 4px;
      color: #0b1b4d;
      font-size: 13.5px;
      font-weight: 700;
    }

    .panel p {
      margin: 0;
      font-size: 13.5px;
      font-weight: 500;
      line-height: 1.5;
      letter-spacing: normal;
      text-transform: none;
    }

    :host.claro .que {
      border-color: #c5d0e3;
      background: #eef3ff;
      color: #1e4fd6;
    }

    :host.claro .que:hover,
    :host.claro .que[aria-expanded='true'] {
      background: #1e4fd6;
      color: #fff;
    }

    :host-context(.tema-oscuro) .panel {
      background: #152238;
      color: #e5e7eb;
    }

    :host-context(.tema-oscuro) .panel strong {
      color: #f8fafc;
    }
  `,
})
export class AyudaFranjaComponent {
  @Input({ required: true }) titulo = '';
  @Input({ required: true }) detalle = '';
  @Input() tono: 'oscuro' | 'claro' = 'oscuro';
  @ViewChild('panel') panel?: ElementRef<HTMLElement>;

  abierta = false;

  alternar(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    const nodo = this.panel?.nativeElement;
    const boton = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    if (!nodo || !boton || typeof nodo.showPopover !== 'function') {
      return;
    }
    if (nodo.matches(':popover-open')) {
      nodo.hidePopover();
      return;
    }
    const rect = boton.getBoundingClientRect();
    nodo.style.right = `${Math.max(12, window.innerWidth - rect.right)}px`;
    nodo.style.left = 'auto';
    nodo.style.top = `${rect.bottom + 8}px`;
    nodo.showPopover();
    requestAnimationFrame(() => {
      const alto = nodo.offsetHeight;
      if (rect.bottom + 8 + alto > window.innerHeight - 12) {
        nodo.style.top = `${Math.max(12, rect.top - alto - 8)}px`;
      }
    });
  }

  alCambiar(event: Event): void {
    const estado = (event as ToggleEvent).newState;
    this.abierta = estado === 'open';
  }

  @HostListener('document:keydown.escape')
  cerrarTecla(): void {
    const nodo = this.panel?.nativeElement;
    if (nodo?.matches(':popover-open')) {
      nodo.hidePopover();
    }
  }
}
