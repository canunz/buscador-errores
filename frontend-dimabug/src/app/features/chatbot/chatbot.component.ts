import { Component, ElementRef, inject, ViewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ChatbotService } from '../../core/services/chatbot.service';
import { ChatbotSolucion } from '../../core/models/chatbot.model';

import { MaterialesPublicosComponent } from '../../shared/ui/materiales-publicos.component';
import { ChatbotImagenComponent } from './chatbot-imagen.component';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

interface Mensaje {
  autor: 'usuario' | 'asistente';
  texto: string;
  soluciones?: ChatbotSolucion[];
  reintentar?: string;
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [ReactiveFormsModule, MaterialesPublicosComponent, ChatbotImagenComponent, RouterLink],
  templateUrl: './chatbot.component.html',
  styleUrl: './chatbot.component.css',
})
/** Pantalla del asistente DimaBug: el usuario escribe su problema y se muestran soluciones. */
export class ChatbotComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ChatbotService);
  readonly auth = inject(AuthService);
  @ViewChild(ChatbotImagenComponent) imagen?: ChatbotImagenComponent;
  @ViewChild('conversacion') conversacion?: ElementRef<HTMLElement>;
  enviando = false;
  mensajes: Mensaje[] = [{
    autor: 'asistente',
    texto: 'Hola, soy el asistente de DimaBug. Cuéntame qué te pasa y te ayudo con una solución de autoservicio.',
  }];
  readonly sugerencias = ['La impresora no imprime', 'No tengo internet', 'El computador no enciende'];
  readonly form = this.fb.nonNullable.group({ consulta: ['', [Validators.required, Validators.maxLength(300)]] });

  usarSugerencia(texto: string): void {
    if (this.enviando) {
      return;
    }
    this.form.controls.consulta.setValue(texto);
    this.enviar();
  }

  buscarDesdeImagen(texto: string): void {
    if (this.enviando || !texto.trim() || texto.trim().length > 300) return;
    this.imagen?.cerrar();
    this.form.controls.consulta.setValue(texto.trim());
    this.enviar();
  }

  /** Envía la consulta al asistente y agrega la respuesta a la conversación. */
  enviar(): void {
    const consulta = this.form.controls.consulta.value.trim();
    if (!consulta || consulta.length > 300 || this.enviando) {
      this.form.controls.consulta.markAsTouched();
      return;
    }
    this.mensajes.push({ autor: 'usuario', texto: consulta });
    this.form.reset();
    this.enviando = true;
    this.moverAlFinal();
    this.api.consultar(consulta).pipe(finalize(() => {
      this.enviando = false;
      this.moverAlFinal();
    })).subscribe({
      next: respuesta => this.mensajes.push({ autor: 'asistente', texto: respuesta.mensaje, soluciones: respuesta.soluciones }),
      error: (error: Error) => this.mensajes.push({ autor: 'asistente', texto: error.message, reintentar: consulta }),
    });
  }

  /** Vuelve a enviar una consulta que falló. */
  reintentar(mensaje: Mensaje): void {
    if (this.enviando || !mensaje.reintentar) return;
    this.form.controls.consulta.setValue(mensaje.reintentar);
    mensaje.reintentar = undefined;
    this.enviar();
  }

  private moverAlFinal(): void {
    setTimeout(() => this.conversacion?.nativeElement.scrollTo({ top: this.conversacion.nativeElement.scrollHeight, behavior: 'smooth' }));
  }
}
