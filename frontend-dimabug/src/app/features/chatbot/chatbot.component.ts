import { Component, ElementRef, inject, ViewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ChatbotService } from '../../core/services/chatbot.service';
import { ChatbotSolucion } from '../../core/models/chatbot.model';

interface Mensaje {
  autor: 'usuario' | 'asistente';
  texto: string;
  soluciones?: ChatbotSolucion[];
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './chatbot.component.html',
  styleUrl: './chatbot.component.css',
})
export class ChatbotComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ChatbotService);
  @ViewChild('conversacion') conversacion?: ElementRef<HTMLElement>;
  enviando = false;
  mensajes: Mensaje[] = [{ autor: 'asistente', texto: 'Hola. Describe brevemente el problema y buscaré una solución de autoservicio autorizada.' }];
  readonly form = this.fb.nonNullable.group({ consulta: ['', [Validators.required, Validators.maxLength(300)]] });

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
      error: (error: Error) => this.mensajes.push({ autor: 'asistente', texto: error.message }),
    });
  }

  private moverAlFinal(): void {
    setTimeout(() => this.conversacion?.nativeElement.scrollTo({ top: this.conversacion.nativeElement.scrollHeight, behavior: 'smooth' }));
  }
}
