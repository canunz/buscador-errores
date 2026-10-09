import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-loading-modal',
  standalone: true,
  template: `
    @if (bloquea) {
      <div class="loading-modal" role="alertdialog" aria-live="assertive" aria-busy="true" [attr.aria-label]="mensaje">
        <div class="loading-modal__backdrop" aria-hidden="true"></div>
        <div class="loading-modal__card">
          <span class="loading-modal__ring" aria-hidden="true">
            <span class="loading-modal__spinner"></span>
          </span>
          <p>{{ mensaje }}</p>
          <span class="loading-modal__espera">Espere un momento</span>
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

      :host-context(.tema-oscuro) .loading-modal__card {
        background: #152238;
        box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
      }

      :host-context(.tema-oscuro) .loading-modal__card p {
        color: #f3f5fb;
      }

      :host-context(.tema-oscuro) .loading-modal__espera {
        color: #94a3b8;
      }

      :host-context(.tema-oscuro) .loading-modal__spinner {
        border-color: rgba(234, 240, 254, 0.16);
        border-top-color: #6b93ff;
        border-right-color: #8babff;
      }
    `,
  ],
})
export class LoadingModalComponent {
  @Input() visible = false;
  @Input() mensaje = 'Cargando';

  /** La carga inicial no tapa la pantalla. El modal queda para guardar o eliminar. */
  get bloquea(): boolean {
    return this.visible && this.mensaje !== 'Cargando';
  }
}
