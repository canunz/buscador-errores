import { Component, Input, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

/**
 * Botones flotantes de ayuda en la esquina inferior derecha.
 *  - WhatsApp: abre un chat con Soporte usando el enlace oficial wa.me
 *    (la app en el celular o WhatsApp Web en el computador).
 *  - Asistente (opcional): lleva al chatbot de DimaBug. No se muestra
 *    cuando el usuario ya está en la página del asistente.
 */
@Component({
  selector: 'app-boton-whatsapp',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (conAsistente && !enAsistente()) {
      <a
        class="flotante asistente"
        routerLink="/chatbot"
        aria-label="Consultar al asistente DimaBug"
        title="Consulta al asistente DimaBug"
      >
        <img src="assets/img/asistente_dimabug.png" alt="" width="34" height="34" />
        <span class="texto">Asistente</span>
      </a>
    }

    <a
      class="flotante whatsapp"
      [href]="enlace"
      target="_blank"
      rel="noopener noreferrer"
      aria-label="Escribir a Soporte por WhatsApp"
      title="Escríbele a Soporte por WhatsApp"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path fill="currentColor" d="M12.04 2C6.58 2 2.15 6.4 2.15 11.83c0 1.74.46 3.44 1.34 4.94L2 22l5.38-1.4a10 10 0 0 0 4.66 1.18h.01c5.46 0 9.89-4.4 9.89-9.83C21.94 6.4 17.5 2 12.04 2zm5.72 13.89c-.24.68-1.4 1.25-1.94 1.33-.5.07-1.13.1-1.82-.11-.42-.13-.96-.31-1.65-.61-2.9-1.25-4.79-4.17-4.94-4.37-.14-.2-1.18-1.57-1.18-3 0-1.42.75-2.12 1.01-2.41.27-.29.58-.36.78-.36h.56c.18 0 .42-.07.66.5.24.58.82 2 .89 2.15.07.15.12.32.02.52-.1.2-.14.32-.28.5-.14.17-.3.39-.42.52-.14.15-.28.3-.12.59.16.29.7 1.16 1.5 1.88 1.04.93 1.9 1.22 2.2 1.36.29.14.46.12.63-.07.17-.2.73-.85.93-1.14.2-.29.39-.24.66-.14.27.1 1.71.8 2 .95.29.15.49.22.56.35.07.12.07.71-.17 1.39z"/>
      </svg>
      <span class="texto">WhatsApp Soporte</span>
    </a>
  `,
  styles: [`
    :host {
      position: fixed;
      right: 28px;
      bottom: 28px;
      z-index: 50;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      justify-content: flex-end;
      flex: 0 0 auto;
      width: auto;
      height: auto;
      max-width: calc(100vw - 32px);
      gap: 10px;
      pointer-events: none;
    }

    .flotante {
      pointer-events: auto;
      display: inline-flex;
      align-items: center;
      justify-content: flex-start;
      flex: 0 0 auto;
      align-self: flex-end;
      width: auto;
      max-width: 100%;
      gap: 10px;
      height: 52px;
      padding: 0 16px 0 12px;
      border-radius: 999px;
      text-decoration: none;
      font-weight: 750;
      font-size: 0.9rem;
      white-space: nowrap;
      box-sizing: border-box;
      transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
    }

    .flotante:hover {
      transform: translateY(-2px);
    }

    .flotante:focus-visible {
      outline: 3px solid #6397ef;
      outline-offset: 3px;
    }

    /* Asistente: blanco con el ícono del sistema, para no confundirse con WhatsApp. */
    .asistente {
      background: #fff;
      color: #0b1b4d;
      border: 1px solid #dbe3f3;
      box-shadow: 0 12px 28px rgba(11, 27, 77, 0.16);
    }

    .asistente:hover {
      box-shadow: 0 16px 34px rgba(11, 27, 77, 0.24);
    }

    .asistente img {
      display: block;
      width: 34px;
      height: 34px;
      object-fit: contain;
    }

    .whatsapp {
      padding-left: 15px;
      background: #25d366;
      color: #fff;
      box-shadow: 0 12px 28px rgba(18, 140, 126, 0.35);
    }

    .whatsapp:hover {
      background: #1ebe5b;
      box-shadow: 0 16px 34px rgba(18, 140, 126, 0.45);
    }

    .whatsapp svg {
      flex-shrink: 0;
      width: 26px;
      height: 26px;
    }

    /* En pantallas chicas quedan solo los globos redondos. */
    @media (max-width: 600px) {
      :host {
        right: 22px;
        bottom: 28px;
        max-width: calc(100vw - 36px);
        gap: 10px;
      }

      .flotante {
        width: 54px;
        min-width: 54px;
        height: 54px;
        padding: 0;
        justify-content: center;
      }

      .texto {
        display: none;
      }
    }
  `],
})
export class BotonWhatsappComponent {
  private readonly router = inject(Router);

  /** Si es true, junto a WhatsApp aparece el acceso al asistente. */
  @Input() conAsistente = false;

  /** Número de Soporte: +56 9 7151 9533, escrito sin espacios ni signo + como pide WhatsApp. */
  readonly enlace = 'https://wa.me/56971519533';

  /** Indica si la página actual es la del asistente, para no repetir el botón. */
  readonly enAsistente = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.router.url.startsWith('/chatbot')),
      startWith(this.router.url.startsWith('/chatbot')),
    ),
    { initialValue: false },
  );
}
