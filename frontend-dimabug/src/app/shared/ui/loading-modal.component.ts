import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-loading-modal',
  standalone: true,
  template: `
    @if (visible) {
      <div class="loading-modal" role="alertdialog" aria-modal="true" aria-live="assertive" aria-busy="true" [attr.aria-labelledby]="tituloId" [attr.aria-describedby]="detalleId">
        <div class="loading-modal__backdrop" aria-hidden="true"></div>
        <div class="loading-modal__card">
          <span class="loading-modal__ring" aria-hidden="true">
            <span class="loading-modal__spinner"></span>
          </span>
          <p [id]="tituloId">{{ mensaje }}</p>
          <span class="loading-modal__espera" [id]="detalleId">{{ detalle }}</span>
        </div>
      </div>
    }
  `,
  styles: [
    `

      .loading-modal {
        position: fixed;
        inset: 0;
        z-index: 9999;
        display: grid;
        place-items: center;
        padding: 1rem;
      }

      .loading-modal__backdrop {
        position: absolute;
        inset: 0;
        background: rgba(22, 48, 112, 0.42);
        backdrop-filter: blur(3px);
      }

      .loading-modal__card {
        position: relative;
        z-index: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 14px;
        min-width: min(320px, 100%);
        padding: 32px 28px 26px;
        border-radius: 20px;
        background: #fff;
        box-shadow: 0 24px 60px rgba(22, 48, 112, 0.28);
        text-align: center;
      }

      .loading-modal__card p {
        margin: 0;
        color: #163070;
        font-size: 17px;
        font-weight: 800;
      }

      .loading-modal__espera {
        color: #64748b;
        font-size: 13.5px;
        font-weight: 600;
      }

      .loading-modal__ring {
        display: grid;
        place-items: center;
        width: 64px;
        height: 64px;
      }

      .loading-modal__spinner {
        width: 44px;
        height: 44px;
        border: 3px solid #e8eefe;
        border-top-color: #1e4fd6;
        border-right-color: #3b7cf0;
        border-radius: 50%;
        animation: loading-spin 0.75s linear infinite;
      }

      @keyframes loading-spin {
        to {
          transform: rotate(360deg);
        }
      }

    `,
  ],
})
/** Modal de espera: aparece mientras carga y se quita solo al terminar. */
export class LoadingModalComponent {
  private static secuencia = 0;
  @Input() visible = false;
  @Input() mensaje = 'Preparando la información';
  @Input() detalle = 'Esto tomará un instante.';
  readonly tituloId = `loading-modal-titulo-${++LoadingModalComponent.secuencia}`;
  readonly detalleId = `loading-modal-detalle-${LoadingModalComponent.secuencia}`;
}
